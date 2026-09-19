package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.NettingTaskJpaRepository;
import com.clearing.netting.domain.model.NettingTask;
import com.clearing.netting.domain.model.NettingTaskStatus;
import com.clearing.netting.domain.port.out.NettingTaskRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class NettingTaskRepositoryAdapter implements NettingTaskRepositoryPort {

    private final NettingTaskJpaRepository repository;

    public NettingTaskRepositoryAdapter(NettingTaskJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public NettingTask save(NettingTask task) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(task)));
    }

    @Override
    public Optional<NettingTask> findById(String taskId) {
        return repository.findById(taskId).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<NettingTask> findAllOrderBySeqAsc() {
        return repository.findAllByOrderBySeqAsc().stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NettingTask> findByStatus(NettingTaskStatus status) {
        return repository.findByStatus(status).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<NettingTask> findNextQueued() {
        return repository.findFirstByStatusOrderBySeqAsc(NettingTaskStatus.QUEUED)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public long maxSeq() {
        return repository.maxSeq();
    }
}
