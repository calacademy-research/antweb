SET sql_mode = '';

DROP TABLE if exists ant.image_catalog;
DROP TABLE if exists ant.image_catalog2;
DROP TABLE if exists ant.imageBak;

ALTER TABLE `ant`.`adm1` ENGINE = InnoDB;
ALTER TABLE `ant`.`admin_alerts` ENGINE = InnoDB;
ALTER TABLE `ant`.`ancillary` ENGINE = InnoDB;
ALTER TABLE `ant`.`antwiki_fossil_taxa` ENGINE = InnoDB;
ALTER TABLE `ant`.`antwiki_taxon_country` ENGINE = InnoDB;
ALTER TABLE `ant`.`antwiki_valid_taxa` ENGINE = InnoDB;
ALTER TABLE `ant`.`artist_group` ENGINE = InnoDB;
ALTER TABLE `ant`.`bioregion` ENGINE = InnoDB;
ALTER TABLE `ant`.`bioregion_taxon` ENGINE = InnoDB;
ALTER TABLE `ant`.`country` ENGINE = InnoDB;
ALTER TABLE `ant`.`country_bioregion` ENGINE = InnoDB;
ALTER TABLE `ant`.`description_edit` ENGINE = InnoDB;
ALTER TABLE `ant`.`description_hist` ENGINE = InnoDB;

# default is zero date, when it shouldn't be
ALTER TABLE `ant`.`description_homonym` MODIFY created timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE `ant`.`description_homonym` ENGINE = InnoDB;
ALTER TABLE `ant`.`event` ENGINE = InnoDB;
ALTER TABLE `ant`.`geolocale` ENGINE = InnoDB;
ALTER TABLE `ant`.`geolocale_taxon` ENGINE = InnoDB;
ALTER TABLE `ant`.`geolocale_taxon_dispute` ENGINE = InnoDB;
ALTER TABLE `ant`.`geolocale_taxon_log` ENGINE = InnoDB;
ALTER TABLE `ant`.`geolocale_taxon_log_detail` ENGINE = InnoDB;
ALTER TABLE `ant`.`group_image` ENGINE = InnoDB;

ALTER TABLE `ant`.`image_count` ENGINE = InnoDB;
ALTER TABLE `ant`.`image_like` ENGINE = InnoDB;
ALTER TABLE `ant`.`image_upload` ENGINE = InnoDB;
ALTER TABLE `ant`.`image_uploaded` ENGINE = InnoDB;
ALTER TABLE `ant`.`login` ENGINE = InnoDB;
ALTER TABLE `ant`.`login_country` ENGINE = InnoDB;
ALTER TABLE `ant`.`login_project` ENGINE = InnoDB;
ALTER TABLE `ant`.`login_project_bak` ENGINE = InnoDB;
ALTER TABLE `ant`.`long_request` ENGINE = InnoDB;
ALTER TABLE `ant`.`lookup` ENGINE = InnoDB;
ALTER TABLE `ant`.`museum` ENGINE = InnoDB;
ALTER TABLE `ant`.`museum_taxon` ENGINE = InnoDB;
ALTER TABLE `ant`.`object_edit` ENGINE = InnoDB;
ALTER TABLE `ant`.`object_hist` ENGINE = InnoDB;
ALTER TABLE `ant`.`object_map` ENGINE = InnoDB;
ALTER TABLE `ant`.`operation_lock` ENGINE = InnoDB;
ALTER TABLE `ant`.`proj_taxon` ENGINE = InnoDB;
ALTER TABLE `ant`.`proj_taxon_dispute` ENGINE = InnoDB;
ALTER TABLE `ant`.`proj_taxon_log` ENGINE = InnoDB;
ALTER TABLE `ant`.`proj_taxon_log_detail` ENGINE = InnoDB;
ALTER TABLE `ant`.`server` ENGINE = InnoDB;
ALTER TABLE `ant`.`statistics` ENGINE = InnoDB;
ALTER TABLE `ant`.`taxon_country` ENGINE = InnoDB;
ALTER TABLE `ant`.`taxon_prop` ENGINE = InnoDB;
ALTER TABLE `ant`.`taxon_prop20170617` ENGINE = InnoDB;
ALTER TABLE `ant`.`team_member` ENGINE = InnoDB;
ALTER TABLE `ant`.`un_country` ENGINE = InnoDB;
ALTER TABLE `ant`.`upload` ENGINE = InnoDB;
ALTER TABLE `ant`.`upload_line` ENGINE = InnoDB;
ALTER TABLE `ant`.`user_agent` ENGINE = InnoDB;
ALTER TABLE `ant`.`worldants_upload` ENGINE = InnoDB;
