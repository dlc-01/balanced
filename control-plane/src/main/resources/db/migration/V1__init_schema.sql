CREATE TABLE upstream (
    id         BIGSERIAL PRIMARY KEY,
    host       VARCHAR(255) NOT NULL,
    port       INT          NOT NULL,
    weight     INT          NOT NULL DEFAULT 1,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE pool (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL UNIQUE,
    algorithm          VARCHAR(50)  NOT NULL DEFAULT 'ROUND_ROBIN',
    sticky_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    sticky_ttl_seconds INT          NOT NULL DEFAULT 300
);

CREATE TABLE pool_upstream (
    pool_id     BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE,
    upstream_id BIGINT NOT NULL REFERENCES upstream(id) ON DELETE CASCADE,
    PRIMARY KEY (pool_id, upstream_id)
);

CREATE TABLE listener (
    id      BIGSERIAL PRIMARY KEY,
    port    INT    NOT NULL UNIQUE,
    pool_id BIGINT NOT NULL REFERENCES pool(id) ON DELETE CASCADE
);