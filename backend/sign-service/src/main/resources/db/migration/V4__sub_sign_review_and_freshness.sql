ALTER TABLE observations ADD COLUMN has_sub_sign BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE observations SET has_sub_sign = TRUE WHERE sub_sign_key IS NOT NULL;

ALTER TABLE sign_clusters
  ADD COLUMN conditions_observed_at DATETIME(3) NULL,
  ADD COLUMN conditions_observation_id CHAR(36) NULL,
  ADD COLUMN conditions_reviewed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sign_clusters SET conditions_observed_at = last_seen_at WHERE conditions IS NOT NULL;

ALTER TABLE label_tasks
  ADD COLUMN ocr_text TEXT NULL,
  ADD COLUMN conditions JSON NULL;

ALTER TABLE cluster_events MODIFY COLUMN action
  ENUM ('CREATED', 'STATUS_CHANGED', 'CODE_CHANGED', 'CONDITIONS_CHANGED', 'REJECTED') NOT NULL;
