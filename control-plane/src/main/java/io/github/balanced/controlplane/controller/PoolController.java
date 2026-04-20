package io.github.balanced.controlplane.controller;

import io.github.balanced.controlplane.controller.dto.CreatePoolRequest;
import io.github.balanced.controlplane.entity.PoolEntity;
import io.github.balanced.controlplane.entity.UpstreamEntity;
import io.github.balanced.controlplane.repository.PoolRepository;
import io.github.balanced.controlplane.repository.UpstreamRepository;
import io.github.balanced.controlplane.service.ConfigSnapshotBuilder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pools")
public class PoolController {

    private static final Logger log = LoggerFactory.getLogger(PoolController.class);

    private final PoolRepository poolRepository;
    private final UpstreamRepository upstreamRepository;
    private final ConfigSnapshotBuilder snapshotBuilder;

    public PoolController(PoolRepository poolRepository, UpstreamRepository upstreamRepository,
                          ConfigSnapshotBuilder snapshotBuilder) {
        this.poolRepository = poolRepository;
        this.upstreamRepository = upstreamRepository;
        this.snapshotBuilder = snapshotBuilder;
    }

    @GetMapping
    public List<PoolEntity> list() {
        return poolRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoolEntity create(@Valid @RequestBody CreatePoolRequest request) {
        var entity = new PoolEntity();
        entity.setName(request.name());
        entity.setAlgorithm(request.algorithm());
        entity.setStickyEnabled(request.stickyEnabled());
        entity.setStickyTtlSeconds(request.stickyTtlSeconds());

        if (request.upstreamIds() != null && !request.upstreamIds().isEmpty()) {
            List<UpstreamEntity> upstreams = upstreamRepository.findAllById(request.upstreamIds());
            entity.setUpstreams(upstreams);
        }

        PoolEntity saved = poolRepository.save(entity);
        snapshotBuilder.rebuild();
        log.info("Created pool '{}' algorithm={}", saved.getName(), saved.getAlgorithm());
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!poolRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        poolRepository.deleteById(id);
        snapshotBuilder.rebuild();
        log.info("Deleted pool id={}", id);
    }
}