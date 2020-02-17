<head>
<title>AntWeb Links</title>
<meta charset="utf-8"/>
</head>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<% 
Login accessLogin = LoginMgr.getAccessLogin(request);
String domainApp = (new Utility()).getDomainApp(); 
%>

<title>Links</title>

<div class=left>
<h1>Links</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
</div>

Homonyms
<ul>
<li>Subspecies homonym:<a href="<%= AntwebProps.getDomainApp() %>/description.do?subfamily=formicinae&genus=camponotus&species=brasiliensis&subspecies=antennatus&rank=subspecies&project=allantwebants">Camponotus brasiliensis antennatus</a>
<li>No Homonyms:  <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=camponotus&species=canescens&subspecies=antennatus&rank=subspecies&project=allantwebants">Camponotus canescens antennatus</a>
<li>Homonym of an unrecognized taxon with no authorDate:   <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=camponotus&species=christi&subspecies=ferrugineus&rank=subspecies&authorDate=Emery,%201899">Camponotus (Mayria) christi ferrugineus</a>
<li>Taxon with 3 homonys: <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=camponotus&species=nitida&rank=species&project=allantwebants">Camponotus nitida</a>
<li>Genus with a fossil homonym: <a href="<%= AntwebProps.getDomainApp() %>/description.do?subfamily=amblyoponinae&genus=protamblyopone&rank=genus&project=allantwebants&project=allantwebants">Protamblyopone</a>
<li>Homonym without matching taxon: <a href="<%= AntwebProps.getDomainApp() %>/description.do?subfamily=formicinae&genus=polyrhachis&species=thrinax&subspecies=nigripes&rank=subspecies&project=allantwebants">Polyrhachis thrinax nigripes</a>
</ul>

<% if (!LoginMgr.isDeveloper(accessLogin)) return; %>

Servers
<ul>
<li><a href="http://antweb-prod">www.antweb-prod</a>
<li><a href="http://antweb-stg">antweb-stg</a>
<li><a href="http://antweb-dev">antweb-dev</a>
<li><a href="http://localhost/antweb/">Dev Antweb</a>
</ul>

<br><br>

Taxa
<ul>
<li>Self Ref <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=romblonella&species=vitiensis&rank=species&project=allantwebants">Synonym</a>
<li>Hemiptera <a href="<%= AntwebProps.getDomainApp() %>/description.do?species=conspicua&genus=morganella&rank=species&project=allantwebants">Non-ant</a>
<li>Special chars in taxonomic info <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=pachycondyla&species=bactronica&rank=species&project=worldants">ant</a>
<li>Subspecies <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=pheidole&species=pubiventris&subspecies=foederalis&rank=species">Pheidole pubiventris foederalis</a>
<li>Subspecies <a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=myrmicinaeaphaenogaster swammerdami clara">myrmicinaeaphaenogaster swammerdami clara</a>
<li>Non Ant Species <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=morganella&species=conspicua&rank=species&project=allantwebants">diaspidinaemorganella conspicua</a>
</ul>

Image Pick
<ul>
  <li>Old <a href="http://localhost/antweb/imagePick.do?searchMethod=imagePickerSearch&searchType=equals&project=allantwebants&name=Camponotus%20(Myrmentoma)%20subbarbatus">Link</a></li>
  <li>New <a href="http://localhost/antweb/imagePick.do?searchMethod=imagePickerSearch&searchType=equals&project=allantwebants&subfamily=formicinae&genus=camponotus&species=subbarbatus">Link specified</a></li>
  <li>New <a href="http://localhost/antweb/imagePick.do?searchMethod=imagePickerSearch&searchType=equals&project=allantwebants&taxonName=formicinaecamponotus subbarbatus">Link taxonName</a></li>
</ul>

Plazi
<ul>
  <li>Plazi <a href="<%= AntwebProps.getDomainApp() %>/getPlazi.do?test">Test</a></li>
  <li>Plazi <a href="<%= AntwebProps.getDomainApp() %>/getPlazi.do?fetchStubUpdate">fetchStub update</a></li>

<!--
Error code 500 for: http://plazi.cs.umb.edu/exist/rest/db/taxonx_docs  
javax.servlet.ServletException: Servlet.init() for servlet EXistServlet threw exception
	org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:102)
	org.apache.catalina.valves.AccessLogValve.invoke(AccessLogValve.java:615)
	org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:293)
	org.apache.coyote.http11.Http11Processor.process(Http11Processor.java:859)
	org.apache.coyote.http11.Http11Protocol$Http11ConnectionHandler.process(Http11Protocol.java:602)
	org.apache.tomcat.util.net.JIoEndpoint$Worker.run(JIoEndpoint.java:489)
	java.lang.Thread.run(Thread.java:662)
root cause

java.lang.ArrayIndexOutOfBoundsException: 4035
	org.exist.util.ByteConversion.byteToShort(ByteConversion.java:60)
	org.exist.storage.index.BFile$SinglePage.readOffsets(BFile.java:2585)
	org.exist.storage.index.BFile$SinglePage.<init>(BFile.java:2564)
	org.exist.storage.index.BFile.getSinglePageForRedo(BFile.java:1038)
	org.exist.storage.index.BFile.redoStoreOverflow(BFile.java:1256)
	org.exist.storage.index.OverflowStoreLoggable.redo(OverflowStoreLoggable.java:94)
	org.exist.storage.recovery.RecoveryManager.doRecovery(RecoveryManager.java:197)
	org.exist.storage.recovery.RecoveryManager.recover(RecoveryManager.java:148)
	org.exist.storage.txn.TransactionManager.runRecovery(TransactionManager.java:101)
	org.exist.storage.BrokerPool.initialize(BrokerPool.java:736)
	org.exist.storage.BrokerPool.<init>(BrokerPool.java:610)
	org.exist.storage.BrokerPool.configure(BrokerPool.java:198)
	org.exist.storage.BrokerPool.configure(BrokerPool.java:172)
	org.exist.http.servlets.EXistServlet.startup(EXistServlet.java:628)
	org.exist.http.servlets.EXistServlet.init(EXistServlet.java:113)
	org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:102)
	org.apache.catalina.valves.AccessLogValve.invoke(AccessLogValve.java:615)
	org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:293)
	org.apache.coyote.http11.Http11Processor.process(Http11Processor.java:859)
	org.apache.coyote.http11.Http11Protocol$Http11ConnectionHandler.process(Http11Protocol.java:602)
	org.apache.tomcat.util.net.JIoEndpoint$Worker.run(JIoEndpoint.java:489)
	java.lang.Thread.run(Thread.java:662)
note The full stack trace of the root cause is available in the Apache Tomcat/6.0.35 logs.
-->  
  <li>Plazi <a href="<%= AntwebProps.getDomainApp() %>/getPlazi.do?fetchSubsetUpdate">fetchSubset update</a></li>
  <li>Plazi <a href="<%= AntwebProps.getDomainApp() %>/getPlazi.do?fetch">fetch</a></li>
  <li>Plazi <a href="<%= AntwebProps.getDomainApp() %>/getPlazi.do?update">update</a></li>
</ul>  
          
API
<ul>
<li><a href="">1</a>
</ul>

Static Links
<ul>
<li><a href="<%= AntwebProps.getDomainApp() %>">Home</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/arizona.jsp">Arizona Home Page</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/tapirlink/www/tapir.php">Tapirlink</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/tapirlink/www/tapir.php">Tapirlink</a>
</ul>

Big Data Sets
<ul>
<li>Species with the most specimens (3302): <a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=species&genus=wasmannia&name=auropunctata">wasmannia auropunctata</a>
<li>Subfamily with the most sub-taxons(10445): <a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=subfamily&name=myrmicinae">myrmicinae</a>
<li>Genus with the most species(1584, 323 imaged): <a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=genus&name=camponotus">camponotus</a>
<li>Genus with 2nd most species(1117, 250 imaged): <a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=genus&name=pheidole">pheidole</a>
</ul>

Cross Site Scripting Hacks:
<ul>
<li><a href="<%= AntwebProps.getDomainApp() %>/images.do?%22%3E%3C/form%3E%3C/div%3E%3C/div%3E%3C/div%3E%3C/div%3E%3C/div%3E%3Cbr%3E%3Cbr%3E%3Cdiv%3E%3Cmarquee%3EYOU%3C/marquee%3E%3Cmarquee%3EARE%3C/marquee%3E%3Cmarquee%3EVULNERABLE%3C/marquee%3E%3Cmarquee%3ETO%3C/marquee%3E%3Cmarquee%3ECROSS%3C/marquee%3E%3Cmarquee%3ESITE%3C/marquee%3E%3Cmarquee%3ESCRIPTING%3C/marquee%3E">Marquee</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/images.do?<script>alert(%27TK00000045%27)</script>">script</a>
</ul>

Has Taxon Page Image embedded:
<ul
<li><a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=camponotus&name=manni%20umbratilis&rank=species">umbratilis</a>
</ul>

GoogleEarth links

<ul>Home Page
<li><a href="<%= AntwebProps.getDomainApp() %>/viewGoogleEarth.jsp?title=&where=Arizona&kml_url=<%= AntwebProps.getDomainApp() %>/googleEarth?project=arizonaants">Arizona</a>
</ul>

<ul>SubFamily
<li><a href="<%= AntwebProps.getDomainApp() %>/viewGoogleEarth.jsp?title=Ponerinae&where=Bolton%20World%20Catalog&kml_url=<%= AntwebProps.getDomainApp() %>/googleEarth?google=subfamily=ponerinae---project=worldants">Ponerinae</a> (was hanging).
</ul>

<ul> Species
<li><a href="<%= AntwebProps.getDomainApp() %>/viewGoogleEarth.jsp?title=Tetramorium tosii&where=Bolton World Catalog&kml_url=<%= AntwebProps.getDomainApp() %>/googleEarth?google=subfamily=myrmicinae---genus=tetramorium---species=tosii---project=worldants">Tetramorium tosii</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/viewGoogleEarth.jsp?title=Thaumatomyrmex&where=Bolton World Catalog&kml_url=<%= AntwebProps.getDomainApp() %>/googleEarth?google=subfamily=ponerinae---genus=thaumatomyrmex---project=worldants">Thaumatomyrmex</a>
</ul>

<ul>Cataulacus
<li><a href="<%= AntwebProps.getDomainApp() %>/description.do?name=cataulacus&rank=genus&project=madagascarants">1</a>
<li><a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=genus&name=cataulacus&subfamily=myrmicinae&project=">2</a>
</ul>


Realtime Logs
    <li><a href="<%= AntwebProps.getDomainApp() %>/log/logins.txt">Logins</a>
    <li><a href="<%= AntwebProps.getDomainApp() %>/log/searches.txt">Searches</a>
    <li><a href="<%= AntwebProps.getDomainApp() %>/log/zonageeks.txt">Zonageek</a> errors
</ul>

<ul>Has Images locally:
  <li><a href="http://localhost/antweb/specimen.do?name=casent0039799">casent0039799</a>
</ul>

<hr>
<h3>Locality Link Error</h3>

Summary: Special characters not displaying properly on new server.  It breaks links to the pages because
these values are not retrieved correctly from the database (?) to be translated.

<h2>Works</h2>
<ul>Taxonomic Browser - species pacificum (Works)
<li><a href="http://localhost/antweb/browse.do?subfamily=myrmicinae&genus=tetramorium&name=pacificum&rank=species&project=mauritiusants">Localhost </a>
  <ul>
    <li><a href="http://localhost/antweb/locality.do?name=Mah%E9%20Mont%20Copolia%20520">Mah%E9%20Mont%20Copolia%20520</a> Appears as http://localhost/antweb/locality.do?name=Mah%E9%20Mont%20Copolia%20520
  </ul>
<li><a href="https://10.2.22.112/browse.do?subfamily=myrmicinae&genus=tetramorium&name=pacificum&rank=species&project=mauritiusants">OldAntweb</a>
<li><a href="https://www.antweb.org/browse.do?subfamily=myrmicinae&genus=tetramorium&name=pacificum&rank=species&project=mauritiusants">Antweb</a> Does not work!  Did before.  Now it does again.  Confusion.
</ul>

<h2>Antblog</h2>
<ul>
<li><a href="http://www.antweb.org/cgi-bin/mt/mt.cgi">Admin</a>
</ul>

<h2>Special Characters</h2>
<ul>
<li>Locality (Jack's): <a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=CASENT0606226">Machaquilá</a>
<li>Locality (Academy): <a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=casent0160810">Tetramorium Pacificum</a> (Fixed! by speciment-body.jsp:79 URIUtil.encodePath())
  <ul>
    <li>Working <a href="<%= AntwebProps.getDomainApp() %>/locality.do?name=Mah%E9%20Mont%20Copolia%20520">Locality</a>
  </ul>
<li>Taxonomic History: <a href="<%= AntwebProps.getDomainApp() %>/description.do?rank=genus&name=tyrannomyrmex&project=worldants">Fernández</a>
<li>Taxonomic History: <a href="<%= AntwebProps.getDomainApp() %>/description.do?genus=myrmica&species=martini&rank=species&project=allantwebants">Vésubie</a>
</ul>

From specimen-body.jsp:
<pre>
< jsp :useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" / >
...
< a href="locality.do?name=< %= formatter.clearNull((String) specimen.getLocalityCode()) % >">< %= formatter.appendToNonNull(formatter.clearNull((String) specimen.getCountry()),":") % > < %= formatter.appendToNonNull(formatter.clearNull((String) specimen.getAdm1()),":") % > < %= formatter.appendToNonNull(formatter.clearNull((String) specimen.getAdm2()),";") % > < %= formatter.appendToNonNull(formatter.clearNull((String) specimen.getLocalityName()),";") % > < %= formatter.clearNull((String) desc.get("loclatitude")) % > < %= formatter.clearNull((String) desc.get("loclongitude")) % > < %= formatter.clearNull((String) desc.get("elevation")) % >< /a>
</pre>

<pre>
mysql> select code, taxon_name, subgenus, tribe, speciesgroup, subfamily, genus, species, adm1, adm2, localityname, localitycode from specimen where code = 'casent0160810';
+---------------+---------------------------------+----------+--------------+--------------+------------+-------------+-----------+--------+----------+---------------------------+-----------------------+
| code          | taxon_name                      | subgenus | tribe        | speciesgroup | subfamily  | genus       | species   | adm1   | adm2     | localityname              | localitycode          |
+---------------+---------------------------------+----------+--------------+--------------+------------+-------------+-----------+--------+----------+---------------------------+-----------------------+
| casent0160810 | myrmicinaetetramorium pacificum |          | tetramoriini |              | myrmicinae | tetramorium | pacificum |        |          | Mah? Island, Mont Copolia | Mah? Mont Copolia 520 |
+---------------+---------------------------------+----------+--------------+--------------+------------+-------------+-----------+--------+----------+---------------------------+-----------------------+
</pre>

<hr>

<a href="http://localhost/antweb/descEditSearch.do?searchMethod=descEditSearch&daysAgo=30">Description Edit Search</a>

<hr>
