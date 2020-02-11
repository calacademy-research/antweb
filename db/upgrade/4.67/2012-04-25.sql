#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.67/2012-04-25.sql
#

drop table image_catalog;

create table image_catalog (
  `id` int(11) NOT NULL auto_increment,
  `dir_name` varchar(64) NOT NULL,
  `image_name` varchar(64) NOT NULL,
  `created` timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (id)
);

create index image_catalog_dir_name_idx on image_catalog (dir_name);



  

