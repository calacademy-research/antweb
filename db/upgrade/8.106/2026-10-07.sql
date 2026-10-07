-- Measurements table for the myrmetrics ant body-size dataset (Zhao, Boyko et al.).
-- One row per image x trait x release. Columns mirror the myrmetrics release TSV
-- (SCHEMA.md), except: release -> data_release and view -> image_view
-- (both are MySQL reserved words). Loaded by api/v3.1/load_measurements.py;
-- served read-only at /v3.1/measurements.
-- Later user submissions go to a separate staging table, not here: the unique key
-- allows one value per image, trait and release.

CREATE TABLE IF NOT EXISTS measurement (
  id                  int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  data_release        varchar(16)  NOT NULL,
  record_id           varchar(200) NOT NULL,
  specimen_code       varchar(128) NOT NULL,
  image_filename      varchar(200) NOT NULL,
  image_url           varchar(400) DEFAULT NULL,
  image_view          varchar(8)   NOT NULL,
  image_width         int          DEFAULT NULL,
  image_height        int          DEFAULT NULL,
  trait               varchar(8)   NOT NULL,
  trait_name          varchar(40)  DEFAULT NULL,
  value               double       DEFAULT NULL,
  unit                varchar(8)   DEFAULT 'mm',
  value_px            double       DEFAULT NULL,
  trait_kp_a          varchar(32)  DEFAULT NULL,
  trait_kp_b          varchar(32)  DEFAULT NULL,
  scale_value_raw     double       DEFAULT NULL,
  scale_unit_raw      varchar(8)   DEFAULT NULL,
  scale_mm            double       DEFAULT NULL,
  scale_value_source  varchar(24)  DEFAULT NULL,
  scale_bar_px        double       DEFAULT NULL,
  scale_bar_source    varchar(24)  DEFAULT NULL,
  px_per_mm           double       DEFAULT NULL,
  kp1_name varchar(32) DEFAULT NULL, kp1_x double DEFAULT NULL, kp1_y double DEFAULT NULL, kp1_conf double DEFAULT NULL,
  kp2_name varchar(32) DEFAULT NULL, kp2_x double DEFAULT NULL, kp2_y double DEFAULT NULL, kp2_conf double DEFAULT NULL,
  kp3_name varchar(32) DEFAULT NULL, kp3_x double DEFAULT NULL, kp3_y double DEFAULT NULL, kp3_conf double DEFAULT NULL,
  kp4_name varchar(32) DEFAULT NULL, kp4_x double DEFAULT NULL, kp4_y double DEFAULT NULL, kp4_conf double DEFAULT NULL,
  kp5_name varchar(32) DEFAULT NULL, kp5_x double DEFAULT NULL, kp5_y double DEFAULT NULL, kp5_conf double DEFAULT NULL,
  kp6_name varchar(32) DEFAULT NULL, kp6_x double DEFAULT NULL, kp6_y double DEFAULT NULL, kp6_conf double DEFAULT NULL,
  n_detections        int          DEFAULT NULL,
  keypoints_corrected tinyint(1)   DEFAULT NULL,
  scale_corrected     tinyint(1)   DEFAULT NULL,
  qa_kp_ok            tinyint(1)   DEFAULT NULL,
  qa_ruler_ok         tinyint(1)   DEFAULT NULL,
  qa_missing_head     tinyint(1)   DEFAULT NULL,
  qa_missing_gaster   tinyint(1)   DEFAULT NULL,
  status              varchar(16)  NOT NULL,
  flag_reason         varchar(40)  DEFAULT NULL,
  flag_note           text,
  pose_model          varchar(64)  DEFAULT NULL,
  ruler_model         varchar(64)  DEFAULT NULL,
  contributor         varchar(128) DEFAULT NULL,
  record_date         date         DEFAULT NULL,
  loaded              timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_release_record (data_release, record_id),
  KEY idx_specimen (specimen_code),
  KEY idx_release_trait (data_release, trait),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
