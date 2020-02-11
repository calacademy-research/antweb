#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.1.7/2011-04-11.sql
#

alter table specimen add column access_login int(11);

alter table ancillary add column access_login int(11);


#

insert into ancillary (title, fileName, directory, contents, last_changed, project_name, access_login) 
select title, fileName, directory, contents, last_changed,	 project_name, access_login from ancillary where id = 9;
# The id of the generated record should be included in the webapp/team/staff.jsp

update ancillary set directory = "team" where id = 23;

insert into ancillary (title, fileName, directory, contents, last_changed, project_name, access_login) select title, fileName, directory, contents, last_changed, project_name, access_login from ancillary where id = 23;

update ancillary set title="Antweb Curators", fileName = "curators" where id = 24;
# The id of the generated record should be included in the webapp/team/staff.jsp


CREATE TABLE `team_member` (
  `id` int(5) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `role_org` varchar(255) DEFAULT NULL,
  `email` varchar(60) DEFAULT NULL,
  `img_loc` varchar(60) DEFAULT NULL,
  `img_width` int(5) DEFAULT NULL,
  `img_height` int(5) DEFAULT NULL,
  `img_file_name` text,
  `img_file_type` text,
  `img_file_size` text,
  `img_file_bin` longblob,
  `text` varchar(5000) DEFAULT NULL,
  `section` int(2) DEFAULT NULL,
  `rank` int(4) DEFAULT NULL,
  `is_published` bit(1) DEFAULT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=44 DEFAULT CHARSET=latin1;

insert into team_member (name, role_org, email, img_loc, section, rank, is_published, text) 
values ('Phil Ward', 'Professor, Entomology, University of California, Davis', 'psward@ucdavis.edu', 'phil.jpg', 2, 50, 1, 'Ant systematist and project leader for Ants of California.');
insert into team_member (name, role_org, email, img_loc, section, rank, is_published, text) 
values ('James Trager', 'Missouri Botanical Garden', 'james.trager@mobot.org', 'james250.jpg', 2, 100, 1, 'Ant systematist and project leader for Ants of Illinois and Missouri.');
insert into team_member (name, role_org, email, img_loc, section, rank, is_published, text) 
values ('Jack Longino', 'Evergreen State College', 'longinoj@evergreen.edu', 'jack250.jpg', 2, 200, 1, 'Ant systematist and project leader for Ants of Central America.');
insert into team_member (name, role_org, email, img_loc, section, rank, is_published, text) 
values ('Lloyd Davis', 'Florida Ant Mafia', 'james.trager@mobot.org', 'lloydAndPheidoleMiliticidaCrop.jpg', 2, 300, 1, 'Lloyd is an expert on North American ants and oversees content for the NA region.');

update team_member set id = 16 where name = 'Phil Ward';
update team_member set id = 5 where name = 'James Trager';
update team_member set id = 2 where name = 'Jack Longino';
update team_member set id = 10 where name = 'Lloyd Davis';

