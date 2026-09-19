package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.NettingQueueService;
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
@RequestMapping("/api/netting-queue")
public class NettingQueueController {

    private final NettingQueueService queueService;

    public NettingQueueController(NettingQueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public List<TaskResponse> list() {
        AuthContext.require();
        return queueService.list().stream().map(TaskResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public List<TaskResponse> submit(@Valid @RequestBody SubmitRequest request) {
        AuthContext.requireOperator();
        List<NettingTask> tasks = queueService.submit(request.items().stream()
                .map(i -> new NettingQueueService.TaskSpec(i.settleDate(), i.currency()))
                .collect(Collectors.toList()));
        // submit() committed above; now the drain thread can see the new rows
        queueService.triggerDrain();
        return tasks.stream().map(TaskResponse::from).collect(Collectors.toList());
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
            String runId,
            String failureReason,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
        static TaskResponse from(NettingTask t) {
            return new TaskResponse(
                    t.getTaskId(),
                    t.getSeq(),
                    t.getSettleDate(),
                    t.getCurrency(),
                    t.getStatus(),
                    t.getRunId(),
                    t.getFailureReason(),
                    t.getCreatedAt(),
                    t.getStartedAt(),
                    t.getFinishedAt());
        }
    }
}
