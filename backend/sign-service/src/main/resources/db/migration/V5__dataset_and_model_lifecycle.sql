ALTER TABLE label_tasks
  ADD COLUMN dataset_version VARCHAR(10) NULL,
  ADD COLUMN ocr_status ENUM ('NOT_REQUIRED','PENDING','REVIEWED','SKIPPED') NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD KEY ix_label_dataset (dataset_version, status);
UPDATE label_tasks t JOIN observations o ON o.id = t.observation_id
SET t.ocr_status = CASE WHEN t.conditions IS NOT NULL THEN 'REVIEWED'
    WHEN t.status = 'SKIPPED' THEN 'SKIPPED' ELSE 'PENDING' END
WHERE o.sub_sign_key IS NOT NULL;
-- Existing reviewed labels remain available in a named legacy dataset.
UPDATE label_tasks SET dataset_version = 'v1.0' WHERE label_code IS NOT NULL;
UPDATE label_tasks SET status = 'OPEN' WHERE status = 'LABELED' AND ocr_status = 'PENDING';

ALTER TABLE model_versions
  ADD COLUMN status ENUM ('DRAFT','ACTIVE','RETIRED') NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN metrics JSON NULL,
  ADD COLUMN released_at DATETIME(3) NULL,
  ADD KEY ix_model_status (label_map_version, status);
-- Older registrations lack release evidence. Keep the artifacts as drafts until a real card is supplied.
UPDATE model_versions SET metrics = JSON_OBJECT();
ALTER TABLE model_versions MODIFY COLUMN metrics JSON NOT NULL;
-- A channel per label map allows older compatible clients to keep receiving releases.
ALTER TABLE model_release CHANGE COLUMN id label_map_version INT NOT NULL;
UPDATE model_release r JOIN model_versions m ON m.version = r.version
SET r.label_map_version = m.label_map_version;
UPDATE model_release SET version = NULL, activated_by = NULL, activated_at = NULL;
