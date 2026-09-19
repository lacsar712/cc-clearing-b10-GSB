package com.clearing.netting.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A queued netting task: one (settleDate, currency) netting execution waiting
 * in the serial queue. Linked to the produced NettingRun once executed.
 */
public class NettingTask {
    private final String taskId;
    private final long seq;
    private final LocalDate settleDate;
    private final String currency;
    private NettingTaskStatus status;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String runId;
    private String failureReason;

    public NettingTask(
            String taskId,
            long seq,
            LocalDate settleDate,
            String currency,
            NettingTaskStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String runId,
            String failureReason) {
        this.taskId = Objects.requireNonNull(taskId);
        this.seq = seq;
        this.settleDate = Objects.requireNonNull(settleDate);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.runId = runId;
        this.failureReason = failureReason;
    }

    public static NettingTask create(long seq, LocalDate settleDate, String currency) {
        return new NettingTask(
                UUID.randomUUID().toString(),
                seq,
                settleDate,
                currency,
                NettingTaskStatus.QUEUED,
                Instant.now(),
                null,
                null,
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

    public void requeue() {
        this.status = NettingTaskStatus.QUEUED;
        this.startedAt = null;
        this.finishedAt = null;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getRunId() {
        return runId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
