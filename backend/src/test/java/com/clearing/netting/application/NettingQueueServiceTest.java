package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;
import com.clearing.netting.domain.port.out.NettingTaskRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NettingQueueServiceTest {

    private InMemoryTaskRepo taskRepo;
    private NettingApplicationService nettingService;
    private NettingQueueService queueService;

    private final LocalDate day1 = LocalDate.of(2026, 9, 18);
    private final LocalDate day2 = LocalDate.of(2026, 9, 19);

    @BeforeEach
    void setUp() {
        taskRepo = new InMemoryTaskRepo();
        nettingService = mock(NettingApplicationService.class);
        queueService = new NettingQueueService(taskRepo, nettingService, 0);
    }

    @Test
    void drainsSeriallyInSeqOrder() {
        List<String> calls = new ArrayList<>();
        when(nettingService.execute(any(LocalDate.class), anyString())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            String ccy = inv.getArgument(1);
            calls.add(date + "/" + ccy);
            return completedRun(date, ccy);
        });

        queueService.submit(List.of(
                new NettingQueueService.TaskSpec(day1, "USD"),
                new NettingQueueService.TaskSpec(day2, "CNY"),
                new NettingQueueService.TaskSpec(day2, "EUR")));
        queueService.drainQueue();

        assertEquals(List.of(day1 + "/USD", day2 + "/CNY", day2 + "/EUR"), calls);

        List<NettingTask> tasks = taskRepo.findAllOrderBySeqAsc();
        assertEquals(3, tasks.size());
        for (NettingTask t : tasks) {
            assertEquals(NettingTaskStatus.COMPLETED, t.getStatus());
            assertNotNull(t.getRunId());
            assertNotNull(t.getStartedAt());
            assertNotNull(t.getFinishedAt());
        }
    }

    @Test
    void failureDoesNotClearSubsequentTasks() {
        when(nettingService.execute(eq(day1), eq("USD")))
                .thenThrow(new DomainException("NO_OBLIGATIONS", "no OPEN obligations for settleDate/currency"));
        when(nettingService.execute(eq(day2), eq("CNY")))
                .thenAnswer(inv -> completedRun(day2, "CNY"));

        queueService.submit(List.of(
                new NettingQueueService.TaskSpec(day1, "USD"),
                new NettingQueueService.TaskSpec(day2, "CNY")));
        queueService.drainQueue();

        List<NettingTask> tasks = taskRepo.findAllOrderBySeqAsc();
        assertEquals(NettingTaskStatus.FAILED, tasks.get(0).getStatus());
        assertEquals("no OPEN obligations for settleDate/currency", tasks.get(0).getFailureReason());
        assertNull(tasks.get(0).getRunId());
        // continue-on-failure: the second task still ran to completion
        assertEquals(NettingTaskStatus.COMPLETED, tasks.get(1).getStatus());
        assertNotNull(tasks.get(1).getRunId());
    }

    @Test
    void submitAssignsConsecutiveSeqAcrossBatches() {
        queueService.submit(List.of(new NettingQueueService.TaskSpec(day1, "USD")));
        queueService.submit(List.of(
                new NettingQueueService.TaskSpec(day2, "USD"),
                new NettingQueueService.TaskSpec(day2, "EUR")));

        List<Long> seqs = taskRepo.findAllOrderBySeqAsc().stream()
                .map(NettingTask::getSeq)
                .collect(Collectors.toList());
        assertEquals(List.of(1L, 2L, 3L), seqs);
    }

    private NettingApplicationService.NettingRunResult completedRun(LocalDate date, String ccy) {
        NettingRun run = NettingRun.create(date, ccy);
        run.markRunning();
        run.markCompleted();
        return new NettingApplicationService.NettingRunResult(run, List.of(), List.of());
    }

    private static final class InMemoryTaskRepo implements NettingTaskRepositoryPort {
        private final Map<String, NettingTask> store = new LinkedHashMap<>();

        @Override
        public NettingTask save(NettingTask task) {
            store.put(task.getTaskId(), task);
            return task;
        }

        @Override
        public List<NettingTask> saveAll(List<NettingTask> tasks) {
            tasks.forEach(this::save);
            return tasks;
        }

        @Override
        public Optional<NettingTask> findById(String taskId) {
            return Optional.ofNullable(store.get(taskId));
        }

        @Override
        public List<NettingTask> findAllOrderBySeqAsc() {
            return store.values().stream()
                    .sorted(Comparator.comparingLong(NettingTask::getSeq))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<NettingTask> findNextQueued() {
            return findAllOrderBySeqAsc().stream()
                    .filter(t -> t.getStatus() == NettingTaskStatus.QUEUED)
                    .findFirst();
        }

        @Override
        public List<NettingTask> findByStatus(NettingTaskStatus status) {
            return findAllOrderBySeqAsc().stream()
                    .filter(t -> t.getStatus() == status)
                    .collect(Collectors.toList());
        }

        @Override
        public long maxSeq() {
            return store.values().stream().mapToLong(NettingTask::getSeq).max().orElse(0);
        }
    }
}
