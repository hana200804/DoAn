CREATE TABLE label_tasks (
  id CHAR(36) NOT NULL PRIMARY KEY,
  observation_id CHAR(36) NOT NULL,
  reason VARCHAR(32) NOT NULL,
  status ENUM ('OPEN', 'LABELED', 'SKIPPED') NOT NULL DEFAULT 'OPEN',
  label_code VARCHAR(20) NULL,
  label_box JSON NULL,
  resolved_by CHAR(36) NULL,
  resolved_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_label_observation (observation_id),
  KEY ix_label_status (status, created_at),
  CONSTRAINT fk_label_observation FOREIGN KEY (observation_id) REFERENCES observations (id),
  CONSTRAINT fk_label_code FOREIGN KEY (label_code) REFERENCES sign_types (code)
) ENGINE = InnoDB;

-- A lease permits retry after a worker crashes; inference never holds a cluster lock.
CREATE TABLE observation_analysis (
  observation_id CHAR(36) NOT NULL PRIMARY KEY,
  status ENUM ('PENDING', 'PROCESSING', 'RETRY', 'DONE', 'FAILED') NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  generation INT NOT NULL DEFAULT 0,
  available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  result JSON NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY ix_analysis_due (status, available_at),
  CONSTRAINT fk_analysis_observation FOREIGN KEY (observation_id) REFERENCES observations (id)
) ENGINE = InnoDB;

ALTER TABLE sign_clusters ADD COLUMN conditions_confidence DOUBLE NOT NULL DEFAULT 0;

CREATE TABLE model_versions (
  version VARCHAR(20) NOT NULL PRIMARY KEY,
  label_map_version INT NOT NULL,
  min_app_version VARCHAR(32) NOT NULL,
  detector_key VARCHAR(255) NOT NULL,
  detector_sha256 CHAR(64) NOT NULL,
  detector_size BIGINT NOT NULL,
  classifier_key VARCHAR(255) NOT NULL,
  classifier_sha256 CHAR(64) NOT NULL,
  classifier_size BIGINT NOT NULL,
  created_by CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB;

-- A single pointer serializes activation and rollback without two active versions.
CREATE TABLE model_release (
  id INT NOT NULL PRIMARY KEY,
  version VARCHAR(20) NULL,
  activated_by CHAR(36) NULL,
  activated_at DATETIME(3) NULL,
  CONSTRAINT fk_release_model FOREIGN KEY (version) REFERENCES model_versions (version)
) ENGINE = InnoDB;
INSERT INTO model_release (id) VALUES (1);

-- Serialize clustering across service replicas so simultaneous first sightings create one cluster.
CREATE TABLE cluster_ingestion_lock (id INT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT INTO cluster_ingestion_lock (id) VALUES (1);
