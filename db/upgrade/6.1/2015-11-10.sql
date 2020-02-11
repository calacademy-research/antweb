drop table if exists object_edit;
CREATE TABLE object_edit (
  object_key varchar(128) NOT NULL,
  title varchar(32) NOT NULL,
  content longtext,
  edit_id int(10) NOT NULL DEFAULT '0',
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_manual_entry tinyint(4) NOT NULL,
  access_group int(11) DEFAULT NULL,
  access_login int(11) DEFAULT NULL,
  UNIQUE KEY object_key (object_key, title)
  );

drop table if exists object_hist;
CREATE TABLE object_hist (
  object_key varchar(128) CHARACTER SET utf8 NOT NULL,
  title varchar(32) CHARACTER SET utf8 NOT NULL,
  content longtext CHARACTER SET utf8,
  edit_id int(10) NOT NULL DEFAULT '0',
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  hist_id int(10) NOT NULL AUTO_INCREMENT,
  access_group varchar(32) DEFAULT NULL,
  access_login int(11) DEFAULT NULL,
  PRIMARY KEY (hist_id)
);  

update project set display_key = "Comoros" where project_name = "comorosants";
  