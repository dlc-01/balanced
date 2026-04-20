# Balanced — TCP Load Balancer

L4 TCP load balancer with separated control plane and data plane in a single JVM.

```
                    ┌────────────────────────────────────────────┐
                    │                  JVM                       │
                    │                                            │
  client ──TCP──▶   │  ┌───────────┐   AtomicRef   ┌───────────┐ │
                    │  │ Data Plane│◀─────────────▶│  Control  │ │
  client ──TCP──▶   │  │  (NIO)    │ ConfigSnapshot│   Plane   │ │
                    │  └─────┬─────┘               │ (Spring)  │ │
                    │        │                     └─────┬─────┘ │
                    │        ▼                           │       │
                    │   upstream pool                    ▼       │
                    │   ┌──┐ ┌──┐ ┌──┐               PostgreSQL  │
                    │   │u1│ │u2│ │u3│                           │
                    │   └──┘ └──┘ └──┘                           │
                    └────────────────────────────────────────────┘
```

## Architecture

| Layer | Responsibility | Dependencies |
|-------|---------------|-------------|
| **common** | Records, enums, `ConfigProvider` interface | JDK only |
| **data-plane** | NIO event loop, load balancing, TCP proxying | common + micrometer + slf4j |
| **control-plane** | REST API, JPA, health checker, config rebuild | common + Spring Boot |
| **app** | Wires both planes, graceful shutdown | all modules |

Data plane has **zero Spring dependencies** — communicates with control plane only through `AtomicReference<ConfigSnapshot>`. This mirrors how Envoy/xDS work: if you ever need to split into separate processes, the interface is already defined.

## Balancing Algorithms

- **Round Robin** — equal distribution across healthy upstreams
- **Weighted Round Robin** — proportional to upstream weight
- **Least Connections** — picks upstream with fewest active connections

Sticky sessions supported per-pool with configurable TTL.

## Quick Start

```bash
# Build
./mvnw clean package -DskipTests

# Run everything with Docker Compose
docker compose up --build

# Or run locally (needs PostgreSQL)
export DB_HOST=localhost DB_PORT=5432 DB_NAME=balanced DB_USER=balanced DB_PASS=balanced
java -jar app/target/balanced-app-0.1.0-SNAPSHOT.jar
```

## API Examples

```bash
# Create upstreams
curl -X POST http://localhost:8081/api/upstreams \
  -H 'Content-Type: application/json' \
  -d '{"host":"127.0.0.1","port":9001,"weight":1}'

curl -X POST http://localhost:8081/api/upstreams \
  -H 'Content-Type: application/json' \
  -d '{"host":"127.0.0.1","port":9002,"weight":2}'

# Create a pool
curl -X POST http://localhost:8081/api/pools \
  -H 'Content-Type: application/json' \
  -d '{"name":"web","algorithm":"WEIGHTED_ROUND_ROBIN","stickyEnabled":false,"stickyTtlSeconds":0,"upstreamIds":[1,2]}'

# Create a listener (binds port 8080 → pool "web")
curl -X POST http://localhost:8081/api/listeners \
  -H 'Content-Type: application/json' \
  -d '{"port":8080,"pool":{"id":1}}'

# Send traffic through the load balancer
curl http://localhost:8080/

# Check health status
curl http://localhost:8081/api/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

## Monitoring

| Service | URL |
|---------|-----|
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |
| Control plane API | http://localhost:8081 |
| Load balancer (data) | http://localhost:8080 |

Key metrics: `lb_active_connections`, `lb_connections_total`, `lb_bytes_total`, `lb_upstream_health`, `lb_health_check_duration_seconds`.

## Project Structure

```
balanced/
├── pom.xml                          # parent POM, module definitions
├── common/                          # ConfigSnapshot, Upstream, Pool, Listener
├── data-plane/                      # NIO event loop, balancers, zero Spring
├── control-plane/                   # Spring Boot REST + JPA + health checker
├── app/                             # main class, structured logging config
├── upstream-mock/                   # trivial HTTP server for testing
├── docker-compose.yml               # full stack: LB + upstreams + DB + monitoring
├── prometheus.yml                   # scrape config
├── grafana/                         # provisioned datasource + dashboard
└── .github/workflows/ci.yml         # GitHub Actions: JDK 21, mvn verify
```

## Tests

```bash
./mvnw test
```

24 tests: unit (balancers, ConfigSnapshot), integration (REST + H2), E2E (real TCP through LB).

## Design Decisions

- **Single thread NIO** — no thread pool, no races, predictable latency. One selector handles tens of thousands of connections.
- **Immutable ConfigSnapshot** — atomic swap via `AtomicReference`, no locks on hot path.
- **Health status in memory only** — DB stores declarative config (what should exist), runtime state is computed. On restart, first health check cycle restores it.
- **DTO layer** — JPA entities never exposed in API responses. Validation at boundary.
- **Structured logging** — MDC carries `pool`, `upstream`, `configVersion`. JSON in Docker, human-readable locally.
- **Dynamic listener rebinding** — config changes take effect without process restart.
- **Back-pressure** — OP_READ/OP_WRITE toggling prevents buffer overflow when one side is slow.

## Tech Stack

Java 21, Spring Boot 3.4, JPA/Hibernate, Flyway, PostgreSQL, Micrometer, Prometheus, Grafana, Logback + logstash-encoder, JUnit 5, AssertJ, H2 (tests).