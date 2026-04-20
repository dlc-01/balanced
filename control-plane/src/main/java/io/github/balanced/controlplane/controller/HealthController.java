package io.github.balanced.controlplane.controller;

import io.github.balanced.common.ConfigProvider;
import io.github.balanced.common.ConfigSnapshot;
import io.github.balanced.controlplane.service.HealthChecker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ConfigProvider configProvider;
    private final HealthChecker healthChecker;

    public HealthController(ConfigProvider configProvider, HealthChecker healthChecker) {
        this.configProvider = configProvider;
        this.healthChecker = healthChecker;
    }

    @GetMapping
    public Map<String, Object> health() {
        ConfigSnapshot snapshot = configProvider.current();
        return Map.of(
                "configVersion", snapshot.version(),
                "pools", snapshot.pools().size(),
                "listeners", snapshot.listeners().size()
        );
    }
}