#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.0.10/2013-08-01.sql
#


#mysql> select concat(concat(concat(concat('update project set authorbio = \'', authorbio), '\' where project_name = \''), project_name), '\';') from project where authorbio like "%pdf%" or authorbio like "%PDF%";
#+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
#| concat(concat(concat(concat('update project set authorbio = \'', authorbio), '\' where project_name = \''), project_name), '\';')                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
#+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+

update project set authorbio = '<b>Alabama Ant Curator</b><br>
MacGown, J. A. and J. A. Forster. 2005. <a href=http://www.antweb.org/web/speciesList/alabama/APreliminaryListofAnts2005.pdf><b>  A preliminary list of the ants (Hymenoptera:  Formicidae) of Alabama, U.S.A</b></a>.  Entomological News 116: 61-74. <br>
<br>
Layton, B. and J. A. MacGown. 2006. <a href=http://www.antweb.org/web/speciesList/alabama/ControalofArgies.pdf ><b> Control of Argentine Ants and Odorous House Ants in the Home</b></a>. Mississippi State University Extension Service, Publication no. 2407. 7 pp. <br><br>
J.A.MacGown. <a href= http://www.msstate.edu/org/mississippientmuseum/Researchtaxapages/Formicidaehome.html
"" target=""new"">Ants (Formicidae) of the southeastern United States. </a>  WebSite-includes species list, info on species, keys to southeastern ants, etc.
<br>
<br>MacGown, J. A., J.G. Hill, and M. A. Deyrup. 2007. <a href=http://www.antweb.org/web/speciesList/mississippi/MacGown2007_Brachymyrmex.pdf target = ""_blank""><b> Brachymyrmex patagonicus </b></a>(Hymenoptera: Formicidae), an emerging pest species in the southeastern United States. Florida Entomologist 90: 457-464. <br>' where project_name = 'alabamaants';

update project set authorbio = '<b>AZ Ant Curators</b><br>
Johnson, R. A. 1996. Arizona ants. <a href=http://www.antweb.org/web/speciesList/arizona/arizona_wildlife_views_1995.pdf><b>Arizona Wildlife Views</b><a>. June, pp. 2-5.' where project_name = 'arizonaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             

update project set authorbio = '<b>Baja Ant Curator. </b><br>

Download Johnson and Ward\'s 2002 <a href=http://www.antweb.org/web/speciesList/baja/biogeography.pdf>""Biogeography and endemism of ants In Baja California""</a> (Journal of Biogeography 29:1009-1026).' where project_name = 'bajaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           

update project set authorbio = '<b>Belgium Ant Curator</b><br><br>

Recent publications by Dekoninck and colleagues: <br>
<a href="http://www.antweb.org/web/speciesList/belgium/Belgiumants2006.pdf" target="new">Belgian checklist</a>
<a href="http://www.antweb.org/web/speciesList/belgium/Dekoninckrareants.pdf" target="new"> Rare ant species</a><br>
<a href="http://www.antweb.org/web/speciesList/belgium/Dekoninckredlist.pdf" target="new">Red list</a><br>
<a href="http://www.antweb.org/web/speciesList/belgium/Dekoninck_etal_Bull_145_2009_22-24.pdf" target="new">Ants of Rocher</a><br>
<a href="http://www.antweb.org/web/speciesList/belgium/Entomo_144_I_IV_04_Vankerkhoven.pdf" target="new">First record of <i>Myrmica gallienii </i></a><br><br>
See <a href= http://www.formicidae.be target="new">Ants of Belgium database</a> for additional information.' where project_name = 'belgiumants';                                                                                                                                                                                                                                                                                    

update project set authorbio = '<b>British Columbia Ant Curator </b>
 <p>Download Rob\'s 2006 <a href=http://www.antweb.org/web/speciesList/britishcolumbia/Higgins_and_Lindgren_CWD_and_ants.pdf><b> Study on the role of woody debris</b></a  </p>' where project_name = 'britishcolumbiaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     

update project set authorbio = '<b>California Ant Curator</b><br>
Download Phil\'s 2005 <a href=http://www.antweb.org/web/speciesList/california/Ward2005.pdf><b> Synoptic review of the ants of California</b></a> (Zootaxa 936:1-68).' where project_name = 'calants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              

update project set authorbio = '<b>Croatia Ant Curator</b><br>
Download Gregor\'s 2006  <a href=http://www.antweb.org/web/speciesList/croatia/CRO.pdf><b>Review of the ant fauna (Hymenoptera: Formicidae) of Croatia </b></a> (Acta Entomologica Slovenica 14: 131-156)' where project_name = 'croatiaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         

update project set authorbio = '<b>Czech Ant Curators</b>
<br></br>
Their recent publications on Czech ants <br>
are focused on faunistics (<a href=http://www.antweb.org/web/speciesList/czech/Czech_01.pdf><b>1</b></a>, <a href=http://www.antweb.org/web/speciesList/czech/Czech_05.pdf><b>5</b></a>, <a href=http://www.antweb.org/web/speciesList/czech/Czech_06.pdf><b>6</b></a>,  <a href=http://www.antweb.org/web/speciesList/czech/Czech_07.pdf><b>7</b></a>),  <br>
ethology (<a href=http://www.antweb.org/web/speciesList/czech/Czech_02.pdf><b>2</b></a>, <a href=http://www.antweb.org/web/speciesList/czech/Czech_03.pdf><b>3</b></a>), ecology (<a href=http://www.antweb.org/web/speciesList/czech/8.pdf><b>8</b></a>, <a href=http://www.antweb.org/web/speciesList/czech/9.pdf><b>9</b></a>), and conservation biology (<a href=http://www.antweb.org/web/speciesList/czech/Czech_04.pdf><b>4</b></a>).' where project_name = 'czechants';                                                                                                                                                                                                                                                                                                                                                                                                                                                    

update project set authorbio = '<b>Florida Ant Curators</b><br><br>
Deyrup, M., L. Davis, & S. Cover. 2000. <a href=http://www.antweb.org/web/speciesList/florida/ExoticAnts2000.pdf><b>Exotic ants in Florida</b></a>. Trans. American Entomol. Soc. 126: 293-326.<br>
Deyrup M. 2003. <a href=http://www.antweb.org/web/speciesList/florida/UpdatedFLants.pdf><b>An updated list of Florida ants</b></a> (Hymenoptera: Formicidae). Florida Entomologist 86: 43-48.' where project_name = 'floridaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 

update project set authorbio = '<b> France Curators </b>

Download Weulersse & Galkowski\'s 2009 <a href= "http://www.antweb.org/web/speciesList/france/Ants_of_France_Weulersse _Galkowski_2009.pdf" target="new"><b> Up-to-date list of the Ants of France</b></a> Bulletin de la Soci&#233t&#233 Entomologique de France, 114:475-510.' where project_name = 'franceants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     

update project set authorbio = '<b>Hawaii Ant Curator</b><br>
Krushelnycky, P. D., L. L. Loope & N. J. Reimer. 2005. <a href=http://www.antweb.org/web/speciesList/hawaii/Ant_review2005.pdf><b>The Ecology, Policy, and Management of Ants in Hawaii. Proc. Hawaiin Entomol</b></a>.  Soc. 37:1-25.' where project_name = 'hawaiiants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              

update project set authorbio = '<b>India Ant Curator</b><br>
<br>Recent publications by Bharti and colleagues:<br>
<a href="http://www.antweb.org/web/speciesList/india/Bharti_Altitudinal_Diversity.pdf" target="new">Altitudinal diversity</a><br>
<a href="http://www.antweb.org/web/speciesList/india/Bharti_Formicids_at_FRI_Dehradun.pdf" target="new"> Ants at Indian museum Dehradun</a><br>
<a href="http://www.antweb.org/web/speciesList/india/Bharti_Seasonal_patterns.pdf" target="new">Seasonal patterns</a><br>
<a href="http://www.antweb.org/web/speciesList/india/Bharti_diversity_and_abundance.pdf" target="new">Diversity and abundance</a><br>

<br>
See <a href= http://www.antdiversityindia.com target=""new"">Ants of India </a> for additional information.' where project_name = 'indiaants';                                                                                                                                                                                                                                                                                                                                                                        

update project set authorbio = '<b>and Ant Team</b><br>
Fisher, B. L. 2003. <a href=http://www.antweb.org/web/speciesList/madagascar/Fisher2003.pdf><b>Ants (Formicidae: Hymenoptera). </b></a> In The natural history of Madagascar . S. M. Goodman and J. P. Benstead (eds). Pp. 811- 819. University of Chicago Press.' where project_name = 'madants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            

update project set authorbio = '<b>Malagasy Ant Curator</b><br>
Fisher, B. L. 1997. <a href=http://www.antweb.org/web/speciesList/malagasy/BiogeographyandEcology1997.pdf><b> Biogeography and ecology of the ant fauna of Madagascar (Hymenoptera: Formicidae). </b></a>. Journal of Natural History 31:269-302.' where project_name = 'malagasyants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               

update project set authorbio = '<b> Mauritius Ant Curators at work</b><br>
Fisher, B. L. 2005. <a href=http://www.antweb.org/web/speciesList/mauritius/Fisher_56_35_LR.pdf><b>A new species of Discothyrea Roger from Mauritius and a new species of Proceratium from Madagascar (Hymenoptera: Formicidae) </b></a>. Proceedings of the California Academy of Sciences 56:657-667.' where project_name = 'mauritiusants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             

update project set authorbio = '<b>Mississippi Ant Curator</b><br>
MacGown, J. A., R. L. Brown, and J. G. Hill. 2005. <a href=http://www.antweb.org/web/speciesList/mississippi/AnAnnotatedList2005.pdf ><b>  An annotated list of the <i>Pyramica </i> (Hymenoptera:  Formicidae:  Dacetini) of Mississippi</b></a>.  Journal of the Kansas Entomological Society.  78: 285-289 <br>
<br>
Layton, B. and J. A. MacGown. 2006. <a href=http://www.antweb.org/web/speciesList/mississippi/ControalofArgies.pdf ><b> Control of Argentine Ants and Odorous House Ants in the Home</b></a>. Mississippi State University Extension Service, Publication no. 2407. 7 pp. <br>
<br>
J.A.MacGown.  <a href= "http://www.mississippientomologicalmuseum.org.msstate.edu/Researchtaxapages/Formicidaehome.html"
target="new"> Ants (Formicidae of the Southeastern United States </a>.  WebSite-includes species list, info on species, keys to southeastern ants, etc.<br>
<br>
MacGown, J. A., J.G. Hill, and M. A. Deyrup. 2007. <a href=http://www.antweb.org/web/speciesList/mississippi/MacGown2007_Brachymyrmex.pdf target = ""_blank""><b> Brachymyrmex patagonicus </b></a>(Hymenoptera: Formicidae), an emerging pest species in the southeastern United States. Florida Entomologist 90: 457-464.' where project_name = 'mississippiants'; 

update project set authorbio = '<b>Netherlands Ant Curator</b><br>
Download Boer et al. 2003 <a href=http://www.antweb.org/web/speciesList/netherlands/Boer2003.pdf><b> List of ants (Hymenoptera: Formicidae) of Belgium and The Netherlands, their status and Dutch vernacular names</b></a> (Entomologische Berichten 63: 54-58).
<p>Boer & Vierbergen 2008 <a href="http://www.antweb.org/web/speciesList/netherlands/Boer_exotic.pdf"><b> Exotic ants in The Netherlands (Hymenoptera: Formicidae) </b></a>(Entomologische Berichten 68: 121-129).</p>

<p><a href="http://nlmieren.nl/websitepages/soortenlijst.html"><b>Species list of the Netherlands</b></a></p>' where project_name = 'netherlandsants';                                                                                                                                                                                                                                                                                                                                                                                            

update project set authorbio = '<b>New Zealand Ant Curator</b><br><br>
Recent publications by Ward and colleagues: 
<a href="http://www.antweb.org/web/speciesList/newzealand/2005WardWeta.pdf">NZ checklist</a><br>
<a href="http://www.antweb.org/web/speciesList/newzealand/2006WardetalDivDist.pdf">Origin of exotic ants</a><br>
<a href="http://www.antweb.org/web/speciesList/newzealand/2005WardetalSociobio.pdf">Expansion of Argentine Ants</a><br>
<a href="http://www.antweb.org/web/speciesList/newzealand/2005Ward HarrisNZEcol.pdf">Habitat invasibility by Argentine ants</a>' where project_name = 'newzealandants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       

update project set authorbio = '<b>Ohio Ant Curator</b><br>
Download Kal\'s 2008 <a href=http://www.antweb.org/web/speciesList/ohio/p_flavipes_ohio.pdf><b> Paratrechina Flavipes (Smith) (Hymenoptera: Formicidae), a new exotic ant for Ohio</b></a> (Proc. Entomol. Soc. Wash. 110: 439?444).' where project_name = 'ohioants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    

update project set authorbio = '<b> Reunion Ant Curators</b><br>Blard, F., Dorow, W.H.O., Delabie, J.H.C. 2003. <a href=http://www.antweb.org/web/speciesList/reunion/BlardReunion2003.pdf><b> Les fourmis de l\'ile de la Reunion (Hymenoptera: Formicidae). </b></a> Bulletin de la Societe Entomologique de France 108: 127-137.' where project_name = 'reunionants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          

update project set authorbio = '<b>Serbia and Montenegro Ant Curator</b><br>
Download Petrov\'s <a href=http://www.antweb.org/web/speciesList/serbia/Serbia_ants.pdf><b>Ants of Serbia</b></a>' where project_name = 'serbiaants';                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    

update project set authorbio = '<b>Slovenia Ant Curator</b><br>
Download Gregor\'s recent publications:
<a href=http://www.antweb.org/web/speciesList/slovenia/Slovenia_species_list.pdf><b>Slovenia checklist</b></a>
<br>
<a href=http://www.antweb.org/web/speciesList/slovenia/Slovenia.pdf><b>New species for Slovenia </b></a>' where project_name = 'sloveniaants';


update homepage set content = "<a href=/page.do?name=bayarea>Bay Area Ants Survey</a>" where content_type = "mod3headline";
update homepage set content = "<a href=/worldants.jsp>World Ant Collections</a>" where content_type = "mod6headline";
update homepage set content = "
<div class=\"curator_container\">
<div class=\"curator phil\">
<p><b>Phil Ward</b><br />
University of California, Davis
</div>
<div class=\"curator james\">
<p><b>James Trager</b><br />
Missouri Botanical Garden
</div>
<div class=\"curator lloyd\">
<p><b>Lloyd Davis</b><br />
Florida Ant Mafia
</div>
<div class=\"curator jack\">
<p><b>Jack Longino</b><br />
University of Utah
</div>
<div class=\"curator corrie\">
<p><b>Corrie S. Moreau</b><br />
Field Museum
</div>
<div class=\"curator brian\">
<p><b>Brian L. Fisher</b><br />
California Academy of Sciences
</div>
<div class=\"clear\"></div>
</div>
    </div>

    <div id=\"get_involved\">	
        <b><a href=\"/staff.do\">Meet the rest of the team!</a></b><br />
            <p>Many curators already contribute to AntWeb - would you like to join us?  Curators can edit the home page of the geographic section they curate, upload specimen data and authority files, and control a number of other aspects of their project. Learn how to <a href=http://www.antweb.org/documentation/documentation.jsp> submit data to Antweb</a>.

<p>If you would like to join us, contact us at - <a href=\"mailto:antweb@calacademy.org\">antweb@calacademy.org</a>." 
where content_type = "mod5text";

update homepage set content = "<p>AntWeb is the world\'s largest online database of images, 
specimen records, and natural history information on ants. It is community driven and open to contribution from anyone with specimen records, natural history comments, or images. </p>
<p> </p>
<p>Our mission is to publish for the scientific community 
high quality images of all the world&#39;s ant species.  AntWeb 
provides tools for submitting images, specimen records, annotating species 
pages, and managing regional species lists. <a href=\"<%= domainApp %>/about.do\">More...</a></p>"  
where content_type = "mod1text";
