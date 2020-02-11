#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.16/2011-10-17.sql
#


alter table taxon add column source varchar(64);


create table statistics (
  id int(11) NOT NULL auto_increment,
  specimens int(11) default NULL,
  extant_taxa int(11),
  specimens_imaged int(11),
  species_imaged int(11),
  login_id int(11),  
  created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (id)
);



alter table taxon add column family varchar(65) default "formicidae";
alter table specimen add column family varchar(65) default "formicidae";








