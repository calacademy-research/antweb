#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.61/2012-03-23.sql
#

alter table favorite_images add column created timestamp default CURRENT_TIMESTAMP;
alter table description_edit add column access_group int(11), add column access_login int(11);
alter table description_hist add column access_login int(11);

alter table favorite_images add column access_group int(11), add column access_login int(11);



update description_edit set content = "Image taken by&nbsp;Fernando Amo<br><a href=\"http://www.antweb.org/web/curator/1/A. cardenai.jpg\"\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/1/A. cardenai.jpg\"></a>" where taxon_name = "myrmicinaeaphaenogaster cardenai" and title = "images";
# http://10.2.22.83/description.do?rank=species&name=cardenai&genus=aphaenogaster&project=eurasianants
update description_edit set content = "Worker from the type series carrying a larvae. Photo by Brian Fisher<br><a href=\"http://www.antweb.org/web/curator/1/IMG_2288.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/1/IMG_2288.jpg\"></a>" where taxon_name = "pseudomyrmecinaetetraponera manangotra" and title = "images"; 
# http://10.2.22.83/description.do?rank=species&name=manangotra&genus=tetraponera&project=madants
update description_edit set content = "Plate_45_<em>Camponotus_kadi</em> (Sarnat &amp; Economo, in press).<a href=\"http://www.antweb.org/web/curator/17/Plate_45_Camponotus_kadi.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_45_Camponotus_kadi.jpg\"></a>" where taxon_name = "formicinaecamponotus schmeltzi kadi" and title = "images"; 
# http://10.2.22.83/description.do?name=schmeltzi%20kadi&genus=camponotus&rank=species&project=pacificislandsants
update description_edit set content = "Plate_30_Camponotus_polynesicus (Sarnat &amp; Economo, in press)</span><a href=\"http://www.antweb.org/web/curator/17/Plate_30_Camponotus_polynesicus.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_30_Camponotus_polynesicus.jpg\"></a>" where taxon_name = "formicinaecamponotus polynesicus" and title = "images"; 
# http://10.2.22.83/description.do?name=polynesicus&genus=camponotus&rank=species&project=fijiants
update description_edit set content = "Plate_47_Camponotus_schmeltzi (Sarnat &amp; Economo, in press)</span><a href=\"http://www.antweb.org/web/curator/17/Plate_47_Camponotus_schmeltzi.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_47_Camponotus_schmeltzi.jpg\"></a>" where taxon_name = "formicinaecamponotus schmeltzi" and title = "images"; 
update description_edit set content = "<br><a href=\"http://www.antweb.org/web/curator/17/Plate_185_Proceratium_FJ01.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_185_Proceratium_FJ01.jpg\"></a>" where taxon_name = "proceratiinaeproceratium fj01" and title = "images"; 
update description_edit set content = "Plate_31_<span style=\"font-style: italic;\">Camponotus_vitiensis </span>(Sarnat &amp; Economo, in press)<a href=\"http://www.antweb.org/web/curator/17/Plate_31_Camponotus_vitiensis.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_31_Camponotus_vitiensis.jpg\"></a>" where taxon_name = "formicinaecamponotus vitiensis" and title = "images"; 
update description_edit set content = "Plate_62<em>_Carebara_atoma. </em>Ecological and geographical distribution in Fiji<em>&nbsp;</em>(Sarnat &amp; Economo, in press).<a href=\"http://www.antweb.org/web/curator/17/Plate_62_Carebara_atoma.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_62_Carebara_atoma.jpg\"></a>" where taxon_name = "myrmicinaecarebara atoma" and title = "images"; 
update description_edit set content = "Plate_3_Cerapachys_cryptus. Ecological and geographical distribution in Fiji (Sarnat &amp; Economo, in press).</span><a href=\"http://www.antweb.org/web/curator/17/Plate_3_Cerapachys_cryptus-copy.jpg\"><img  class=\"taxon_page_img \" src=\"http://www.antweb.org/web/curator/17/Plate_3_Cerapachys_cryptus-copy.jpg\"></a>" where taxon_name = "cerapachyinaecerapachys cryptus" and title = "images"; 

#Funky record (doesnt display title or taxon_name when select *).  This fixes it:
update description_edit set content = "<br><a href=\"http://www.antweb.org/web/curator/17/Plate_37_Camponotus_umbratilis.jpg\"><img class=\"taxon_page_img\" src=\"http://www.antweb.org/web/curator/17/Plate_37_Camponotus_umbratilis.jpg\"></a>" where taxon_name = "formicinaecamponotus manni umbratilis" and title = "images"; 
# But broken still display due to <div id="Cleaner"> tags in the references
update description_edit set content = "Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;<br>Wheeler, W.M. (1934) Some aberrant species of Camponotus (Colobopsis) from the Fiji Islands. Annals of the Entomological Society of America, 27, 415-424." where taxon_name = "formicinaecamponotus manni umbratilis" and title = "references";

# These are to correct the other references description_edits that contained div tags.
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology." where title = "references" and taxon_name = "formicinaecamponotus polynesicus";
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus levuanus";
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus lauensis";
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus laminatus";
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus kadi";
update description_edit set content = "Donisthorpe, H. (1946) Undescribed forms of Camponotus (Colobopsis) vitiensis from the Fiji Islands (Hymenoptera, Formicidae). Proceedings of the Royal Entomological Society of London Series B Taxonomy, 15, 69-70.<br>Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus vitiensis";
update description_edit set content = "Mann, W.M. (1921) The ants of the Fiji Islands. Bulletin of the Museum of Comparative Zoology, 64, 401-499.<br>Sarnat, E.M. &amp; Economo, E.P. (In Press) Ants of Fiji. University of California Publications in Entomology.&nbsp;" where title = "references" and taxon_name = "formicinaecamponotus cristatus";



#Why?
#select * from description_edit where title = "images" and taxon_name = "formicinaecamponotus manni umbratilis";
#returns
#+---------------------------------------+--------+-----------------------------------------------------------------------------------------------------------------+---------+---------------------+----------+-----------------+
#| taxon_name                            | title  | content                                                                                                         | edit_id | created             | taxon_id | is_manual_entry |
#+---------------------------------------+--------+-----------------------------------------------------------------------------------------------------------------+---------+---------------------+----------+-----------------+
#<br><img class="taxon_page_img" src="http://www.antweb.org/web/curator/17/Plate_37_Camponotus_umbratilis.jpg"> |  847059 | 2012-03-22 09:28:04 |     NULL |               1 | 
#+---------------------------------------+--------+-----------------------------------------------------------------------------------------------------------------+---------+---------------------+----------+-----------------+
#1 row in set (0.00 sec)

#Something funky about this record.  If the content comes first in the select list, I can see the taxon_name and title, otherwise not.
#select content, taxon_name, title, edit_id, created, taxon_id, is_manual_entry from description_edit where title = "images" and taxon_name = "formicinaecamponotus manni umbratilis";
#+-----------------------------------------------------------------------------------------------------------------+---------------------------------------+--------+---------+---------------------+----------+-----------------+
#| content                                                                                                         | taxon_name                            | title  | edit_id | created             | taxon_id | is_manual_entry |
#+-----------------------------------------------------------------------------------------------------------------+---------------------------------------+--------+---------+---------------------+----------+-----------------+
#<br><img class="taxon_page_img" src="http://www.antweb.org/web/curator/17/Plate_37_Camponotus_umbratilis.jpg"> | formicinaecamponotus manni umbratilis | images |  847059 | 2012-03-22 09:28:04 |     NULL |               1 | 
#+-----------------------------------------------------------------------------------------------------------------+---------------------------------------+--------+---------+---------------------+----------+-----------------+
#1 row in set (0.00 sec)


#What is up with this?
#http://10.2.22.83/description.do?genus=camponotus&name=manni%20umbratilis&rank=species

#To test:
#update description_edit set content = "" where taxon_name = "formicinaecamponotus manni umbratilis" and title = "images"; 

#This fixes:
#delete from description_edit where taxon_name = "formicinaecamponotus manni umbratilis" and title = "references";


