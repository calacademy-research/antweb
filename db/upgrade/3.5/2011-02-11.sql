# 3.5/2011-02-11.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/3.5/2011-02-11.sql
#

# Strategy is to move functionality as simply as possible from groups to login.  
# Then, create the new group table.  Leave it groups - group is a reserved word.

create table login (
  `id` int(11) NOT NULL auto_increment,
  `first_name` varchar(64) default NULL,
  `last_name` varchar(64) NOT NULL,
  `email` varchar(128) NOT NULL,
  `name` varchar(32) NOT NULL,
  `title` varchar(128) NOT NULL,
  `password` varchar(32) NOT NULL,
  `group_id` int(11),
  `created` timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (id)
);

insert into login (first_name, last_name, email, name, title, password, group_id)
 select contact_first_name, contact_last_name, contact_email, name, title, password, uid from groups;

# To Do
alter table groups drop column contact_first_name;
alter table groups drop column contact_last_name;
alter table groups drop column contact_email;
alter table groups drop column title;
alter table groups drop column password;
# Do we live with the groups primary key column being called uid?  Yes.

alter table groups add column admin_login_id int(11) not null;
alter table groups add column is_upload_specimens tinyint not null;
alter table groups add column is_upload_images tinyint not null;

update groups set is_upload_specimens = 1 where uid in (1, 2, 16);
update groups set is_upload_images = 1 where uid in (1, 2, 16);

update groups set admin_login_id = uid;

update groups g set g.name = (select l.title from login l where l.group_id = g.uid); 

# Make luke like the future.  Members of other groups.
update login set group_id = 1 where id = 4;
delete from groups where uid = 4;
delete from groups_project where group_id = 4;

alter table groups change uid id int(11) NOT NULL auto_increment;
alter table login drop column title;

alter table login add column is_admin tinyint not null;
update login set is_admin = 1 where id = 1;

insert into login (first_name, last_name, email, name, password, group_id) values ("Mark", "Johnson", "mark@inforaction.com", "mark", "markjam", 1);
update login set is_admin = 1 where name = "mark";

#alter table login add column is_active tinyint not null;     # We could just change the password to in@ctive
#update login set is_admin = 1 where id = 1;


#insert into project(project_name, project_title) values ('None', 'No Project(s)');

insert into groups values (0, "Default Group", null, null, 0, 0, 0);
update groups set id = 0 where id = 22;

# Make sure Default Group is 0.  May have to modify above update.
