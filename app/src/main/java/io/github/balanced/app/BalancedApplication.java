package io.github.balanced.app;

import io.github.balanced.controlplane.service.ConfigSnapshotBuilder;
import io.github.balanced.dataplane.DataPlane;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PreDestroy;

@SpringBootApplication(scanBasePackages = "io.github.balanced")
@EntityScan("io.github.balanced.controlplane.entity")
@EnableJpaRepositories("io.github.balanced.controlplane.repository")
@EnableScheduling
public class BalancedApplication {

    private static final Logger log = LoggerFactory.getLogger(BalancedApplication.class);

    private DataPlane dataPlane;

    public static void main(String[] args) {
        SpringApplication.run(BalancedApplication.class, args);
    }

    @Bean
    public DataPlane dataPlane(ConfigSnapshotBuilder configProvider, MeterRegistry registry) {
        this.dataPlane = new DataPlane(configProvider, registry);
        return this.dataPlane;
    }

    @Bean
    public CommandLineRunner startDataPlane(DataPlane dataPlane, ConfigSnapshotBuilder snapshotBuilder) {
        return args -> {
            snapshotBuilder.rebuild();

            Thread thread = new Thread(dataPlane, "data-plane");
            thread.setDaemon(true);
            thread.start();
            log.info("Data plane started on thread '{}'", thread.getName());
        };
    }

    @PreDestroy
    public void onShutdown() {
        if (dataPlane != null) {
            log.info("Initiating graceful shutdown of data plane");
            dataPlane.shutdown();
        }
    }
}