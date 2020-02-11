#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.2.0/2013-12-20.sql
#

# In Bolton upload file headers (with inconsistencies - underscores)
#  antcat_id
#  Author date
#  author_date_html
#  authors
#  year
#  status
#  available
#  current valid name
#  original combination
#  was original combination


#alter table taxon drop column antcat_id;
#alter table taxon drop column author_date;
#alter table taxon drop column author_date_html;
#alter table taxon drop column authors;
#alter table taxon drop column year;
#alter table taxon drop column status;
#alter table taxon drop column available:
#alter table taxon drop column current_valid_name;
#alter table taxon drop column original_combination;
#alter table taxon drop column was_original_combination;

#alter table taxon add column typed tinyint(4);
#alter table taxon add column antcat_id int(11)
#, add column author_date varchar(100);
#alter table taxon add column author_date_html varchar(300)
#, add column authors varchar(300)
#, add column year varchar(30)
#, add column status varchar(300)
#, add column available int(11)
#, add column current_valid_name varchar(300)
#, add column original_combination int(11)
#, add column was_original_combination int(11);

 alter table taxon add column reference_id int(11)
 , add column bioregion varchar(128)
 , add column country varchar(128)
 , add column current_valid_rank varchar(64)
 , add column current_valid_parent varchar(300); 

alter table taxon modify column antcat_id int(11), modify column author_date varchar(100);
alter table taxon modify column author_date_html varchar(300);
alter table taxon modify column authors varchar(300);
alter table taxon modify column year varchar(30);
alter table taxon modify column status varchar(300);
alter table taxon modify column available tinyint(4);
alter table taxon modify column current_valid_name varchar(300);
alter table taxon modify column original_combination tinyint(4);
alter table taxon modify column was_original_combination varchar(128);

# To see this for testing, go to: /browse.do?name=mystrium&rank=genus&project=allantwebants and see Mystreum leonie
# update taxon set author_date = "Mystrium leonie Bihn & Verhaagh, 2007" where taxon_name = "amblyoponinaemystrium leonie";

alter table specimen modify column type varchar(128);
alter table taxon_country add column created timestamp default CURRENT_TIMESTAMP;

alter table taxon drop column valid_name;
alter table taxon drop column valid;
alter table taxon drop column author_and_date;

# Can we do this?  515 records.
# delete from taxon where source like '%project.txt';
# delete from proj_taxon where taxon_name not in (select distinct taxon_name from taxon);

# We need to delete all taxon, proj_taxon and specimen records.

delete from taxon where taxon_name like '%\'%';
delete from proj_taxon where taxon_name like '%\'%';
update specimen set taxon_name = "formicinaepolyrhachis aurea", species = "aurea" where taxon_name like '%\'%';


update groups g set g.admin_login_id = 36 where g.id = 31;
update groups g set g.admin_login_id = 50 where g.id = 25;
update groups g set g.admin_login_id = 52 where g.id = 26;
update groups g set g.admin_login_id = 69 where g.id = 29;
update groups g set g.admin_login_id = 71 where g.id = 28;
update groups g set g.admin_login_id = 72 where g.id = 30;
update groups g set g.admin_login_id = 73 where g.id = 32;
update groups g set g.admin_login_id = 74 where g.id = 33;
update groups g set g.admin_login_id = 76 where g.id = 34;
update groups g set g.admin_login_id = 78 where g.id = 35;
