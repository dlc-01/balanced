package io.github.balanced.controlplane.controller;

import io.github.balanced.controlplane.controller.dto.CreateUpstreamRequest;
import io.github.balanced.controlplane.controller.dto.UpstreamResponse;
import io.github.balanced.controlplane.entity.UpstreamEntity;
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
@RequestMapping("/api/upstreams")
public class UpstreamController {

    private static final Logger log = LoggerFactory.getLogger(UpstreamController.class);

    private final UpstreamRepository repository;
    private final ConfigSnapshotBuilder snapshotBuilder;

    public UpstreamController(UpstreamRepository repository, ConfigSnapshotBuilder snapshotBuilder) {
        this.repository = repository;
        this.snapshotBuilder = snapshotBuilder;
    }

    @GetMapping
    public List<UpstreamResponse> list() {
        return repository.findAll().stream().map(UpstreamResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UpstreamResponse create(@Valid @RequestBody CreateUpstreamRequest request) {
        var entity = new UpstreamEntity();
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setWeight(request.weight());
        UpstreamEntity saved = repository.save(entity);
        snapshotBuilder.rebuild();
        log.info("Created upstream {}:{} weight={}", saved.getHost(), saved.getPort(), saved.getWeight());
        return UpstreamResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
        snapshotBuilder.rebuild();
        log.info("Deleted upstream id={}", id);
    }
}