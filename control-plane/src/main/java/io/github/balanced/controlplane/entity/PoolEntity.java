package io.github.balanced.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import io.github.balanced.common.BalancingAlgorithm;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pool")
public class PoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalancingAlgorithm algorithm = BalancingAlgorithm.ROUND_ROBIN;

    @Column(name = "sticky_enabled", nullable = false)
    private boolean stickyEnabled = false;

    @Column(name = "sticky_ttl_seconds", nullable = false)
    private int stickyTtlSeconds = 300;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pool_upstream",
            joinColumns = @JoinColumn(name = "pool_id"),
            inverseJoinColumns = @JoinColumn(name = "upstream_id")
    )
    private List<UpstreamEntity> upstreams = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BalancingAlgorithm getAlgorithm() { return algorithm; }
    public void setAlgorithm(BalancingAlgorithm algorithm) { this.algorithm = algorithm; }

    public boolean isStickyEnabled() { return stickyEnabled; }
    public void setStickyEnabled(boolean stickyEnabled) { this.stickyEnabled = stickyEnabled; }

    public int getStickyTtlSeconds() { return stickyTtlSeconds; }
    public void setStickyTtlSeconds(int stickyTtlSeconds) { this.stickyTtlSeconds = stickyTtlSeconds; }

    public List<UpstreamEntity> getUpstreams() { return upstreams; }
    public void setUpstreams(List<UpstreamEntity> upstreams) { this.upstreams = upstreams; }
}