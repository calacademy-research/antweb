#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.49/2012-01-27.sql
#

create table bioregion (
  `name` varchar(64) NOT NULL,
  `description` varchar(1000) NOT NULL,
  `created` timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (name)
);

create table country (
  `name` varchar(64) NOT NULL,
  `iso_code` varchar(64) NOT NULL,
  `created` timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (name)
);

