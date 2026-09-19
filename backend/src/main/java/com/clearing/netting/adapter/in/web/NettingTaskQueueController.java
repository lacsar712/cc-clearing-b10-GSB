package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.NettingTaskQueueService;
import com.clearing.netting.application.NettingTaskWorker;
import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/netting-tasks")
public class NettingTaskQueueController {

    private final NettingTaskQueueService queueService;
    private final NettingTaskWorker worker;

    public NettingTaskQueueController(NettingTaskQueueService queueService, NettingTaskWorker worker) {
        this.queueService = queueService;
        this.worker = worker;
    }

    @GetMapping
    public QueueResponse list() {
        AuthContext.require();
        return new QueueResponse(
                queueService.getFailurePolicy(),
                queueService.getFailurePolicyNote(),
                queueService.list().stream().map(TaskResponse::from).collect(Collectors.toList()));
    }

    @PostMapping
    public List<TaskResponse> submit(@Valid @RequestBody SubmitRequest request) {
        AuthContext.requireOperator();
        List<NettingTask> created = queueService.submitBatch(
                request.items().stream()
                        .map(i -> new NettingTaskQueueService.TaskSpec(i.settleDate(), i.currency()))
                        .collect(Collectors.toList()));
        worker.kick();
        return created.stream().map(TaskResponse::from).collect(Collectors.toList());
    }

    public record SubmitRequest(@NotEmpty @Size(max = 50) List<@Valid Item> items) {
    }

    public record Item(@NotNull LocalDate settleDate, @NotBlank String currency) {
    }

    public record TaskResponse(
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
        static TaskResponse from(NettingTask t) {
            return new TaskResponse(
                    t.getTaskId(),
                    t.getSeq(),
                    t.getSettleDate(),
                    t.getCurrency(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getStartedAt(),
                    t.getFinishedAt(),
                    t.getRunId(),
                    t.getFailureReason());
        }
    }

    public record QueueResponse(String failurePolicy, String failurePolicyNote, List<TaskResponse> tasks) {
    }
}
