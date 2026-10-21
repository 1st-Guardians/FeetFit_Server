package com.feetfit.server.service.ImageService;

import com.feetfit.server.domain.AccountCleanupTask;
import com.feetfit.server.service.UserService.AccountCleanupStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class ReportUploadCleanupFilter extends OncePerRequestFilter {
    private final ObjectProvider<AccountCleanupStore> cleanup;
    private final ObjectProvider<S3ObjectDeletionClient> images;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/reports/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = response.getStatus() >= 400;
        } finally {
            if (failed) {
                boolean queued = false;
                for (var upload : UploadedImageTracker.uploads(request)) {
                    try {
                        var key = images.getObject().resolveOwnedImage(upload.getImageUrl());
                        if (key != null) {
                            cleanup.getObject().enqueue(AccountCleanupTask.Kind.S3_IMAGE,
                                    new AccountCleanupStore.Payload(key.bucket(), key.key(), null, null, false), null);
                            queued = true;
                        }
                    } catch (RuntimeException failure) {
                        log.warn("Could not queue abandoned report image cleanup. failureType={}", failure.getClass().getSimpleName());
                    }
                }
                if (queued) {
                    try {
                        cleanup.getObject().requestProcessing();
                    } catch (RuntimeException failure) {
                        log.warn("Image cleanup notification failed; scheduled processing will retry.");
                    }
                }
            }
        }
    }
}
