package com.feetfit.server.service.UserService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.AccountCleanupTask;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.event.AccountCleanupRequestedEvent;
import com.feetfit.server.repository.AccountCleanupTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountCleanupStore {
    public record Payload(String bucket, String key, String subject, String token, boolean adminKey) { }
    public record Claimed(Long id, AccountCleanupTask.Kind kind, String encryptedPayload, String token) { }

    private final AccountCleanupTaskRepository tasks;
    private final AccountCleanupCipher cipher;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public void enqueue(AccountCleanupTask.Kind kind, Payload payload, SocialType socialType) {
        try {
            String hash = socialType == null ? null : cipher.identityHash(socialType.name(), payload.subject());
            tasks.save(new AccountCleanupTask(kind, cipher.encrypt(mapper.writeValueAsString(payload)), hash));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize cleanup task.");
        }
    }

    @Transactional
    public void requestProcessing() {
        events.publishEvent(new AccountCleanupRequestedEvent());
    }

    @Transactional(readOnly = true)
    public void requireNoPendingUnlink(SocialType type, String subject) {
        if (tasks.existsByIdentityHash(cipher.identityHash(type.name(), subject))) {
            throw new UserHandler(ErrorStatus.USER_WITHDRAWAL_CLEANUP_PENDING);
        }
    }

    @Transactional
    public Claimed claim(Long id) {
        var task = tasks.findByIdForUpdate(id).orElse(null);
        String token = UUID.randomUUID().toString();
        if (task == null || !task.claim(LocalDateTime.now(), token)) return null;
        return new Claimed(task.getId(), task.getKind(), task.getEncryptedPayload(), token);
    }

    public Payload readPayload(Claimed task) {
        try {
            return mapper.readValue(cipher.decrypt(task.encryptedPayload()), Payload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read cleanup task.");
        }
    }

    @Transactional
    public void complete(Claimed claim) {
        tasks.complete(claim.id(), claim.token());
    }

    @Transactional
    public void retry(Claimed claim) {
        tasks.findByIdForUpdate(claim.id()).ifPresent(task -> task.retry(claim.token()));
    }
}
