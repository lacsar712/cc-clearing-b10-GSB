package com.clearing.netting.application;

import com.clearing.netting.domain.model.NettingTask;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serial queue drain: tasks execute one at a time, in seq order, on a single
 * worker thread. Policy CONTINUE_ON_FAILURE: a failed task never clears or
 * blocks the rest of the queue.
 */
@Component
public class NettingTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(NettingTaskWorker.class);

    private final NettingTaskQueueService queueService;
    private final NettingApplicationService nettingService;
    private final long taskDelayMs;
    private final ExecutorService executor;
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public NettingTaskWorker(
            NettingTaskQueueService queueService,
            NettingApplicationService nettingService,
            @Value("${app.netting.queue.task-delay-ms:1500}") long taskDelayMs) {
        this.queueService = queueService;
        this.nettingService = nettingService;
        this.taskDelayMs = taskDelayMs;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "netting-task-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** Nudge the worker; no-op if a drain loop is already running. */
    public void kick() {
        if (draining.compareAndSet(false, true)) {
            executor.execute(this::drainLoop);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int requeued = queueService.requeueRunningTasks();
        if (requeued > 0) {
            log.info("requeued {} netting task(s) left RUNNING by previous shutdown", requeued);
        }
        kick();
    }

    /** Backstop so queued tasks are picked up even if a kick was missed. */
    @Scheduled(fixedDelayString = "${app.netting.queue.poll-ms:5000}")
    public void scheduledKick() {
        kick();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void drainLoop() {
        try {
            while (true) {
                Optional<NettingTask> next = queueService.findNextQueued();
                if (next.isEmpty()) {
                    return;
                }
                if (!process(next.get())) {
                    return;
                }
            }
        } finally {
            draining.set(false);
            // a submit may have raced the last findNextQueued: re-check before going idle
            if (queueService.findNextQueued().isPresent()) {
                kick();
            }
        }
    }

    /** @return true to keep draining, false to stop the loop */
    private boolean process(NettingTask task) {
        queueService.markRunning(task.getTaskId());
        log.info("netting task #{} {} started ({}/{})",
                task.getSeq(), task.getTaskId(), task.getSettleDate(), task.getCurrency());
        if (!sleepBeforeExecute()) {
            return false;
        }
        try {
            NettingApplicationService.NettingRunResult result =
                    nettingService.execute(task.getSettleDate(), task.getCurrency());
            queueService.markCompleted(task.getTaskId(), result.run().getRunId());
            log.info("netting task #{} completed, run {}", task.getSeq(), result.run().getRunId());
            return true;
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? "unexpected error" : ex.getMessage();
            log.warn("netting task #{} failed: {}", task.getSeq(), reason);
            queueService.markFailed(task.getTaskId(), reason);
            // CONTINUE_ON_FAILURE: a failed task never clears or blocks the rest of the queue
            return true;
        }
    }

    /** Small visible pause while RUNNING so the queue page can show sequential progress. */
    private boolean sleepBeforeExecute() {
        if (taskDelayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(taskDelayMs);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
