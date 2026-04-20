package io.github.balanced.controlplane.repository;

import io.github.balanced.controlplane.entity.ListenerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListenerRepository extends JpaRepository<ListenerEntity, Long> {
}