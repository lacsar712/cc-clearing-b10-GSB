package com.clearing.netting.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A queued netting task: one (settleDate, currency) netting run waiting for its turn.
 * Tasks are drained serially in seq order; a failed task does not clear the rest of the queue.
 */
public class NettingTask {
    private final String taskId;
    private final long seq;
    private final LocalDate settleDate;
    private final String currency;
    private NettingTaskStatus status;
    private String runId;
    private String failureReason;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    public NettingTask(
            String taskId,
            long seq,
            LocalDate settleDate,
            String currency,
            NettingTaskStatus status,
            String runId,
            String failureReason,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
        this.taskId = Objects.requireNonNull(taskId);
        this.seq = seq;
        this.settleDate = Objects.requireNonNull(settleDate);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.status = Objects.requireNonNull(status);
        this.runId = runId;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static NettingTask create(long seq, LocalDate settleDate, String currency) {
        return new NettingTask(
                UUID.randomUUID().toString(),
                seq,
                settleDate,
                currency,
                NettingTaskStatus.QUEUED,
                null,
                null,
                Instant.now(),
                null,
                null);
    }

    public void markRunning() {
        this.status = NettingTaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markCompleted(String runId) {
        this.status = NettingTaskStatus.COMPLETED;
        this.runId = runId;
        this.failureReason = null;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = NettingTaskStatus.FAILED;
        this.failureReason = reason;
        this.finishedAt = Instant.now();
    }

    /** Back to QUEUED after a restart interrupted a RUNNING task. */
    public void requeue() {
        this.status = NettingTaskStatus.QUEUED;
        this.startedAt = null;
    }

    public String getTaskId() {
        return taskId;
    }

    public long getSeq() {
        return seq;
    }

    public LocalDate getSettleDate() {
        return settleDate;
    }

    public String getCurrency() {
        return currency;
    }

    public NettingTaskStatus getStatus() {
        return status;
    }

    public String getRunId() {
        return runId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
