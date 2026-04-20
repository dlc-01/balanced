package io.github.balanced.controlplane.controller;

import io.github.balanced.controlplane.entity.ListenerEntity;
import io.github.balanced.controlplane.repository.ListenerRepository;
import io.github.balanced.controlplane.service.ConfigSnapshotBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listeners")
public class ListenerController {

    private final ListenerRepository repository;
    private final ConfigSnapshotBuilder snapshotBuilder;

    public ListenerController(ListenerRepository repository, ConfigSnapshotBuilder snapshotBuilder) {
        this.repository = repository;
        this.snapshotBuilder = snapshotBuilder;
    }

    @GetMapping
    public List<ListenerEntity> list() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListenerEntity create(@RequestBody ListenerEntity listener) {
        ListenerEntity saved = repository.save(listener);
        snapshotBuilder.rebuild();
        return saved;
    }
}