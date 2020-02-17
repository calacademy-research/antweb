<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<% String domainApp = (new Utility()).getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">	
<tiles:put name="title" value="Arizona species Identification" />	
<tiles:put name="body-content" type="string">
<div id="page_contents">	   
    <h1>Arizona species Identification</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>
<div id="page_data">
    <div id="overview_data">	   	   
    <p>
        <strong>ANTS OF ARIZONA</strong></p>
    <p>
        Species identification literature</p>
    <p>
        Version 14 July 2011</p>
    <p>
        All references taken from AntCat (http://antcat.org/).</p>
    <p>
        <strong><em>Acanthostichus</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        MacKay, W. P. 1996. A revision of the ant genus <em>Acanthostichus</em> (Hymenoptera: Formicidae). Sociobiology 27:129-179. [1996] <a href="http://antbase.org/ants/publications/8559/8559.pdf">PDF</a></p>
    <p>
        <strong><em>Acromyrmex </em></strong></p>
    <p>
        Only one species in Arizona (and North America): <em>A. versicolor</em>.</p>
    <p>
        <strong><em>Acropyga </em></strong></p>
    <p>
        LaPolla, J. S. 2004. <em>Acropyga</em> (Hymenoptera: Formicidae) of the world. Contributions of the American Entomological Institute 33(3):1-130. [2004-07] <a href="http://antbase.org/ants/publications/21151/21151.pdf">PDF</a></p>
    <p>
        <strong><em>Amblyopone </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Ward, P. S. 1988. Mesic elements in the western Nearctic ant fauna: taxonomic and biological notes on <em>Amblyopone</em>, <em>Proceratium</em>, and <em>Smithistruma</em> (Hymenoptera: Formicidae). Journal of the Kansas Entomological Society 61:102-124. [1988-03-04] <a href="http://antbase.org/ants/publications/2954/2954.pdf">PDF</a></p>
    <p>
        <strong><em>Aphaenogaster</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        MacKay, W. P. 1989. A new <em>Aphaenogaster</em> (Hymenoptera: Formicidae) from southern New Mexico. Journal of the New York Entomological Society 97:47-49. [1989-03-29] <a href="http://antbase.org/ants/publications/4403/4403.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Umphrey, G. J. 1996. Morphometric discrimination among sibling species in the <em>fulva</em>-<em>rudis</em>-<em>texana</em> complex of the ant genus <em>Aphaenogaster</em> (Hymenoptera: Formicidae). Canadian Journal of Zoology 74:528-559.</p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Atta </em></strong></p>
    <p>
        Only one species in Arizona: <em>A. texana</em>.</p>
    <p>
        <strong><em>Brachymyrmex</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p>
        <strong><em>Camponotus</em></strong></p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R. 1988. Taxonomic notes on Nearctic species of <em>Camponotus</em>, subgenus <em>Myrmentoma</em> (Hymenoptera: Formicidae). Pp. 55-78 in: Trager, J. C. (ed.) 1988. Advances in myrmecology. Leiden: E. J. Brill, xxvii + 551 pp. [1988]</p>
    <p>
        Snelling, R. R. 2006. Taxonomy of the <em>Camponotus festinatus </em>complex in the United States of America (Hymenoptera: Formicidae). Myrmecologische Nachrichten 8:83-97. [2006-09] <a href="http://antbase.org/ants/publications/21119/21119.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <br />
    <p>
        <strong><em>Cardiocondyla </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Seifert, B. 2003A. The ant genus <em>Cardiocondyla</em> (Insecta: Hymenoptera: Formicidae) - a taxonomic revision of the <em>C. elegans</em>, <em>C. bulgarica</em>, <em>C. batesii</em>, <em>C. nuda</em>, <em>C. shuckardi</em>, <em>C. stambuloffii</em>, <em>C. wroughtonii</em>, <em>C. emeryi</em>, and <em>C. minutior</em> species groups. Annalen des Naturhistorischen Museums in Wien. B, Botanik, Zoologie 104:203-338. [2003-03] <a href="http://antbase.org/ants/publications/21102/21102.pdf">PDF</a></p>
    <p>
        <strong><em>Cephalotes</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        De Andrade, M. L.; Baroni Urbani, C. 1999. Diversity and adaptation in the ant genus <em>Cephalotes</em>, past and present. Stuttgarter Beitr?ge zur Naturkunde. Serie B (Geologie und Pal?ontologie) 271:1-889. [1999-05] <a href="http://antbase.org/ants/publications/8096/8096.pdf">PDF</a></p>
    <p>
        <strong><em>Cerapachys </em></strong></p>
    <p>
        Only one species in Arizona: <em>C. augustae</em>.</p>
    <p>
        <strong><em>Crematogaster</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Buren, W. F. 1968b. A review of the species of <em>Crematogaster</em>, sensu stricto, in North America (Hymenoptera, Formicidae). Part II. Descriptions of new species. Journal of the Georgia Entomological Society 3:91-121. [1968-07] <a href="http://antbase.org/ants/publications/6817/6817.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Longino, J. T. 2003A. The <em>Crematogaster </em>(Hymenoptera, Formicidae, Myrmicinae) of Costa Rica. Zootaxa 151:1-150. [2003-03-05] <a href="http://antbase.org/ants/publications/20256/20256.pdf">PDF</a></p>
    <p>
        <strong><em>?</em></strong></p>
    <p>
        <strong><em>Cyphomyrmex </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Kempf, W. W. 1964d. A revision of the Neotropical fungus-growing ants of the genus <em>Cyphomyrmex</em> Mayr. Part I: Group of <em>strigatus</em> Mayr (Hym., Formicidae). Studia Entomologica 7:1-44. [1964-12-10] <a href="http://antbase.org/ants/publications/4576/4576.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R.; Longino, J. T. 1992. Revisionary notes on the fungus-growing ants of the genus <em>Cyphomyrmex</em>, <em>rimosus</em> group (Hymenoptera: Formicidae: Attini). Pp. 479-494 in: Quintero, D.; Aiello, A. (eds.) 1992. Insects of Panama and Mesoamerica: selected studies. Oxford: Oxford University Press, xxii + 692 pp. [1992]</p>
    <p>
        <strong><em>Dolopomyrmex </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Cover, S. P.; Deyrup, M. 2007. A new ant genus from the southwestern United States. Memoirs of the American Entomological Institute 80:89-99. [2007-09]</p>
    <p>
        <strong><em>Dorymyrmex </em></strong></p>
    <br />
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R. 1995a. Systematics of Nearctic ants of the genus <em>Dorymyrmex</em> (Hymenoptera: Formicidae). Contributions in Science (Los Angeles) 454:1-14. [1995-07-27] <a href="http://antbase.org/ants/publications/8468/8468.pdf">PDF</a></p>
    <p>
        Trager, J. C. 1988a. A revision of <em>Conomyrma</em> (Hymenoptera: Formicidae) from the southeastern United<br />
        States, especially Florida, with keys to the species. Florida Entomologist 71:11-29. [1988-03-18] <a href="http://antbase.org/ants/publications/2903/2903.pdf">PDF</a></p>
    <p>
        Errata in Florida Entomologist 71:219. (1988.06.20)</p>
    <br />
    <p>
        <strong><em>Forelius</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Cuezzo, F. 2000. Revisi?n del g?nero <em>Forelius</em> (Hymenoptera: Formicidae: Dolichoderinae). Sociobiology 35:197-275.</p>
    <p>
        Ward, P. S. 2005. A synoptic review of the ants of California (Hymenoptera: Formicidae). Zootaxa 936:1-68. [2005-04-12] <a href="http://antbase.org/ants/publications/21008/21008.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Formica </em></strong></p>
    <br />
    <p style="margin-left:.5in;text-indent:-.5in">
        Agosti, D.; Bolton, B. 1990b. New characters to differentiate the ant genera <em>Lasius</em> F. and <em>Formica</em> L. (Hymenoptera: Formicidae). Entomologist&#39;s Gazette 41:149-156. [1990-09-30] <a href="http://antbase.org/ants/publications/6855/6855.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Francoeur, A. 1973. R?vision taxonomique des esp?ces n?arctiques du groupe <em>fusca</em>, genre <em>Formica</em> (Formicidae, Hymenoptera). M?moires de la Soci?t? Entomologique du Qu?bec 3:1-316. [1973-09] <a href="http://antbase.org/ants/publications/4955/4955.pdf">PDF</a></p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R.; Buren, W. F. 1985. Description of a new species of slave-making ant in the <em>Formica sanguinea</em> group (Hymenoptera: Formicidae). Great Lakes Entomologist 18:69-78. [1985-06-03] <a href="http://antbase.org/ants/publications/2732/2732.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        &nbsp;</p>
    <p>
        <strong><em>Hypoponera</em></strong></p>
    <p>
        <strong><em>?</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Lasius </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Agosti, D.; Bolton, B. 1990b. New characters to differentiate the ant genera <em>Lasius</em> F. and <em>Formica</em> L. (Hymenoptera: Formicidae). Entomologist&#39;s Gazette 41:149-156. [1990-09-30] <a href="http://antbase.org/ants/publications/6855/6855.pdf">PDF</a></p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Wilson, E. O. 1955a. A monographic revision of the ant genus <em>Lasius</em>. Bulletin of the Museum of Comparative Zoology 113:1-201. [1955-03] <a href="http://antbase.org/ants/publications/3473/3473.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Wing, M. W. 1968a. Taxonomic revision of the Nearctic genus <em>Acanthomyops</em> (Hymenoptera: Formicidae). Memoirs of the Cornell University Agricultural Experiment Station 405:1-173. [1968-03] <a href="http://antbase.org/ants/publications/3514/3514.pdf">PDF</a></p>
    <p>
        <strong><em>Leptothorax </em></strong></p>
    <p>
        <strong><em>Linepithema </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Only one species in Arizona: <em>L. humile.</em></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        &nbsp;</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Wild, A. L. 2004. Taxonomy and distribution of the Argentine ant, <em>Linepithema humile </em>(Hymenoptera: Formicidae). Annals of the Entomological Society of America 97:1204-1215. [2004-11] <a href="http://antbase.org/ants/publications/20351/20351.pdf">PDF</a></p>
    <p>
        <strong><em>Liometopum </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Del Toro, I.; Pacheco, J. A.; MacKay, W. P. 2009. Revision of the ant genus <em>Liometopum </em>(Hymenoptera: Formicidae). Sociobiology 53:299-369. [2009]</p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        &nbsp;</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        <strong><em>&nbsp;</em></strong></p>
    <p>
        <strong><em>Messor </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Johnson, R. A. 2000A. Seed-harvester ants (Hymenoptera: Formicidae) of North America: an overview of ecology and biogeography. Sociobiology 36:89-122 + 83-88.</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Johnson, R. A. 2001. Biogeography and community structure of North American seed-harvesting ants. Annual Review of Entomology 46:1-29.</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Smith, M. R. 1956a. A key to the workers of <em>Veromessor</em> Forel of the United States and the description of a new subspecies (Hymenoptera, Formicidae). Pan-Pacific Entomologist 32:36-38. [1956-03-14] <a href="http://antbase.org/ants/publications/2693/2693.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        &nbsp;</p>
    <p>
        <strong><em>Monomorium </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        DuBois, M. B. 1986. A revision of the native New World species of the ant genus <em>Monomorium</em> (<em>minimum</em> group) (Hymenoptera: Formicidae). University of Kansas Science Bulletin 53:65-119. [1986-03-24] <a href="http://antbase.org/ants/publications/5973/8973.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>&nbsp;</em></strong></p>
    <p>
        <strong><em>Myrmecina </em></strong></p>
    <p>
        Only one named species in Arizona (also one undescribed species): <em>M. americana</em>.</p>
    <p>
        <strong><em>Myrmecocystus </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R. 1976. A revision of the honey ants, genus <em>Myrmecocystus</em> (Hymenoptera: Formicidae). Natural History Museum of Los Angeles County. Science Bulletin 24:1-163. [1976-08-05] <a href="http://antbase.org/ants/publications/2724/2724.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R. 1982b. A revision of the honey ants, genus <em>Myrmecocystus</em>, first supplement (Hymenoptera: Formicidae). Bulletin of the Southern California Academy of Sciences 81:69-86. [1982-10-13] <a href="http://antbase.org/ants/publications/2728/2728.pdf">PDF</a></p>
    <p>
        <strong><em>Myrmica</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Francoeur, A. 2007. The <em>Myrmica punctiventris</em> and <em>M. crassirugis</em> species groups in the Nearctic region. Memoirs of the American Entomological Institute 80:153-185. [2007-09]</p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Neivamyrmex </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Borgmeier, T. 1955. Die Wanderameisen der neotropischen Region. Studia Entomologica 3:1-720. [1955] <a href="http://antbase.org/ants/publications/6500/6500.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, G. C.; Snelling, R. R. 2007. New synonymy, new species, new keys to <em>Neivamyrmex</em> army ants of the United States. Memoirs of the American Entomological Institute 80:459-550. [2007-09]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Watkins, J. F., II 1985. The identification and distribution of the army ants of the United States of America (Hymenoptera, Formicidae, Ecitoninae). Journal of the Kansas Entomological Society 58:479-502. [1985-07-31] <a href="http://antbase.org/ants/publications/11077/11077.pdf">PDF</a></p>
    <p>
        <strong><em>Nylanderia</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Trager, J. C. 1984b. A revision of the genus <em>Paratrechina</em> (Hymenoptera: Formicidae) of the continental United States. Sociobiology 9:49-162. [1984]</p>
    <p>
        <strong><em>Odontomachus</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Deyrup, M.; Cover, S. 2004B. A new species of <em>Odontomachus</em> ant (Hymenoptera: Formicidae) from inland ridges of Florida, with a key to <em>Odontomachus</em> of the United States. Florida Entomologist 87:136-144. [2004-06-08] <a href="http://antbase.org/ants/publications/20428/20428.pdf">PDF</a></p>
    <p>
        <strong><em>? </em></strong></p>
    <p>
        <strong><em>Pheidole </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Gregg, R. E. 1959 (&quot;1958&quot;). Key to the species of <em>Pheidole</em> (Hymenoptera: Formicidae) in the United States. Journal of the New York Entomological Society 66:7-48. [1959-01-20] <a href="http://antbase.org/ants/publications/4928/4928.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Wilson, E. O. 2003A. <em>Pheidole</em> in the New World. A dominant, hyperdiverse ant genus. Cambridge, Mass.: Harvard University Press, [ix] + 794 pp. [2003-03]</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        &nbsp;</p>
    <p>
        <strong><em>Pogonomyrmex </em></strong></p>
    <p>
        Cole, A. C., Jr. 1968. <em>Pogonomyrmex</em> harvester ants. A study of the genus in North America. Knoxville, Tenn.: University of Tennessee Press, x + 222 pp. [1968]</p>
    <p>
        Taber, S. W. 1998. The world of harvester ants. College Station, Texas: Texas A&amp;M University Press, xvii + 213 pp.</p>
    <p>
        Mackay, W.; MacKay, E. 2002. The ants of New Mexico (Hymenoptera: Formicidae). Lewiston, New York: Edwin Mellen Press, 400 pp. [2002-12] <a href="http://antbase.org/ants/publications/21098/21098.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Polyergus</em></strong></p>
    <p>
        Trager, J. C. (in prep).</p>
    <p>
        <strong><em>Prenolepis</em></strong></p>
    <p>
        Only one species in Arizona: <em>P. imparis</em>.</p>
    <p>
        <strong><em>Pseudomyrmex </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Ward, P. S. 1985b. The Nearctic species of the genus <em>Pseudomyrmex</em> (Hymenoptera: Formicidae). Quaestiones Entomologicae 21:209-246. [1985] <a href="http://antbase.org/ants/publications/2952/2952.pdf">PDF</a></p>
    <p>
        <strong><em>Pyramica </em></strong></p>
    <p>
        Bolton, B. 1999. Ant genera of the tribe Dacetonini (Hymenoptera: Formicidae). Journal of Natural History 33:1639-1689. [1999-11] <a href="http://antbase.org/ants/publications/8085/8085.pdf">PDF</a></p>
    <p>
        Bolton, B. 2000. The ant tribe Dacetini. Memoirs of the American Entomological Institute 65:1-1028. [2000-12-28] Includes contributions by S. O. Shattuck (revision of Austral epopostrumiform genera) and B. L. Fisher (revision of Malagasy <em>Strumigenys</em>).</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Ward, P. S. 1988. Mesic elements in the western Nearctic ant fauna: taxonomic and biological notes on <em>Amblyopone</em>, <em>Proceratium</em>, and <em>Smithistruma</em> (Hymenoptera: Formicidae). Journal of the Kansas Entomological Society 61:102-124. [1988-03-04] <a href="http://antbase.org/ants/publications/2954/2954.pdf">PDF</a></p>
    <p>
        <strong><em>Rogeria </em></strong></p>
    <p>
        Kugler, C. 1994. A revision of the ant genus <em>Rogeria</em> with description of the sting apparatus (Hymenoptera: Formicidae). Journal of Hymenoptera Research 3:17-89. [1994-10-15] <a href="http://antbase.org/ants/publications/8859/8859.pdf">PDF</a></p>
    <p>
        <strong><em>Solenopsis </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Thompson, C. R. 1989. The thief ants, <em>Solenopsis molesta</em> group, of Florida (Hymenoptera: Formicidae). Florida Entomologist 72:268-283. [1989-06-30] <a href="http://antbase.org/ants/publications/2870/2870.pdf">PDF</a></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Thompson, C. R.; Johnson, C. 1989. Rediscovered species and revised key to the Florida thief ants (Hymenoptera: Formicidae). Florida Entomologist 72:697-698. [1989-12-22] <a href="http://antbase.org/ants/publications/2871/2871.pdf">PDF</a></p>
    <p>
        Trager, J. C. 1991. A revision of the fire ants, <em>Solenopsis geminata</em> group (Hymenoptera: Formicidae: Myrmicinae). Journal of the New York Entomological Society 99:141-198. [1991-05-29] <a href="http://antbase.org/ants/publications/2904/2904.pdf">PDF</a></p>
    <p>
        <strong><em>Stenamma</em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Smith, M. R. 1957b. Revision of the genus <em>Stenamma</em> Westwood in America north of Mexico (Hymenoptera, Formicidae). American Midland Naturalist 57:133-174. [1957-01] <a href="http://128.146.250.117/pdf">PDF</a>s/2697/2697.pdf&quot;&gt;PDF</p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Snelling, R. R. 1973c. Studies on California ants. 7. The genus <em>Stenamma</em> (Hymenoptera: Formicidae). Contributions in Science (Los Angeles) 245:1-38. [1973-06-28] <a href="http://antbase.org/ants/publications/2721/2721.pdf">PDF</a></p>
    <p>
        <strong><em>?</em></strong></p>
    <p>
        <strong><em>Strumigenys </em></strong></p>
    <p>
        Bolton, B. 2000. The ant tribe Dacetini. Memoirs of the American Entomological Institute 65:1-1028. [2000-12-28] Includes contributions by S. O. Shattuck (revision of Austral epopostrumiform genera) and B. L. Fisher (revision of Malagasy <em>Strumigenys</em>).</p>
    <p>
        <strong><em>Tapinoma </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Creighton, W. S. 1950a. The ants of North America. Bulletin of the Museum of Comparative Zoology 104:1-585. [1950-04] <a href="http://antbase.org/ants/publications/6224/6224.pdf">PDF</a></p>
    <p>
        <strong><em>Temnothorax </em></strong></p>
    <p>
        Mackay, W. P. 2000. A review of the New World ants of the subgenus <em>Myrafant</em>, (genus <em>Leptothorax</em>) (Hymenoptera: Formicidae). Sociobiology 36:265-444. [2000-08] <a href="http://antbase.org/ants/publications/14659/14659.pdf">PDF</a></p>
    <p>
        Ward, P. S. 2005. A synoptic review of the ants of California (Hymenoptera: Formicidae). Zootaxa 936:1-68. [2005-04-12] <a href="http://antbase.org/ants/publications/21008/21008.pdf">PDF</a></p>
    <p>
        Wheeler, G. C.; Wheeler, J. 1986g. The ants of Nevada. Los Angeles: Natural History Museum of Los Angeles County, vii + 138 pp. [1986]</p>
    <p>
        <strong><em>Tetramorium </em></strong></p>
    <p style="margin-left:.5in;text-indent:-.5in">
        Bolton, B. 1979. The ant tribe Tetramoriini (Hymenoptera: Formicidae). The genus <em>Tetramorium</em> Mayr in the Malagasy region and in the New World. Bulletin of the British Museum (Natural History). Entomology 38:129-181. [1979-03-29] <a href="http://antbase.org/ants/publications/6435/6435.pdf">PDF</a></p>
    <p>
        <strong><em>Trachymyrmex </em></strong></p>
    <p>
        Rabeling, C.; Cover, S. P.; Johnson, R. A.; Mueller, U. G. 2007. A review of the North American species of the fungus-gardening ant genus <em>Trachymyrmex</em> (Hymenoptera: Formicidae). Zootaxa 1664:1-53. [2007-12-17]</p>
    <%        
    AncFile ancFile = (AncFile) session.getAttribute("ancFile");	           
    Login accessLogin = LoginMgr.getAccessLogin(request);        
    if (accessLogin != null) {          
        String requestURL = request.getRequestURL().toString();          
        String accessIdStr = "/" + (new Integer(accessLogin.getId())).toString() + "/";	      
        if ((accessLogin.isAdmin()) || (accessLogin.getProjects().contains("arizonaants")) || (requestURL.contains(accessIdStr)) ) {  %>	   
            <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=31" />	   		
                <input type="submit" value="Edit Page">	   	
            </form>	   	 
            <% if (!(session.getAttribute("ancFile") == null)) { %>	   	
                <form method="POST" action="<%= domainApp %>/ancPageSave.do"> 	   		
                    <input type="submit" value="Save Page">	   	
                </form>         
            <% } %>       
        <% } %>	   		 
    <% } %>	   		   	
    </div>	   	
</div>	
</tiles:put>
</tiles:insert>