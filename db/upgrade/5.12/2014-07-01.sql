#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.12/2014-07-01.sql
#


alter table statistics add column action varchar(100);


#select group_concat(created), group_concat(specimen), taxon_name, specimen, project_name from favorite_images group by taxon_name, specimen, project_name having count(*) > 1;

delete from favorite_images where taxon_name = "amblyoponinaeamblyopone aberrans" and specimen = "casent0172185" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone australis" and specimen = "casent0172268" and created = "2012-11-15 01:25:07";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone clarki" and specimen = "casent0172816" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone hackeri" and specimen = "casent0172219" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone leae" and specimen = "casent0172391" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone longidens" and specimen = "casent0172211" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone mercovichi" and specimen = "casent0172207" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "amblyoponinaeamblyopone michaelseni" and specimen = "casent0172208" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "dolichoderinaeaptinoma" and specimen = "casent0130148" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "dolichoderinaeaptinoma antongil" and specimen = "casent0489161" and created = "0000-00-00 00:00:00";
delete from favorite_images where taxon_name = "dolichoderinaeaptinoma mangabe" and specimen = "casent0130148" and created = "0000-00-00 00:00:00";

#select * from favorite_images where taxon_name = "myrmicinaepheidole annemariae";
delete from favorite_images where taxon_name = "myrmicinaepheidole annemariae" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole annemariae", "casent0101688", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole ensifera" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole ensifera", "casent0101780", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole grallatrix" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole grallatrix", "casent0101943", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole lucida" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole lucida", "casent0101624", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole oswaldi" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole oswaldi", "casent0101640", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole sikorae" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole sikorae", "casent0101692", "madants", now(), null, null);
delete from favorite_images where taxon_name = "myrmicinaepheidole sikorae litigiosa" and project_name = "madants";
insert into favorite_images (taxon_name, specimen, project_name, created, access_group, access_login) values ("myrmicinaepheidole sikorae litigiosa", "casent0101628", "madants", now(), null, null);

alter table favorite_images add unique key (taxon_name, specimen, project_name);
