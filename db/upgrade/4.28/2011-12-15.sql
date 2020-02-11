#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.28/2011-12-15.sql
#



mysql> select project_name, locality from project where locality like '%province%';;
+---------------------+-----------------------------+
| project_name        | locality                    |
+---------------------+-----------------------------+
| calants             | province='california'       | 
| worldants           | province is not null        | 
| arizonaants         | province='arizona'          | 
| louisianaants       | province='louisiana'        | 
| floridaants         | province='florida'          | 
| galapagosants       | province='galapagos'        | 
| missouriants        | province='missouri'         | 
| illinoisants        | province='illinois'         | 
| hawaiiants          | province='hawaii'           | 
| alabamaants         | province='alabama'          | 
| mississippiants     | province='mississipi'       | 
| ohioants            | province='ohio'             | 
| albertaants         | province='alberta'          | 
| utahants            | province='Utah'             | 
| coloradoants        | province='Colorado'         | 
| texasants           | province='Texas'            | 
| texasants   ?       | province='Borneo'           | 
| britishcolumbiaants | province='British Columbia' | 
| floridakeysants     | province='florida'          | 
| queenslandants      | province='queensland'       | 
+---------------------+-----------------------------+
20 rows in set (0.00 sec)


update project set locality='adm1=\'California\'' where project_name = "calants";
update project set locality='adm1 is not null' where project_name = "worldants";
update project set locality='adm1=\'Arizona\'' where project_name = "arizonaants";
update project set locality='adm1=\'Louisiana\'' where project_name = "louisianaants";
update project set locality='adm1=\'Florida\'' where project_name = "floridaants";
update project set locality='adm1=\'Galapagos\'' where project_name = "galapagosants";
update project set locality='adm1=\'Missouri\'' where project_name = "missouriants";
update project set locality='adm1=\'Illinois\'' where project_name = "illinoisants";
update project set locality='adm1=\'Hawaii\'' where project_name = "hawaiiants";
update project set locality='adm1=\'Alabama\'' where project_name = "alabamaants";
update project set locality='adm1=\'Mississipi\'' where project_name = "mississippiants";
update project set locality='adm1=\'Ohio\'' where project_name = "ohioants";
update project set locality='adm1=\'Alberta\'' where project_name = "albertaants";
update project set locality='adm1=\'Utah\'' where project_name = "utahants";
update project set locality='adm1=\'Colorado\'' where project_name = "coloradoants";
update project set locality='adm1=\'Texas\'' where project_name = "texasants";
update project set locality='adm1=\'Borneo\'' where project_name = "borneoants";
update project set locality='adm1=\'British Columbia\'' where project_name = "britishcolumbiaants";
update project set locality='adm1=\'Florida\'' where project_name = "floridakeysants";
update project set locality='adm1=\'Queensland\'' where project_name = "queenslandants";



alter table statistics add column total_images int(11);
