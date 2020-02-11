2010-08-03.sql

Create table description_edit select * from description where 1=0;

alter table description_edit add id int(10) NOT NULL auto_increment, add primary key (id);

alter table description_edit add column created timestamp;



    