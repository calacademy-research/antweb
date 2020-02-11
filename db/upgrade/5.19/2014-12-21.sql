#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.19/2014-12-21.sql
#

alter table description_edit add column code varchar(128) default null;

alter table description_edit drop primary key;

alter table description_edit add unique key (taxon_name, title, code);

alter table description_hist add column code varchar(128) default null;


drop table if exists proj_taxon_log;
create table proj_taxon_log (
  log_id int(11) NOT NULL auto_increment,
  project_name varchar(32),
  curator_id int(11) not null,
  created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_current tinyint(4) NOT NULL,
  PRIMARY KEY (log_id)
);

drop table if exists proj_taxon_log_detail;
create table proj_taxon_log_detail as select * from proj_taxon where 1=0;
alter table proj_taxon_log_detail add column log_id int(11);
alter table proj_taxon_log_detail add primary key (log_id, project_name, taxon_name);
