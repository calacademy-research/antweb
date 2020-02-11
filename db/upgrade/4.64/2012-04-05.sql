#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.64/2012-04-05.sql
#

delete from bioregion;

insert into bioregion (name, description) values ("Afrotropic", "including Sub-Saharan Africa");
insert into bioregion (name, description) values ("Malagasy", "Madagascar and Southwest Indian Ocean Islands including Seychelles, Comoros, Mascarenes");
insert into bioregion (name, description) values ("Palearctic", "including the bulk of Eurasia and North Africa");
insert into bioregion (name, description) values ("Australasia", "including Australia, New Guinea, and neighboring islands. The northern boundary of this zone is known as the Wallace line.");
insert into bioregion (name, description) values ("Indomalaya", "including the Indian subcontinent and Southeast Asia");
insert into bioregion (name, description) values ("Oceania", "Pacific Ocean islands including Polynesia, Melanesia, Micronesia.");
insert into bioregion (name, description) values ("Nearctic", "including most of North America");
insert into bioregion (name, description) values ("Neotropic", "including South America and the Caribbean");



update groups set abbrev = "TestGroup" where id = 25;

drop table upload;

create table upload (
  `id` int(11) NOT NULL auto_increment,
  `login_id` int(11) NOT NULL,
  `group_name` varchar(64) NOT NULL,
  `group_id` int(11) NOT NULL,
  `log_file_name` varchar(64) NOT NULL,
  `created` timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY  (id)
);

insert into upload (group_id, log_file_name) values (1, "20120126-16:58:05-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120203-08:44:32-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120216-09:10:42-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120306-06:57:31-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120314-16:37:36-outbiota1.txt");
insert into upload (group_id, log_file_name) values (25, "20120319-16:23:42-outbiota25.txt");
insert into upload (group_id, log_file_name) values (1, "20120402-08:57:49-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120126-16:59:02-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120203-09:19:44-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120217-09:35:05-outbiota1.txt");
insert into upload (group_id, log_file_name) values (24, "20120306-07:30:00-outbiota24.txt");
insert into upload (group_id, log_file_name) values (2, "20120314-17:02:39-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120320-08:51:19-outbiota1.txt");
insert into upload (group_id, log_file_name) values (24, "20120403-05:36:38-outbiota24.txt");
insert into upload (group_id, log_file_name) values (1, "20120126-17:03:29-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120207-09:01:55-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120222-10:11:15-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120306-12:23:55-outbiota1.txt");
insert into upload (group_id, log_file_name) values (21, "20120316-12:59:22-outbiota21.txt");
insert into upload (group_id, log_file_name) values (2, "20120320-19:15:00-outbiota2.txt");
insert into upload (group_id, log_file_name) values (24, "20120403-06:04:29-outbiota24.txt");
insert into upload (group_id, log_file_name) values (1, "20120127-09:04:58-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120208-08:48:49-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120223-11:20:53-outbiota1.txt");
insert into upload (group_id, log_file_name) values (24, "20120307-23:47:30-outbiota24.txt");
insert into upload (group_id, log_file_name) values (21, "20120316-17:21:06-outbiota21.txt");
insert into upload (group_id, log_file_name) values (2, "20120321-22:56:55-outbiota2.txt");
insert into upload (group_id, log_file_name) values (24, "20120404-09:25:36-outbiota24.txt");
insert into upload (group_id, log_file_name) values (1, "20120127-10:13:29-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120208-18:52:58-outbiota2.txt");
insert into upload (group_id, log_file_name) values (21, "20120227-08:53:23-outbiota21.txt");
insert into upload (group_id, log_file_name) values (1, "20120313-16:06:42-outbiota1.txt");
insert into upload (group_id, log_file_name) values (21, "20120316-17:46:12-outbiota21.txt");
insert into upload (group_id, log_file_name) values (1, "20120322-09:37:40-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120404-09:55:40-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120201-09:19:39-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120208-19:32:57-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120301-15:30:25-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120313-23:41:44-outbiota1.txt");
insert into upload (group_id, log_file_name) values (21, "20120316-17:47:38-outbiota21.txt");
insert into upload (group_id, log_file_name) values (2, "20120326-17:44:01-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120201-10:48:04-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120209-09:36:59-outbiota1.txt");
insert into upload (group_id, log_file_name) values (2, "20120305-08:55:40-outbiota2.txt");
insert into upload (group_id, log_file_name) values (1, "20120314-00:42:25-outbiota1.txt");
insert into upload (group_id, log_file_name) values (21, "20120316-17:48:32-outbiota21.txt");
insert into upload (group_id, log_file_name) values (1, "20120328-09:03:23-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120202-09:46:49-outbiota1.txt");
insert into upload (group_id, log_file_name) values (1, "20120210-08:54:28-outbiota1.txt");
insert into upload (group_id, log_file_name) values (24, "20120306-06:25:21-outbiota24.txt");
insert into upload (group_id, log_file_name) values (1, "20120314-08:54:54-outbiota1.txt");
insert into upload (group_id, log_file_name) values (21, "20120319-08:52:06-outbiota21.txt");
insert into upload (group_id, log_file_name) values (1, "20120329-14:28:14-outbiota1.txt");

update upload set created = "2012-01-26 16:58:05" where log_file_name = "20120126-16:58:05-outbiota1.txt";

update upload set created = "2012-02-03 08:44:32" where log_file_name = "20120203-08:44:32-outbiota2.txt";
update upload set created = "2012-02-16 09:10:42" where log_file_name = "20120216-09:10:42-outbiota1.txt";
update upload set created = "2012-03-06 06:57:31" where log_file_name = "20120306-06:57:31-outbiota2.txt";
update upload set created = "2012-03-14 16:37:36" where log_file_name = "20120314-16:37:36-outbiota1.txt";
update upload set created = "2012-03-19 16:23:42" where log_file_name = "20120319-16:23:42-outbiota25.txt";
update upload set created = "2012-04-02 08:57:49" where log_file_name = "20120402-08:57:49-outbiota1.txt";
update upload set created = "2012-01-26 16:59:02" where log_file_name = "20120126-16:59:02-outbiota1.txt";
update upload set created = "2012-02-03 09:19:44" where log_file_name = "20120203-09:19:44-outbiota2.txt";
update upload set created = "2012-02-17 09:35:05" where log_file_name = "20120217-09:35:05-outbiota1.txt";
update upload set created = "2012-03-06 07:30:00" where log_file_name = "20120306-07:30:00-outbiota24.txt";
update upload set created = "2012-03-14 17:02:39" where log_file_name = "20120314-17:02:39-outbiota2.txt";
update upload set created = "2012-03-20 08:51:19" where log_file_name = "20120320-08:51:19-outbiota1.txt";
update upload set created = "2012-04-03 05:36:38" where log_file_name = "20120403-05:36:38-outbiota24.txt";
update upload set created = "2012-01-26 17:03:29" where log_file_name = "20120126-17:03:29-outbiota1.txt";
update upload set created = "2012-02-07 09:01:55" where log_file_name = "20120207-09:01:55-outbiota1.txt";
update upload set created = "2012-02-22 10:11:15" where log_file_name = "20120222-10:11:15-outbiota2.txt";
update upload set created = "2012-03-06 12:23:55" where log_file_name = "20120306-12:23:55-outbiota1.txt";
update upload set created = "2012-03-16 12:59:22" where log_file_name = "20120316-12:59:22-outbiota21.txt";
update upload set created = "2012-03-20 19:15:00" where log_file_name = "20120320-19:15:00-outbiota2.txt";
update upload set created = "2012-04-03 06:04:29" where log_file_name = "20120403-06:04:29-outbiota24.txt";
update upload set created = "2012-01-27 09:04:58" where log_file_name = "20120127-09:04:58-outbiota1.txt";
update upload set created = "2012-02-08 08:48:49" where log_file_name = "20120208-08:48:49-outbiota1.txt";
update upload set created = "2012-02-23 11:20:53" where log_file_name = "20120223-11:20:53-outbiota1.txt";
update upload set created = "2012-03-07 23:47:30" where log_file_name = "20120307-23:47:30-outbiota24.txt";
update upload set created = "2012-03-16 17:21:06" where log_file_name = "20120316-17:21:06-outbiota21.txt";
update upload set created = "2012-03-21 22:56:55" where log_file_name = "20120321-22:56:55-outbiota2.txt";
update upload set created = "2012-04-04 09:25:36" where log_file_name = "20120404-09:25:36-outbiota24.txt";
update upload set created = "2012-01-27 10:13:29" where log_file_name = "20120127-10:13:29-outbiota1.txt";
update upload set created = "2012-02-08 18:52:58" where log_file_name = "20120208-18:52:58-outbiota2.txt";
update upload set created = "2012-02-27 08:53:23" where log_file_name = "20120227-08:53:23-outbiota21.txt";
update upload set created = "2012-03-13 16:06:42" where log_file_name = "20120313-16:06:42-outbiota1.txt";
update upload set created = "2012-03-16 17:46:12" where log_file_name = "20120316-17:46:12-outbiota21.txt";
update upload set created = "2012-03-22 09:37:40" where log_file_name = "20120322-09:37:40-outbiota1.txt";
update upload set created = "2012-04-04 09:55:40" where log_file_name = "20120404-09:55:40-outbiota1.txt";
update upload set created = "2012-02-01 09:19:39" where log_file_name = "20120201-09:19:39-outbiota1.txt";
update upload set created = "2012-02-08 19:32:57" where log_file_name = "20120208-19:32:57-outbiota1.txt";
update upload set created = "2012-03-01 15:30:25" where log_file_name = "20120301-15:30:25-outbiota1.txt";
update upload set created = "2012-03-13 23:41:44" where log_file_name = "20120313-23:41:44-outbiota1.txt";
update upload set created = "2012-03-16 17:47:38" where log_file_name = "20120316-17:47:38-outbiota21.txt";
update upload set created = "2012-03-26 17:44:01" where log_file_name = "20120326-17:44:01-outbiota2.txt";
update upload set created = "2012-02-01 10:48:04" where log_file_name = "20120201-10:48:04-outbiota1.txt";
update upload set created = "2012-02-09 09:36:59" where log_file_name = "20120209-09:36:59-outbiota1.txt";
update upload set created = "2012-03-05 08:55:40" where log_file_name = "20120305-08:55:40-outbiota2.txt";
update upload set created = "2012-03-14 00:42:25" where log_file_name = "20120314-00:42:25-outbiota1.txt";
update upload set created = "2012-03-16 17:48:32" where log_file_name = "20120316-17:48:32-outbiota21.txt";
update upload set created = "2012-03-28 09:03:23" where log_file_name = "20120328-09:03:23-outbiota1.txt";
update upload set created = "2012-02-02 09:46:49" where log_file_name = "20120202-09:46:49-outbiota1.txt";
update upload set created = "2012-02-10 08:54:28" where log_file_name = "20120210-08:54:28-outbiota1.txt";
update upload set created = "2012-03-06 06:25:21" where log_file_name = "20120306-06:25:21-outbiota24.txt";
update upload set created = "2012-03-14 08:54:54" where log_file_name = "20120314-08:54:54-outbiota1.txt";
update upload set created = "2012-03-19 08:52:06" where log_file_name = "20120319-08:52:06-outbiota21.txt";
update upload set created = "2012-03-29 14:28:14" where log_file_name = "20120329-14:28:14-outbiota1.txt";

update upload set group_name = "California Academy of Sciences", login_id = 23 where group_id = 1;
update upload set group_name = "University of Utah", login_id = 2 where group_id = 2;
update upload set group_name = "Field Museum", login_id = 21 where group_id = 21;
update upload set group_name = "Université Rennes", login_id = 27 where group_id = 24;
update upload set group_name = "TestGroup", login_id = 50 where group_id = 25;

update upload set login_id = 22, group_id = 1, group_name = "California Academy of Sciences" where log_file_name like '%fiji%';


insert into upload (log_file_name, created) values ("20120126-13:00:16-allAuthFiles.txt", "2012-01-26 13:00:16");
insert into upload (log_file_name, created) values ("20120220-12:05:06-madants.txt", "2012-02-20 12:05:06");
insert into upload (log_file_name, created) values ("20120131-01:03:26-fossilants.txt", "2012-01-31 01:03:26");
insert into upload (log_file_name, created) values ("20120131-01:03:26-fossilants.txt", "2012-01-31 01:03:26");
insert into upload (log_file_name, created) values ("20120131-01:03:26-fossilants.txt", "2012-01-31 01:03:26");
insert into upload (log_file_name, created) values ("20120131-01:03:26-fossilants.txt", "2012-01-31 01:03:26");
insert into upload (log_file_name, created) values ("20120126-13:33:01-poeantsReload.txt", "2012-01-26 13:33:01");
insert into upload (log_file_name, created) values ("20120220-12:10:12-madants.txt", "2012-02-20 12:10:12");
insert into upload (log_file_name, created) values ("20120307-23:54:06-fossilants.txt", "2012-03-07 23:54:06");
insert into upload (log_file_name, created) values ("20120126-14:31:27-poeantsReload.txt", "2012-01-26 14:31:27");
insert into upload (log_file_name, created) values ("20120126-14:31:27-poeantsReload.txt", "2012-01-26 14:31:27");
insert into upload (log_file_name, created) values ("20120220-12:13:24-madants.txt", "2012-02-20 12:13:24");
insert into upload (log_file_name, created) values ("20120312-22:46:48-introducedants.txt", "2012-03-12 22:46:48");
insert into upload (log_file_name, created) values ("20120126-14:59:07-poeantsReload.txt", "2012-01-26 14:59:07");
insert into upload (log_file_name, created) values ("20120312-22:53:15-introducedants.txt", "2012-03-12 22:53:15");
insert into upload (log_file_name, created) values ("20120319-12:44:20-solomonsants.txt", "2012-03-19 12:44:20");
insert into upload (log_file_name, created) values ("20120223-06:10:33-iranants.txt", "2012-02-23 06:10:33");
insert into upload (log_file_name, created) values ("20120312-23:17:38-introducedants.txt", "2012-03-12 23:17:38");
insert into upload (log_file_name, created) values ("20120402-16:07:27-kenyaants.txt", "2012-04-02 16:07:27");
insert into upload (log_file_name, created) values ("20120312-23:28:04-introducedants.txt", "2012-03-12 23:28:04");
insert into upload (log_file_name, created) values ("20120319-16:24:33-solomonsants.txt", "2012-03-19 16:24:33");
insert into upload (log_file_name, created) values ("20120402-16:10:37-kenyaants.txt", "2012-04-02 16:10:37");
insert into upload (log_file_name, created) values ("20120312-23:32:36-introducedants.txt", "2012-03-12 23:32:36");
insert into upload (log_file_name, created) values ("20120319-17:02:19-solomonsants.txt", "2012-03-19 17:02:19");
insert into upload (log_file_name, created) values ("20120229-15:17:09-madants.txt", "2012-02-29 15:17:09-madants.txt");
insert into upload (log_file_name, created) values ("20120319-17:13:36-solomonsants.txt", "2012-03-19 17:13:36");
insert into upload (log_file_name, created) values ("20120403-06:03:09-fossilants.txt", "2012-04-03 06:03:09");
insert into upload (log_file_name, created) values ("20120319-17:38:50-solomonsants.txt", "2012-03-19 17:38:50");
insert into upload (log_file_name, created) values ("20120127-15:04:59-poeantsReload.txt", "2012-01-27 15:04:59");
insert into upload (log_file_name, created) values ("20120128-11:50:29-matogrossodosulants.txt", "2012-01-28 11:50:29");
insert into upload (log_file_name, created) values ("20120320-13:52:00-madants.txt", "2012-03-20 13:52:00");
insert into upload (log_file_name, created) values ("20120128-11:57:11-matogrossodosulants.txt", "2012-01-28 11:57:11");
insert into upload (log_file_name, created) values ("20120320-14:07:45-malagasyants.txt", "2012-03-20 14:07:45");
insert into upload (log_file_name, created) values ("20120406-05:48:15-RennesSpecimenUpload.txt", "2012-04-06 05:48:15");
insert into upload (log_file_name, created) values ("20120128-12:57:47-madants.txt", "2012-01-28 12:57:47");
insert into upload (log_file_name, created) values ("20120216-09:09:13-poeants.txt", "2012-02-16 09:09:13");
insert into upload (log_file_name, created) values ("20120130-03:24:51-fossilants.txt", "2012-01-30 03:24:51");
insert into upload (log_file_name, created) values ("20120321-14:35:01-fijiants.txt", "2012-03-21 14:35:01");
insert into upload (log_file_name, created) values ("20120406-10:42:17-illinoisants.txt", "2012-04-06 10:42:17");
insert into upload (log_file_name, created) values ("20120130-08:34:19-fossilants.txt", "2012-01-30 08:34:19");
insert into upload (log_file_name, created) values ("20120216-17:31:52-albertaantsReload.txt", "2012-02-16 17:31:52");
insert into upload (log_file_name, created) values ("20120307-14:46:12-kenyaants.txt", "2012-03-07 14:46:12");
insert into upload (log_file_name, created) values ("20120130-09:33:48-fossilants.txt", "2012-01-30 09:33:48");
insert into upload (log_file_name, created) values ("20120307-23:46:25-fossilants.txt", "2012-03-07 23:46:25");
insert into upload (log_file_name, created) values ("20120406-09:43:00-CASSpecimenUpload.txt", "2012-04-06 09:43:00-CASSpecimenUpload.txt");
        
update upload set login_id = 22, group_id = 1, group_name = "California Academy of Sciences", created=created where log_file_name like '%allAuth%';
update upload set login_id = 1, group_id = 1, group_name = "California Academy of Sciences", created=created  where log_file_name like '%madants%';
update upload set login_id = 23, group_id = 1, group_name = "California Academy of Sciences", created=created  where log_file_name like '%CASSpecimen%';
update upload set login_id = 17, group_id = 17, group_name = "Curator Eli Sarnat", created=created  where log_file_name like '%fiji%';
update upload set login_id = 17, group_id = 17, group_name = "Curator Eli Sarnat", created=created  where log_file_name like '%solomon%';
update upload set login_id = 20, group_id = 20, group_name = "Donat Agosti", created=created  where log_file_name like '%iranants%';
update upload set login_id = 17, group_id = 17, group_name = "Curator Eli Sarnat", created=created   where log_file_name like '%poeants%';
update upload set login_id = 27, group_id = 24, group_name = "", created=created  where log_file_name like '%RennesSpecimen%';
update upload set login_id = 27, group_id = 24, group_name = "", created=created  where log_file_name like '%fossil%';
update upload set login_id = 17, group_id = 17, group_name = "Curator Eli Sarnat", created=created  where log_file_name like '%introducedants%';
update upload set login_id = 24, group_id = 1, group_name = "California Academy of Sciences", created=created  where log_file_name like '%kenya%';
update upload set login_id = 51, group_id = 0, group_name = "Default Group", created=created  where log_file_name like '%matogrossodo%';
update upload set login_id = 1, group_id = 1, group_name = "California Academy of Sciences", created=created  where log_file_name like '%malagasyants%';
update upload set login_id = 5, group_id = 5, group_name = "Curator James Trager", created=created  where log_file_name like '%illinoisants%';
update upload set login_id = 8, group_id = 8, group_name = "Curator Joe MacGown", created=created  where log_file_name like '%alberta%';
update upload set login_id = 22, group_id = 1, group_name = "California Academy of Sciences", created=created  where log_file_name like '%Reload%';




  

