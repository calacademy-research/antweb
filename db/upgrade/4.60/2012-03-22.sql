#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.60/2012-03-22.sql
#

update taxon set source="outbiota1.txt" where source = "/home/antweb/workingdir/outbiota1.txt";
update taxon set source="outbiota21.txt" where source = "/home/antweb/workingdir/outbiota21.txt";
update taxon set source="outbiota2.txt" where source = "/home/antweb/workingdir/outbiota2.txt";
update taxon set source="outbiota24.txt" where source = "/home/antweb/workingdir/outbiota24.txt";
update taxon set source="outbiota25.txt" where source = "/home/antweb/workingdir/outbiota25.txt";

