package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.NettingTaskJpaEntity;
import com.clearing.netting.domain.model.NettingTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NettingTaskJpaRepository extends JpaRepository<NettingTaskJpaEntity, String> {
    List<NettingTaskJpaEntity> findAllByOrderBySeqAsc();

    List<NettingTaskJpaEntity> findByStatus(NettingTaskStatus status);

    Optional<NettingTaskJpaEntity> findFirstByStatusOrderBySeqAsc(NettingTaskStatus status);

    @Query("select coalesce(max(t.seq), 0) from NettingTaskJpaEntity t")
    long maxSeq();
}
