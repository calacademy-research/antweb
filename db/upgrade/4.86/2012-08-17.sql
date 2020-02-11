#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.86/2012-08-17.sql
#

drop table if exists long_request;

create table long_request (
  id int(11) NOT NULL auto_increment,
  cache_type varchar(30) not null,
  url varchar(200) not null,
  dir_file varchar(200) not null,
  millis int not null,
  is_logged_in tinyint(4) NOT NULL,
  curator_id int(11) not null,
  created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  cached timestamp,
  PRIMARY KEY  (id)
);

alter table long_request modify column request_info varchar(2000);

insert into copyright values (10, "California Academy of Sciences, 2002-2012");

alter table long_request add column busy_connections int(2);

update groups set name = "Robert J. Higgins" where id = 18;


alter table taxon add column valid_name int(11);
# update taxon t set valid_name = 1 where t.taxon_name in (select taxon_name from description_edit where title = "taxonomichistory");

alter table description_hist modify access_group varchar(32);


delete from proj_taxon where taxon_name = "formicidae";

insert into proj_taxon (project_name, taxon_name) 
select distinct project_name, "formicidae" from proj_taxon;

update taxon set rank = "genus" where taxon_name = "dorylinaedorylus";

#select taxon_name from taxon where rank = "species" and (species is null or species = "");
#select taxon_name from taxon where rank = "genus" and (genus is null or genus = "");
#select taxon_name from taxon where rank = "subfamily" and (subfamily is null or subfamily = "");

# Not performed in production.  Made no difference on dev machine.  Strategy aborted.
alter table specimen modify column datedetermined datetime;
