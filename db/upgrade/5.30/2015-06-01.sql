#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.30/2015-06-01.sql
#

drop table event;

create table event (
    id int(11) NOT NULL auto_increment
  , operation varchar(64) NOT NULL
  , curator_id int(11)
  , name varchar(64) NOT NULL
  , created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  , PRIMARY KEY  (id)
);


create table description_edit_plazi20150601 as
select * from description_edit where title = "plaziData";