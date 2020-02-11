#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.1.33/2013-11-25.sql
#

#alter table taxon add column author_name varchar(30);
#alter table taxon add column author_date varchar(30);
alter table taxon add column author_and_date varchar(100);
#alter table taxon add column species_moved tinyint(4);

# To see this for testing, go to: /browse.do?name=mystrium&rank=genus&project=allantwebants and hover cursor over Mystreum leonie
# update taxon set author_and_date = "Mystrium leonie Bihn & Verhaagh, 2007" where taxon_name = "amblyoponinaemystrium leonie";
