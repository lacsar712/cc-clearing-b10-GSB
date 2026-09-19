package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;
import com.clearing.netting.domain.port.out.NettingTaskRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NettingTaskQueueService {

    /** Fixed queue policy: a failed task never clears or blocks the rest of the queue. */
    public static final String FAILURE_POLICY = "CONTINUE_ON_FAILURE";
    public static final String FAILURE_POLICY_NOTE = "单项失败不清空后续任务，队列按序继续推进（CONTINUE_ON_FAILURE）";

    private final NettingTaskRepositoryPort taskRepository;

    public NettingTaskQueueService(NettingTaskRepositoryPort taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public List<NettingTask> submitBatch(List<TaskSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            throw new DomainException("EMPTY_BATCH", "at least one task is required");
        }
        long seq = taskRepository.maxSeq();
        List<NettingTask> created = new ArrayList<>();
        for (TaskSpec spec : specs) {
            if (spec.settleDate() == null) {
                throw new DomainException("INVALID_DATE", "settleDate is required");
            }
            if (spec.currency() == null || spec.currency().isBlank()) {
                throw new DomainException("INVALID_CURRENCY", "currency is required");
            }
            NettingTask task = NettingTask.create(++seq, spec.settleDate(), spec.currency().trim());
            created.add(taskRepository.save(task));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NettingTask> list() {
        return taskRepository.findAllOrderBySeqAsc();
    }

    @Transactional(readOnly = true)
    public Optional<NettingTask> findNextQueued() {
        return taskRepository.findNextQueued();
    }

    @Transactional
    public void markRunning(String taskId) {
        NettingTask task = requireTask(taskId);
        task.markRunning();
        taskRepository.save(task);
    }

    @Transactional
    public void markCompleted(String taskId, String runId) {
        NettingTask task = requireTask(taskId);
        task.markCompleted(runId);
        taskRepository.save(task);
    }

    @Transactional
    public void markFailed(String taskId, String reason) {
        NettingTask task = requireTask(taskId);
        task.markFailed(reason);
        taskRepository.save(task);
    }

    /** Tasks left RUNNING by a shutdown go back to QUEUED so the serial queue can resume. */
    @Transactional
    public int requeueRunningTasks() {
        List<NettingTask> running = taskRepository.findByStatus(NettingTaskStatus.RUNNING);
        for (NettingTask task : running) {
            task.requeue();
            taskRepository.save(task);
        }
        return running.size();
    }

    public String getFailurePolicy() {
        return FAILURE_POLICY;
    }

    public String getFailurePolicyNote() {
        return FAILURE_POLICY_NOTE;
    }

    private NettingTask requireTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new DomainException("TASK_NOT_FOUND", "netting task not found: " + taskId));
    }

    public record TaskSpec(LocalDate settleDate, String currency) {
    }
}
