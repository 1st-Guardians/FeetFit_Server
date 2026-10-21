package com.feetfit.server.service.UserService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.AccountCleanupTask;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.event.UserWithdrawnEvent;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.service.ImageService.S3ObjectDeletionClient;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {
    private record Identity(SocialType type, String subject) { }
    private final UserRepository users;
    private final MeasurementSessionRepository sessions;
    private final MeasurementCommandService measurements;
    private final MeasurementSocketService sockets;
    private final AccountCleanupStore cleanup;
    private final S3ObjectDeletionClient images;
    private final SocialUnlinkClient social;
    private final EntityManager em;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponseDTO.WithdrawUserResultDTO withdraw(Long userId, UserRequestDTO.WithdrawUserRequestDTO request) {
        if (userId == null) throw new GeneralException(ErrorStatus._UNAUTHORIZED);
        Identity identity = transaction().execute(ignored -> users.findById(userId)
                .map(user -> new Identity(user.getSocialType(), user.getSocialId()))
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND)));
        // Reauthentication is outside the transaction that locks measurements and deletes the account.
        var unlink = social.prepare(identity.type(), identity.subject(), request);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction().execute(ignored -> deleteAccount(userId, identity, unlink));
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == 2) throw new UserHandler(ErrorStatus.USER_WITHDRAWAL_CONFLICT);
            } catch (GeneralException exception) {
                if (exception.getCode() != ErrorStatus.USER_WITHDRAWAL_CONFLICT || attempt == 2) throw exception;
            }
        }
        throw new UserHandler(ErrorStatus.USER_WITHDRAWAL_CONFLICT);
    }

    private UserResponseDTO.WithdrawUserResultDTO deleteAccount(Long userId, Identity identity,
                                                                SocialUnlinkClient.Prepared unlink) {
        em.clear();
        List<Long> ids = sessions.findIdsByUserId(userId);
        List<MeasurementSession> lockedSessions = new ArrayList<>();
        // Report writes lock session before user; use the same order to avoid deadlocks.
        for (Long id : ids) sessions.findByIdForUpdate(id).ifPresent(lockedSessions::add);
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
        if (!ids.equals(sessions.findIdsByUserId(userId))) throw new UserHandler(ErrorStatus.USER_WITHDRAWAL_CONFLICT);
        if (user.getSocialType() != identity.type() || !user.getSocialId().equals(identity.subject())) {
            throw new GeneralException(ErrorStatus._FORBIDDEN);
        }

        Set<S3ObjectDeletionClient.ObjectKey> objects = collectImages(userId);
        for (var object : objects) cleanup.enqueue(AccountCleanupTask.Kind.S3_IMAGE,
                new AccountCleanupStore.Payload(object.bucket(), object.key(), null, null, false), null);
        if (!unlink.requiresManualUnlink()) cleanup.enqueue(unlink.kind(), unlink.payload(), identity.type());
        cleanup.requestProcessing();

        for (var session : lockedSessions) {
            if (session.getStatus() != MeasurementStatus.COMPLETED && session.getStatus() != MeasurementStatus.FAILED) {
                session.updateStatus(MeasurementStatus.FAILED, session.getMeasurementDurationSec());
                session.updateFailure(MeasurementFailureReason.USER_CANCELLED, "회원 탈퇴로 측정이 종료되었습니다.");
                sockets.sendMeasurementStatusChanged(session);
            }
        }
        var device = user.getDevice();
        if (device != null && users.countByDeviceId(device.getId()) <= 1 && device.getStatus() == DeviceStatus.REGISTERED) {
            device.markAvailable();
        }
        em.flush();
        for (Long id : ids) measurements.deleteMeasurementRecords(userId, id);
        for (String table : List.of("user_foot_care_todo_assignment", "user_health_article", "shoe_click_history",
                "shoe_search_history", "device_connection_log", "user_terms")) {
            em.createNativeQuery("DELETE FROM " + table + " WHERE user_id = :userId")
                    .setParameter("userId", userId).executeUpdate();
        }
        em.createNativeQuery("DELETE FROM users WHERE id = :userId").setParameter("userId", userId).executeUpdate();
        em.clear();
        events.publishEvent(new UserWithdrawnEvent(userId));
        return UserResponseDTO.WithdrawUserResultDTO.builder().userId(userId).deleted(true)
                .deletedAt(LocalDateTime.now()).cleanupPending(!objects.isEmpty() || !unlink.requiresManualUnlink())
                .socialUnlinkStatus(unlink.requiresManualUnlink() ? "MANUAL_UNLINK_REQUIRED" : "PENDING").build();
    }

    private Set<S3ObjectDeletionClient.ObjectKey> collectImages(Long userId) {
        Set<S3ObjectDeletionClient.ObjectKey> result = new LinkedHashSet<>();
        collect(result, """
                SELECT analysis.left_image_url, analysis.right_image_url FROM hallux_valgus_analysis analysis
                JOIN measurement_session session ON session.id = analysis.measurement_session_id WHERE session.user_id = :userId
                """, userId);
        collect(result, """
                SELECT analysis.suspicious_area_map_image_url, analysis.original_foot_image_url,
                       analysis.sole_suspicious_area_map_image_url, analysis.sole_original_foot_image_url
                FROM tina_pedis_analyses analysis JOIN measurement_session session ON session.id = analysis.measurement_id
                WHERE session.user_id = :userId
                """, userId);
        collect(result, """
                SELECT analysis.left_pressure_image_url, analysis.right_pressure_image_url,
                       analysis.left_pressure_heatmap_image_url, analysis.right_pressure_heatmap_image_url,
                       analysis.left_plantar_footprint_image_url, analysis.right_plantar_footprint_image_url
                FROM daily_foot_analysis analysis JOIN measurement_session session ON session.id = analysis.measurement_id
                WHERE session.user_id = :userId
                """, userId);
        collect(result, """
                SELECT analysis.plantar_footprint_image_image FROM plantar_footprint analysis
                JOIN measurement_session session ON session.id = analysis.measurement_id WHERE session.user_id = :userId
                """, userId);
        return result;
    }

    private void collect(Set<S3ObjectDeletionClient.ObjectKey> result, String sql, Long userId) {
        for (Object row : em.createNativeQuery(sql).setParameter("userId", userId).getResultList()) {
            for (Object value : row instanceof Object[] values ? values : new Object[]{row}) {
                if (value instanceof String url) {
                    var key = images.resolveOwnedImage(url);
                    if (key != null) result.add(key);
                }
            }
        }
    }

    private TransactionTemplate transaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }
}
