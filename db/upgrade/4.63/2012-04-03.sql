#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.63/2012-04-03.sql
#

alter table groups add column abbrev varchar(20);

update groups set abbrev="Default" where id = 0;
update groups set abbrev="CAS" where id = 1;
update groups set abbrev="Utah" where id = 2;
update groups set abbrev="UCDavis" where id = 16;
update groups set abbrev="BBlaimer" where id = 19;
update groups set abbrev="FM" where id = 21;
update groups set abbrev="Rennes" where id = 24;
update groups set abbrev="TestGroup" where id = 25;


# The queries below were an attempt to resolve a perceived data problem.  Many taxonomicnotes exist
# but are not used on the site.  taxanomicnotes are used.  These efforts were attempted on Apr 4 
# and rolled back on April 5th.


# where there is only on tax*nomicnotes, update it.
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "amblyoponinaeamblyopone armigera";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "amblyoponinaeamblyopone zwaluwenburgi";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "aneuretinaepityomyrmex tornquisti";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "cerapachyinaecerapachys cryptus";                  
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "cerapachyinaecerapachys fuscior";                  
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaegracilidris";                        
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaeleptomyrmula maravignae";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaetapinoma aberrans01";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "ectatomminaecanapone dentata";                     
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotites xiejiaheensis";             
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaedrymomyrmex claripennis";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaeformica fragilis";                       
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaeformica major";                          
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaelasius punctulatus";                     
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaeoecophylla xiejiaheensis";               
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaepaleosminthurus juliae";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaepolyrhachis annosa";                     
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "incertae_sediseoformica pinguis";                  
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "incertae_sediseomyrmex guchengziensis";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaearchimyrmex piatnitzkyi";               
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaearchimyrmex rostratus";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaearchimyrmex smekali";                   
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaeypresiomyrma bartletti";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaeypresiomyrma orbiculata";               
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmeciinaeypresiomyrma rebekkae";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster ensifera";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova";                     
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova-complex_morphotype1"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova-complex_morphotype2"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova-complex_morphotype3"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova-complex_morphotype4"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster hova-complex_morphotype5"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster mahery";                   
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster nosibeensis";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecrematogaster schencki";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaeelectromyrmex klebsi";                   
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaeeomyrmex guchengziensis";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica bremii";                         
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica jurinei";                        
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica macrocephala";                   
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica molassica";                      
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica obsoleta";                       
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaepheidole harrisonfordi";                 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaepyramica electrina";                     
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaepyramica schleeorum";                    
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "ponerinaeponerine_genus2 fr01";                    
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex apache";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex caeciliae";           
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex cubaensis";           
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex ejectus";             
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex elongatus";           
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex gracilis";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex pallidus";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaePseudomyrmex seminole";            
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaepseudomyrmex simplex";             
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera grandidieri";          
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera hespera";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera hirsuta";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera inermis";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera manangotra";           
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera merita";               
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera phragmotica";          
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera variegata";


#templates
delete from description_edit where title = "taxonomicnotes" and taxon_name = 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = 


#updated to set taxanomicnotes to be taxonomicnotes
select taxon_name, title, content from description_edit where taxon_name = "aneuretinaeburmomyrma rossi";              
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "aneuretinaeburmomyrma rossi";   
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus kadi";                
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus kadi";    
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus sadinus";             
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus sadinus"; 
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus schmeltzi kadi";      
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus schmeltzi kadi"; 
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus vitiensis";           
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus vitiensis";  
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaecardiocondyla nuda";             
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaecardiocondyla nuda"; 

#delete taxonomic and update taxanomic
select taxon_name, title, content from description_edit where taxon_name = "dolichoderinaealloiomma changweiensis";    
delete from description_edit where title = "taxonomicnotes" and taxon_name = "dolichoderinaealloiomma changweiensis";    
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaealloiomma changweiensis";    
select taxon_name, title, content from description_edit where taxon_name = "dolichoderinaealloiomma differentialis";   
delete from description_edit where title = "taxonomicnotes" and taxon_name = "dolichoderinaealloiomma differentialis";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaealloiomma differentialis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeelaphrodites mutatus";       
delete from description_edit where title = "taxonomicnotes" and taxon_name = "dolichoderinaeelaphrodites mutatus";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaeelaphrodites mutatus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeelaphrodites scutulatus";    
delete from description_edit where title = "taxonomicnotes" and taxon_name = "dolichoderinaeelaphrodites scutulatus";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaeelaphrodites scutulatus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeeurymyrmex geologicus";     
delete from description_edit where title = "taxonomicnotes" and taxon_name = "dolichoderinaeeurymyrmex geologicus";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "dolichoderinaeeurymyrmex geologicus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus pictus";              
delete from description_edit where title = "taxonomicnotes" and taxon_name = "formicinaecamponotus pictus";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus pictus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeoecophylla xiejiahensis";        
delete from description_edit where title = "taxonomicnotes" and taxon_name = "formicinaeoecophylla xiejiahensis"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaeoecophylla xiejiahensis"; 
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeplagiolepis succini";           
delete from description_edit where title = "taxonomicnotes" and taxon_name = "formicinaeplagiolepis succini"; 
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "formicinaeplagiolepis succini"; 
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedismiosolenopsis fossilis";     
delete from description_edit where title = "taxonomicnotes" and taxon_name = "incertae_sedismiosolenopsis fossilis";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "incertae_sedismiosolenopsis fossilis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica aemula";                 
delete from description_edit where title = "taxonomicnotes" and taxon_name = "myrmicinaemyrmica aemula";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica aemula";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica angusticollis";          
delete from description_edit where title = "taxonomicnotes" and taxon_name = "myrmicinaemyrmica angusticollis";  
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica angusticollis";  
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica archaica";               
delete from description_edit where title = "taxonomicnotes" and taxon_name = "myrmicinaemyrmica archaica";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica archaica";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "ponerinaepachycondyla jpn01";              
delete from description_edit where title = "taxonomicnotes" and taxon_name = "ponerinaepachycondyla jpn01";
update description_edit set title = "taxonomicnotes" where title = "taxanomicnotes" and taxon_name = "ponerinaepachycondyla jpn01";



# Fixed manually
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeelaeomyrmex coloradensis";   
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeelaeomyrmex coloradensis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeelaeomyrmex gracilis";       
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeelaeomyrmex gracilis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeemplastus emeryi";           
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeemplastus emeryi";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeeotapinoma compacta";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeeotapinoma compacta";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeeotapinoma gracilis";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeeotapinoma gracilis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeeotapinoma macalpini";      
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeeotapinoma macalpini";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeiridomyrmex breviantennis";  
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeiridomyrmex breviantennis";

# To do.  For each of the following.  Pull up the page.  From the query, take the undisplay and edit it in.  Then delete the remaineder.
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaepetraeomyrmex minimus";
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaepetraeomyrmex minimus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaepityomyrmex tornquisti";     
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaepityomyrmex tornquisti";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeproiridomyrmex vetulus";     
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeproiridomyrmex vetulus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeprotazteca capitata";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeprotazteca capitata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeprotazteca elongata";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeprotazteca elongata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeprotazteca hendersoni";      
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeprotazteca hendersoni";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaeprotazteca quadrata";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaeprotazteca quadrata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaezherichinius horribilis";    
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaezherichinius horribilis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "dolichoderinaezherichinius rapax";         
delete from description_edit where title = "taxanomicnotes" and taxon_name = "dolichoderinaezherichinius rapax";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "ectatomminaeelectroponera dubia";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "ectatomminaeelectroponera dubia";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaecamponotus fuscipennis";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaecamponotus fuscipennis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeformica gustawi";                
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeformica gustawi";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeformica horrida";                
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeformica horrida";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaelasius pumilus";                 
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaelasius pumilus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeplagiolepis singularis";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeplagiolepis singularis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeplagiolepis squamifera";         
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeplagiolepis squamifera";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeplagiolepis wheeleri";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeplagiolepis wheeleri";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "formicinaeprotrechina carpenteri";         
delete from description_edit where title = "taxanomicnotes" and taxon_name = "formicinaeprotrechina carpenteri";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sediscanapone dentata";           
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sediscanapone dentata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sediseoformica penguis";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sediseoformica penguis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisincertae_sedis miegi";       
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisincertae_sedis miegi";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisleucotaphus cockerelli";     
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisleucotaphus cockerelli";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisleucotaphus gurnetensis";    
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisleucotaphus gurnetensis";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisleucotaphus permancus";      
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisleucotaphus permancus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedispetropone petiolata";        
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedispetropone petiolata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisypresiomyrma bartletti";     
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisypresiomyrma bartletti";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisypresiomyrma orbiculata";    
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisypresiomyrma orbiculata";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "incertae_sedisypresiomyrma rebekkae";      
delete from description_edit where title = "taxanomicnotes" and taxon_name = "incertae_sedisypresiomyrma rebekkae";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaecarebara nitida";                
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaecarebara nitida";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaecarebara ucrainica";             
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaecarebara ucrainica";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaeeulithomyrmex rugosus";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaeeulithomyrmex rugosus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaeeulithomyrmex striatus";         
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaeeulithomyrmex striatus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemessor ak01";                    
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemessor ak01";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemessor sculpturatus";           
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemessor sculpturatus";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica concinna";               
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica concinna";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica nebulosa";               
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica nebulosa";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica tertiaria";              
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica tertiaria";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaemyrmica venusta";                
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaemyrmica venusta";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "myrmicinaestrumigenys electrina";          
delete from description_edit where title = "taxanomicnotes" and taxon_name = "myrmicinaestrumigenys electrina";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "ponerinaeplatythyrea primaeva";            
delete from description_edit where title = "taxanomicnotes" and taxon_name = "ponerinaeplatythyrea primaeva";
select taxon_name, title, content from description_edit where title like '%cnotes' and taxon_name = "pseudomyrmecinaetetraponera oligocenica";
delete from description_edit where title = "taxanomicnotes" and taxon_name = "pseudomyrmecinaetetraponera oligocenica";


