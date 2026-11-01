CREATE TABLE sign_types (
  code              VARCHAR(20)  NOT NULL,
  group_code        ENUM ('PROHIBITORY', 'MANDATORY', 'WARNING', 'AUXILIARY', 'OTHER') NOT NULL,
  name              VARCHAR(200) NOT NULL,
  description       TEXT         NULL,
  speed_kmh         SMALLINT     NULL,
  label_map_version INT          NOT NULL DEFAULT 1,
  active            BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (code)
) ENGINE = InnoDB;

CREATE TABLE sign_clusters (
  id                CHAR(36)      NOT NULL,
  sign_code         VARCHAR(20)   NOT NULL,
  lat               DOUBLE        NOT NULL,
  lng               DOUBLE        NOT NULL,
  heading_deg       SMALLINT      NOT NULL,
  alpha             DOUBLE        NOT NULL DEFAULT 1,
  beta              DOUBLE        NOT NULL DEFAULT 1,
  negative_weight   DOUBLE        NOT NULL DEFAULT 0,
  confidence        DOUBLE        NOT NULL DEFAULT 0.5,
  status            ENUM ('CANDIDATE', 'VERIFIED', 'CONFLICT', 'MISSING_SUSPECTED', 'REJECTED') NOT NULL DEFAULT 'CANDIDATE',
  admin_locked      BOOLEAN       NOT NULL DEFAULT FALSE,
  conditions        JSON          NULL,
  observation_count INT           NOT NULL DEFAULT 0,
  distinct_users    INT           NOT NULL DEFAULT 0,
  last_seen_at      DATETIME(3)   NOT NULL,
  created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_clusters_lat_lng (lat, lng),
  KEY ix_clusters_status (status),
  CONSTRAINT fk_clusters_code FOREIGN KEY (sign_code) REFERENCES sign_types (code)
) ENGINE = InnoDB;

CREATE TABLE cluster_code_weights (
  cluster_id CHAR(36)    NOT NULL,
  sign_code  VARCHAR(20) NOT NULL,
  weight     DOUBLE      NOT NULL,
  PRIMARY KEY (cluster_id, sign_code),
  CONSTRAINT fk_weights_cluster FOREIGN KEY (cluster_id) REFERENCES sign_clusters (id),
  CONSTRAINT fk_weights_code FOREIGN KEY (sign_code) REFERENCES sign_types (code)
) ENGINE = InnoDB;

CREATE TABLE observation_batches (
  id                CHAR(36)    NOT NULL,
  user_id           CHAR(36)    NOT NULL,
  idempotency_key   VARCHAR(64) NOT NULL,
  status            ENUM ('PENDING_UPLOAD', 'PROCESSED') NOT NULL DEFAULT 'PENDING_UPLOAD',
  model_version     VARCHAR(20) NOT NULL,
  label_map_version INT         NOT NULL,
  device_model      VARCHAR(100) NULL,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  processed_at      DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batches_idempotency (user_id, idempotency_key)
) ENGINE = InnoDB;

CREATE TABLE observations (
  id             CHAR(36)     NOT NULL,
  batch_id       CHAR(36)     NOT NULL,
  client_id      VARCHAR(64)  NOT NULL,
  cluster_id     CHAR(36)     NULL,
  user_id        CHAR(36)     NOT NULL,
  kind           ENUM ('POSITIVE', 'NEGATIVE') NOT NULL,
  sign_code      VARCHAR(20)  NULL,
  detector_group VARCHAR(20)  NULL,
  probability    DOUBLE       NULL,
  vote_share     DOUBLE       NULL,
  weight         DOUBLE       NOT NULL DEFAULT 0,
  lat            DOUBLE       NOT NULL,
  lng            DOUBLE       NOT NULL,
  heading_deg    SMALLINT     NOT NULL,
  distance_m     DOUBLE       NULL,
  frames         INT          NULL,
  crop_key       VARCHAR(255) NULL,
  sub_sign_key   VARCHAR(255) NULL,
  flags          JSON         NULL,
  observed_at    DATETIME(3)  NOT NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_obs_cluster (cluster_id, kind, observed_at),
  KEY ix_obs_user (user_id, observed_at),
  CONSTRAINT fk_obs_batch FOREIGN KEY (batch_id) REFERENCES observation_batches (id),
  CONSTRAINT fk_obs_cluster FOREIGN KEY (cluster_id) REFERENCES sign_clusters (id)
) ENGINE = InnoDB;

CREATE TABLE contributors (
  user_id    CHAR(36)    NOT NULL,
  reputation DOUBLE      NOT NULL DEFAULT 1.0,
  agreed     INT         NOT NULL DEFAULT 0,
  disagreed  INT         NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id)
) ENGINE = InnoDB;

CREATE TABLE sign_reports (
  id              CHAR(36)     NOT NULL,
  cluster_id      CHAR(36)     NOT NULL,
  user_id         CHAR(36)     NOT NULL,
  reason          ENUM ('NOT_EXIST', 'WRONG_TYPE', 'WRONG_POSITION', 'OTHER') NOT NULL,
  note            VARCHAR(500) NULL,
  status          ENUM ('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  resolution_note VARCHAR(500) NULL,
  resolved_by     CHAR(36)     NULL,
  resolved_at     DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_reports_status (status, created_at),
  CONSTRAINT fk_reports_cluster FOREIGN KEY (cluster_id) REFERENCES sign_clusters (id)
) ENGINE = InnoDB;

CREATE TABLE cluster_events (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  cluster_id CHAR(36)     NOT NULL,
  actor_type ENUM ('SYSTEM', 'ADMIN') NOT NULL,
  actor_id   CHAR(36)     NULL,
  action     ENUM ('CREATED', 'STATUS_CHANGED', 'CODE_CHANGED', 'REJECTED') NOT NULL,
  old_value  VARCHAR(100) NULL,
  new_value  VARCHAR(100) NULL,
  reason     VARCHAR(500) NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_events_cluster (cluster_id),
  CONSTRAINT fk_events_cluster FOREIGN KEY (cluster_id) REFERENCES sign_clusters (id)
) ENGINE = InnoDB;
