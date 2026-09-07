package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({MeasurementCommandServiceImpl.class, MeasurementSocketService.class})
class MeasurementRecaptureJpaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired MeasurementCommandService service;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean MeasurementCompletionService completion;
    @MockBean SimpMessagingTemplate broker;

    private Long userId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        User user = em.persist(User.builder().nickname("recapture-user")
                .socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build());
        Device device = em.persist(Device.builder().deviceName("recapture-device").build());
        MeasurementSession session = em.persistAndFlush(MeasurementSession.builder().user(user).device(device)
                .measuredAt(LocalDateTime.now()).status(MeasurementStatus.CAPTURING_PHOTO).build());
        userId = user.getId();
        sessionId = session.getId();
        em.clear();
    }

    @Test
    void retryCounterAndWaitingDetailSurviveReloadAndCommittedRetryDispatchesPhoto() {
        update(MeasurementStatus.WAITING_FOR_RECAPTURE, 0);
        em.flush();
        em.clear();
        assertThat(em.find(MeasurementSession.class, sessionId).getRecaptureDetail()).contains("ArUco");
        assertThat(em.find(MeasurementSession.class, sessionId).getPhotoRecaptureCount()).isZero();
        update(MeasurementStatus.READY_FOR_RECAPTURE, 0);
        em.flush();
        em.clear();
        assertThat(em.find(MeasurementSession.class, sessionId).getPhotoRecaptureCount()).isOne();
        assertThat(em.find(MeasurementSession.class, sessionId).getRecaptureDetail()).isNull();
        verifyNoInteractions(hardware, broker);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(hardware).requestPhotoCapture(sessionId, "Bearer test", 1);
        TestTransaction.start();
        assertThat(em.find(MeasurementSession.class, sessionId).getPhotoRecaptureCount()).isOne();
    }

    @Test
    void rollbackDoesNotSendRecaptureSocketOrHardwareCommand() {
        update(MeasurementStatus.WAITING_FOR_RECAPTURE, 0);
        update(MeasurementStatus.READY_FOR_RECAPTURE, 0);

        TestTransaction.flagForRollback();
        TestTransaction.end();

        verifyNoInteractions(hardware, broker);
    }

    private void update(MeasurementStatus status, int attempt) {
        service.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, null, null, null, attempt), "Bearer test");
    }
}
