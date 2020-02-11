#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.53/2012-02-16.sql
#

drop table operation_lock;

create table operation_lock ( 
  id int(11) NOT NULL auto_increment,
  operation int not null,
  duration_millis int not null,
  locked tinyint(4) NOT NULL,
  curator_id int(11) not null,
  curator varchar(1000) NOT NULL,
  created timestamp default '0000-00-00 00:00:00',
  modified timestamp,
  PRIMARY KEY  (id)
);
