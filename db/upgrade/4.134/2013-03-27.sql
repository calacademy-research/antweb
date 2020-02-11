#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.134/2013-03-27.sql
#



alter table bioregion add column biogeographicregion varchar(250);

// Afrotropic should be African
update bioregion set biogeographicregion = "africanants" where name ="Afrotropic";
update bioregion set biogeographicregion = "malagasyants" where name ="Malagasy";
update bioregion set biogeographicregion = "nearcticants" where name ="Nearctic";
update bioregion set biogeographicregion = "australianants" where name = "Australasia";
update bioregion set biogeographicregion = "neotropicalants" where name = "Neotropic";
update bioregion set biogeographicregion = "eurasianants" where name = "Palearctic";
update bioregion set biogeographicregion = "southeastasiaants" where name = "Indomalaya";
update bioregion set biogeographicregion = "pacificislandsants" where name = "Oceania"


delete  from taxon where subfamily is null;

alter table long_request add column is_bot varchar(20);

drop table if exists long_request;

CREATE TABLE `long_request` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `cache_type` varchar(30) NOT NULL,
  `url` varchar(200) NOT NULL,
  `dir_file` varchar(200) NOT NULL,
  `millis` int(11) NOT NULL,
  `is_logged_in` tinyint(4) NOT NULL,
  `curator_id` int(11) NOT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cached` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `cache_millis` int(11) DEFAULT NULL,
  `request_info` varchar(2000) DEFAULT NULL,
  `busy_connections` int(2) DEFAULT NULL,
  `is_bot` varchar(250) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=107114 DEFAULT CHARSET=latin1; 
