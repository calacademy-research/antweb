#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.119/2013-03-09.sql
#


create table image_like (
  id int(11) NOT NULL auto_increment,
  image_id int(11) not null,
  group_id int(11),
  login_id int(11),
  created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY  (id)
);

#insert into login (name, password) values ("like", "ants");