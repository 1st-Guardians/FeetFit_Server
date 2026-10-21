package com.feetfit.server.service.UserService;

import com.feetfit.server.event.AccountCleanupRequestedEvent;
import com.feetfit.server.repository.AccountCleanupTaskRepository;
import com.feetfit.server.service.ImageService.S3ObjectDeletionClient;
import com.feetfit.server.service.ImageService.ImageReferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "account.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AccountCleanupProcessor {
    private final AccountCleanupTaskRepository tasks;
    private final AccountCleanupStore store;
    private final S3ObjectDeletionClient images;
    private final ImageReferenceService references;
    private final SocialUnlinkClient social;
    private final AtomicBoolean running = new AtomicBoolean();

    @Async("accountCleanupTaskExecutor")
    @TransactionalEventListener
    public void onRequested(AccountCleanupRequestedEvent event) {
        processReadyTasks();
    }

    @Scheduled(fixedDelayString = "${account.cleanup.interval-ms:30000}")
    public void processReadyTasks() {
        if (!running.compareAndSet(false, true)) return;
        try {
            for (Long id : tasks.findReadyIds(LocalDateTime.now(), PageRequest.of(0, 20))) {
                var claim = store.claim(id);
                if (claim == null) continue;
                try {
                    var payload = store.readPayload(claim);
                    switch (claim.kind()) {
                        case S3_IMAGE -> {
                            if (!references.isReferenced(images.referenceUrls(payload.key()))) {
                                images.delete(payload.bucket(), payload.key());
                            }
                        }
                        case KAKAO_UNLINK -> social.unlinkKakao(payload);
                        case APPLE_REVOKE -> social.revokeApple(payload.token());
                    }
                    store.complete(claim);
                    log.info("Account cleanup completed. taskId={}, kind={}", id, claim.kind());
                } catch (RuntimeException failure) {
                    store.retry(claim);
                    // Provider responses and object keys can contain credentials or personal data.
                    log.warn("Account cleanup will retry. taskId={}, kind={}, failureType={}",
                            id, claim.kind(), failure.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Account cleanup scan failed. failureType={}", failure.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
