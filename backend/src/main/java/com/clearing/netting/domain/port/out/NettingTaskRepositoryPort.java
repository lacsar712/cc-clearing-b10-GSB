package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;

import java.util.List;
import java.util.Optional;

public interface NettingTaskRepositoryPort {
    NettingTask save(NettingTask task);

    List<NettingTask> saveAll(List<NettingTask> tasks);

    Optional<NettingTask> findById(String taskId);

    List<NettingTask> findAllOrderBySeqAsc();

    Optional<NettingTask> findNextQueued();

    List<NettingTask> findByStatus(NettingTaskStatus status);

    /** Highest seq currently stored, 0 when the queue is empty. */
    long maxSeq();
}
