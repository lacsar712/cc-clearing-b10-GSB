package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;

import java.util.List;
import java.util.Optional;

public interface NettingTaskRepositoryPort {
    NettingTask save(NettingTask task);

    Optional<NettingTask> findById(String taskId);

    List<NettingTask> findAllOrderBySeqAsc();

    List<NettingTask> findByStatus(NettingTaskStatus status);

    Optional<NettingTask> findNextQueued();

    long maxSeq();
}
