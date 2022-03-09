

alter table upload add column backup_dir_file varchar(64);

update proj_taxon set subfamily_count = 0 where project_name = "bayareaants" and subfamily_count = 1;

