# 3.5/2011-03-17.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.1.1/2011-03-17.sql
#

# Implement a group/login redesign so that properties associated with groups are associated with login.
 
alter table login add column is_upload_specimens tinyint not null;
alter table login add column is_upload_images tinyint not null;

update login l set l.is_upload_specimens = 1 where l.group_id in (
  select g.id from groups g where g.is_upload_specimens = 1);

update login l set l.is_upload_images = 1 where l.group_id in (
  select g.id from groups g where g.is_upload_images = 1);

create table login_project as select * from groups_project;
alter table login_project change group_id login_id int(11);

# This can be done later.
# This would set James Trager to the default Group and delete Traeger Curator.
#update login set group_id = 0 where id = 5
#delete from groups where id = 5;

#alter table groups remove extra columns
#is_upload_specimens
#is_upload_images

alter table login modify column is_upload_specimens tinyint(4) not null default 0;
alter table login modify column is_upload_images tinyint(4) not null default 0;