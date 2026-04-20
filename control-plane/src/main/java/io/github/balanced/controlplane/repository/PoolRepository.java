package io.github.balanced.controlplane.repository;

import io.github.balanced.controlplane.entity.PoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PoolRepository extends JpaRepository<PoolEntity, Long> {

    Optional<PoolEntity> findByName(String name);
}