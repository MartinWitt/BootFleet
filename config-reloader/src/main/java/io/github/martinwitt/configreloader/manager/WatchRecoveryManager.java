package io.github.martinwitt.configreloader.manager;

import io.fabric8.kubernetes.client.WatcherException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages watch recovery and retry logic with exponential backoff.
 * Handles reconnection of failed watchers due to "too old resource version" errors.
 */
@Component
public class WatchRecoveryManager {
    private static final Logger logger = LoggerFactory.getLogger(WatchRecoveryManager.class);
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final ScheduledExecutorService executorService =
            new ScheduledThreadPoolExecutor(
                    5,
                    r -> {
                        Thread t = new Thread(r, "WatchRecovery-" + Math.random());
                        t.setDaemon(true);
                        return t;
                    });

    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<>();

    public void scheduleRecovery(String resourceKey, Runnable recoveryAction) {
        RetryState state = retryStates.computeIfAbsent(resourceKey, k -> new RetryState());

        if (state.retryCount.get() >= MAX_RETRIES) {
            logger.error(
                    "Max retries ({}) exceeded for resource {}. Giving up on recovery.",
                    MAX_RETRIES,
                    resourceKey);
            return;
        }

        long backoff = calculateBackoff(state.retryCount.get());
        state.retryCount.incrementAndGet();

        logger.info(
                "Scheduling watch recovery for {} after {} ms (attempt {}/{})",
                resourceKey,
                backoff,
                state.retryCount.get(),
                MAX_RETRIES);

        executorService.schedule(
                () -> {
                    try {
                        recoveryAction.run();
                        resetRetryState(resourceKey);
                        logger.info("Watch recovery successful for {}", resourceKey);
                    } catch (Exception e) {
                        logger.warn(
                                "Watch recovery failed for {}, will retry",
                                resourceKey,
                                e);
                        scheduleRecovery(resourceKey, recoveryAction);
                    }
                },
                backoff,
                TimeUnit.MILLISECONDS);
    }

    public void handleWatcherException(
            String resourceKey, WatcherException exception, Runnable recoveryAction) {
        if (isTooOldResourceVersionError(exception)) {
            logger.warn(
                    "Watch error for {}: too old resource version, initiating recovery",
                    resourceKey);
            scheduleRecovery(resourceKey, recoveryAction);
        } else {
            logger.error(
                    "Unexpected watcher exception for {}: {}",
                    resourceKey,
                    exception.getMessage());
            scheduleRecovery(resourceKey, recoveryAction);
        }
    }

    public void resetRetryState(String resourceKey) {
        retryStates.remove(resourceKey);
    }

    private long calculateBackoff(int retryCount) {
        long backoff = INITIAL_BACKOFF_MS * (1L << retryCount); // Exponential: 2^n
        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    private boolean isTooOldResourceVersionError(WatcherException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("too old resource version");
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    private static class RetryState {
        AtomicInteger retryCount = new AtomicInteger(0);
    }
}
