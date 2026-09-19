package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;
import com.clearing.netting.domain.port.out.NettingTaskRepositoryPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serial netting task queue. Tasks are submitted in batches and drained one at a time
 * in seq order on a single background thread. Failure policy: continue-on-failure —
 * a failed task is marked FAILED and the remaining tasks keep going (never cleared).
 */
@Service
public class NettingQueueService {

    private static final Logger log = LoggerFactory.getLogger(NettingQueueService.class);

    private final NettingTaskRepositoryPort taskRepository;
    private final NettingApplicationService nettingService;
    private final long taskDelayMs;
    private final Object drainLock = new Object();
    private final ExecutorService executor;

    public NettingQueueService(
            NettingTaskRepositoryPort taskRepository,
            NettingApplicationService nettingService,
            @Value("${app.netting.queue.task-delay-ms:1000}") long taskDelayMs) {
        this.taskRepository = taskRepository;
        this.nettingService = nettingService;
        this.taskDelayMs = taskDelayMs;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "netting-queue-drain");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Persist a batch of tasks as QUEUED with consecutive seq numbers.
     * Does not start processing — callers must invoke {@link #triggerDrain()}
     * after this transaction commits so the drain thread sees the new rows.
     */
    @Transactional
    public List<NettingTask> submit(List<TaskSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            throw new DomainException("EMPTY_QUEUE_BATCH", "at least one task is required");
        }
        long seq = taskRepository.maxSeq();
        List<NettingTask> tasks = new ArrayList<>();
        for (TaskSpec spec : specs) {
            if (spec.settleDate() == null) {
                throw new DomainException("INVALID_DATE", "settleDate is required");
            }
            if (spec.currency() == null || spec.currency().isBlank()) {
                throw new DomainException("INVALID_CURRENCY", "currency is required");
            }
            tasks.add(NettingTask.create(++seq, spec.settleDate(), spec.currency().trim().toUpperCase()));
        }
        return taskRepository.saveAll(tasks);
    }

    @Transactional(readOnly = true)
    public List<NettingTask> list() {
        return taskRepository.findAllOrderBySeqAsc();
    }

    /** Kick the background drain. No-op if a drain is already running or queued. */
    public void triggerDrain() {
        executor.submit(this::drainSafely);
    }

    /**
     * Drain the queue serially. Runs on the single executor thread in production;
     * also called directly by tests. Each task commits its own status transitions
     * so clients polling {@link #list()} see the queue advance one task at a time.
     */
    public void drainQueue() {
        synchronized (drainLock) {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<NettingTask> next = taskRepository.findNextQueued();
                if (next.isEmpty()) {
                    return;
                }
                if (!pauseBetweenTasks()) {
                    return;
                }
                processOne(next.get().getTaskId());
            }
        }
    }

    private void processOne(String taskId) {
        NettingTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != NettingTaskStatus.QUEUED) {
            return;
        }
        task.markRunning();
        taskRepository.save(task);
        try {
            NettingApplicationService.NettingRunResult result =
                    nettingService.execute(task.getSettleDate(), task.getCurrency());
            NettingTask done = taskRepository.findById(taskId).orElseThrow();
            done.markCompleted(result.run().getRunId());
            taskRepository.save(done);
        } catch (RuntimeException ex) {
            // continue-on-failure: mark this task FAILED and keep draining the rest
            String reason = ex.getMessage() == null ? "unexpected error" : ex.getMessage();
            log.warn("netting task {} failed: {}", taskId, reason);
            NettingTask failed = taskRepository.findById(taskId).orElseThrow();
            failed.markFailed(reason);
            taskRepository.save(failed);
        }
    }

    private boolean pauseBetweenTasks() {
        if (taskDelayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(taskDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void drainSafely() {
        try {
            drainQueue();
        } catch (RuntimeException e) {
            log.error("netting queue drain terminated unexpectedly", e);
        }
    }

    /** After a restart, tasks left RUNNING were interrupted: requeue them and resume. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        for (NettingTask t : taskRepository.findByStatus(NettingTaskStatus.RUNNING)) {
            t.requeue();
            taskRepository.save(t);
        }
        triggerDrain();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record TaskSpec(LocalDate settleDate, String currency) {
    }
}
