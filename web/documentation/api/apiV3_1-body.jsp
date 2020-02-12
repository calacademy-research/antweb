 
<%@ page import="org.calacademy.antweb.*" %> 
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
  //String apiDomainApp  = "http://api.antweb.org/v3.1";
  String apiDomainApp = "https://10.2.22.30:9090/v3.1";
  
  if (AntwebProps.isDevMode()) {
    apiDomainApp = "http://localhost:5000";  
  }
  Login accessLogin = LoginMgr.getAccessLogin(request);
  
  String appendStr = "";
  if (LoginMgr.isAdmin(accessLogin)) appendStr = "&up=1";  
  // A.log("apiV3_1-body.jsp appendStr:" + appendStr);  

%>
    <div class="container">
        <br><br>
        <h1>AntWeb API Version 3.1</h1>
        <hr>
<!--        <img src='<%= AntwebProps.getDomainApp() %>/image/yellow-star-md.png' width=15> New version! Specimen API field names have changed. While in beta the api is potentially subject to change.
        <hr> -->
        <div class="versionx">Version 3.0 is [<a href='<%= AntwebProps.getDomainApp() %>/apiV3.do'>here</a>]</div>
        <div class="versionx">Version 2 is [<a href='https://www.antweb.org/api/v2/'>here</a>]</div>

<%
   if (LoginMgr.isAdmin(accessLogin)) { %>
        <p><font color=red><b>Admin Note:</font></b> Urls must contain &up=1 or the request will return a blank page ! Automatically added if user is logged in as an admin.</p>
<% }
   if (AntwebProps.isDevMode()) { %>
        <p><b>Dev Note:</b>On dev, the urls will contains localhost:5000 but on the live site, they will seamlessly redirect to the api server. To launch the dev server, go to the antweb/api/v3 directory and invoke: python3.6 api.py</p>
<% } %>

        <p>AntWeb.org is the world's largest online database of images, specimen records, and natural history information on ants. It is community driven and open to contribution from anyone with specimen records, natural history comments, or images.</p> 
        
        <p>This API allows for programatic access to the Antweb database, making the antweb data available to the public in an Json format.</p>

        <p><b>Global Parameters (available for all api, where applicable):</b>
        <table class="arguments">
            <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (&limit=100). Default is 10,000.</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
            <tr>
                <td><b>Ndjson</b></td>
                <td>Results will be formatted in ndjson format for use with Elasticsearch (&ndjson=true)</td>
            </tr>
<% if (LoginMgr.isAdmin(accessLogin)) { %>
            <tr>
                <td><b>Log</b></td>
                <td>Admin only - create output in the apache server log at /var/log/httpd/error_log. (&log=true)</td>
            </tr>
            <tr>
                <td><b>Up</b></td>
                <td>Admin only - API is currently down by default. Include the up parameter to access. (&up=1)</td>
            </tr>            
<% } %>
        </table>
        <p><p>
        <p>There are the different ways to access the api, documented below...
           <br>&nbsp;&nbsp;<a href="#specimens">Specimens</a>
           <br>&nbsp;&nbsp;<a href="#geoSpecimens">Specimens (with Geolocation)</a>

           <br>&nbsp;&nbsp;<a href="#taxa">Taxa</a>
           <br>&nbsp;&nbsp;<a href="#distinctTaxa">Distinct Taxa</a>

           <br>&nbsp;&nbsp;<a href="#images">Images</a>
           <br>&nbsp;&nbsp;<a href="#taxaImages">Taxa Images</a>

           <br>&nbsp;&nbsp;<a href="#geolocales">Geolocales</a>
           <br>&nbsp;&nbsp;<a href="#geolocaleTaxa">Geolocale Taxa</a>
<% if (LoginMgr.isAdmin(accessLogin)) { %>
           <br>&nbsp;&nbsp;<a href="#geolocaleSpeciesStats">Geolocale Species Stats</a>
<% } %>
           <br>&nbsp;&nbsp;<a href="#bioregions">Bioregions</a>
           <br>&nbsp;&nbsp;<a href="#bioregionTaxa">Bioregion Taxa</a>
           
<br>
<a name="specimens"></a><br><hr><br>

        <h2>Specimens</h2>
            <br>        
         	<b>Function:</b> Query for a specific supset of specimens.
            <p><b>Parameters:</b>

        <table class="arguments">
            <tr>
                <td><b>family</b></td>
                <td>Return only specimens of a specific family.</td>
            </tr>
            <tr>
                <td><b>Subfamily</b></td>
                <td>Return only specimens of a specific subfamily.</td>
            </tr>
            <tr>
                <td><b>Genus</b></td>
                <td>Return only specimens of a specific genus.</td>
            </tr>
            <tr>
                <td><b>Species</b></td>
                <td>Return only specimens of a specific species.</td>
            </tr>
            <tr>
                <td><b>Specimen Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (ex: specimenCode=inb0003695883)</td>
            </tr>
            <tr>
                <td><b>Country</b></td>
                <td>This uses a wildcard so a query for ?country=land will return any country containing the word land (ex:. England | Ireland | ...)</td>
            </tr>
            <tr>
                <td><b>Habitat</b></td>
                <td>This uses a wildcard so a query for ?habitat=sand will return any habitat containing the word sand (ex: habitat=sandstone)</td>
            </tr>
            <tr>
                <td><b>Type</b></td>
                <td>(e.g. holotype), this is a wildcard</td>
            </tr>
            <tr>
                <td><b>Georeferenced</b></td>
                <td>This is a boolean argument to filter for only geo referenced specimen (ex: georeferenced=true)</td>
            </tr>
            <tr>
                <td><b>Bbox</b></td>
                <td>This is a bounded box of decimal coordinates in the format ?bbox=x1,y1,x2,y2</td>
            </tr>
            <tr>
                <td><b>Min Date</b></td>
                <td>Query for specimen identified on or after a single date (ex: minDate=yyyy-mm-dd)</td>
            </tr>
             <tr>
                <td><b>Max Date</b></td>
                <td>Query for specimen identified on or before a single date (ex: maxDate=yyyy-mm-dd)</td>
            </tr>
            <tr>
                <td><b>Min Elevation</b></td>
                <td>This is measured in meters.  Query on specimen found at or above a specific elevation (ex: minElevation=1200)</td>
            </tr>
            <tr>
                <td><b>Max Elevation</b></td>
                <td>This is measured in meters.  Query on specimen found at or below a specific elevation (ex: maxElevation=1200)</td>
            </tr>
            <tr>
                <td><b>Museum</b></td>
                <td>Return only specimens of a specific museum (ex: museum=CASC)</td>
            </tr>
            <tr>
                <td><b>Owned By</b></td>
                <td>Return only specimens of a specific Owner (ex: ownedby=NHMW, Vienna, Austria)</td>
            </tr>
            <tr>
                <td><b>Located At</b></td>
                <td>Return only specimen of a specific location (ex: locatedat=JTLC)</td>
            </tr>
            <tr>
                <td><b>Collected By</b></td>
                <td>Return only specimen collected by (ex: collectedby=J. Longino)</td>
            </tr>
            <tr>
                <td><b>Life Stage/Sex</b></td>
                <td>Return specimens with life Stage/Sex notes that contain the value (ex: lifeStageSex=ergatogyne)</td>
            </tr>
            <tr>
                <td><b>Caste</b></td>
                <td>Return only specimen of specified caste (ex: caste=[male | worker | queen])</td>
            </tr>   
            <tr>
                <td><b>Subcaste</b></td>
                <td>Return only specimen of specified subcaste (ex: subcaste=[minor | normal])</td>
            </tr>   
            <tr>
                <td><b>Fossil</b></td>
                <td>Return only specimen that are fossils (ex: fossil=[true | false])</td>
            </tr>   
            <tr>
                <td><b>Status</b></td>
                <td>Return only specimen that are of taxa that match the status (ex: status=[valid | morphotaxon | ...])</td>
            </tr>              
            <tr>
                <td><b>Valid Subfamily</b></td>
                <td>Return only specimen that are of taxa that are in valid subfamilies (ex: validSubfamily=true)</td>
            </tr>              
            <tr>
                <td><b>Valid Genus</b></td>
                <td>Return only specimen that are of taxa that are in valid genera (ex: validGenus=true)</td>
            </tr>   
            <tr>
                <td><b>Has Image</b></td>
                <td>Return only specimens that have images (ex: hasImage=true)</td>
            </tr>
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Return only specimens that are on the given geolocale species list (ex: geolocaleName=Madagascar)</td>
            </tr>
        </table>
            <p><b>Examples:</b>
        <p class="link">A query for the specimen with a specien code of casent0922626:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?specimenCode=casent0922626<%= appendStr %>"><%= apiDomainApp %>/specimens?specimenCode=casent0922626</a></p>
           
        <p class="link">A query for all specimen of the genus Tetramorium that were identified in the 1970's:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium<%= appendStr %>"><%= apiDomainApp %>/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium</a></p>

        <p class="link">A query for all queens of the genus Tetramorium:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium<%= appendStr %>"><%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium</a></p>

        <p class="link">A query for all of the soldier ants of Camponotus:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?caste=worker&subcaste=normal&genus=Camponotus<%= appendStr %>"><%= apiDomainApp %>/specimens?caste=worker&subcaste=normal&genus=Camponotus</a></p>
        
        <p class="link">A query for all holotypes in the database that have geo data:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?type=holotype&georeferenced=true<%= appendStr %>"><%= apiDomainApp %>/specimens?type=holotype&georeferenced=true</a></p>
                        
		<p class="link">A query for all specimen of genus Acanthognathus with a limit of 100 records returned and an offset of 200 records (meaning records 201 &ndash; 300 were returned):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?genus=acanthognathus&limit=100&offset=200<%= appendStr %>"><%= apiDomainApp %>/specimens?genus=acanthognathus&limit=100&offset=200</a></p>
            
        <p class="link">A query for the specimen over 100m elevation, in Australian rainforest, that is a hologype, is georeferenced, and collected in the 1970's:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?minElevation=0&maxElevation=100&country=Australia&habitat=rainforest&type=holotype&georeferenced=true&minDate=1970-01-01&maxDate=1979-12-31:<%= appendStr %>"><%= apiDomainApp %>/specimens?minElevation=0&maxElevation=100&country=Australia&habitat=rainforest&type=holotype&georeferenced=true&minDate=1970-01-01&maxDate=1979-12-31</a></p>            

		<p class="link">A query for the Technomyrmex within the bounding box (w, s, e, n):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?bbox=37.77,-122.46,37.76,-122.47&genus=Technomyrmex<%= appendStr %>"><%= apiDomainApp %>/specimens?coords=37.77,-122.46,37.76,-122.47&genus=Technomyrmex</a></p>

        <p class="link">A query for some queens of the genus Tetramorium in ndjson format (for use with Elasticsearch):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium&limit=10&ndjson=true<%= appendStr %>"><%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium&limit=10&ndjson=true</a></p>

        <p class="link">A query that returns the queens of the genus Cataulacus that are of species found in Madagascar:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?genus=Cataulacus&caste=queen&geolocaleName=Madagascar<%= appendStr %>"><%= apiDomainApp %>/specimens?genus=Cataulacus&caste=queen&geolocaleName=Madagascar</a></p>

<% if (LoginMgr.isAdmin(accessLogin)) { %>
        <p class="link"><b>-- Admin Only --</b>
        <p class="link">A query for all non-fossil, valid genera ants in ndjson format (for use with Elasticsearch):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?family=Formicidae&fossil=false&validGenus=true&limit=1000000&ndjson=true<%= appendStr %>"><%= apiDomainApp %>/specimens?family=Formicidae&fossil=false&validGenus=true&limit=1000000&ndjson=true</a></p>
<% } %>
            
<a name="geoSpecimens"></a><br><hr><br>
 
        <h2>Specimens (by Geolocation)</h2>
            <br>
         	<b>Function:</b> Query for specimen near a coordinate point.         	
            <p><b>Parameters:</b>

        <table class="arguments">
            <tr>
                <td><b>Coords</b></td>
                <td>Return a list of specimens by decimal coordinates. (?coords=lat,lon)</td>
            </tr>
            <tr>
                <td><b>Radius</td>
                <td>Define a specific radius in kilometers (if radius is not defined it will default to 5km). (?coord=latitude,longitude&radius=3)</td>
            </tr>
            <tr>
                <td><b>Distinct</b></td>
                <td>You can select a list of distinct species, genus or subfamilies in the area. (?coord=latitude,longitude&r=radius&distinct=rank)</td>
            </tr>
        </table>            
        <p><b>Examples:</b>
        
        <p class="link">Query for all specimen of genus Technomyrmex found within two kilometers of The California Academy of Sciences:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geoSpecimens?coords=37,%20-122&limit=8000&radius=2<%= appendStr %>"><%= apiDomainApp %>/geoSpecimens?coords=37,%20-122&limit=8000&radius=2</a></p>


<a name="taxa"></a><br><hr><br>

        <h2>Taxa</h2>
            <br>
         	<b>Function:</b> Query for taxa (subfamily, genus, species, subspecies).
            <p><b>Parameters:</b>
        <table class="arguments">

            <tr>
                <td><b>Taxon Name</b></td>
                <td>Antweb unique identifier for a particular taxon (&taxonName= )</td>
            </tr>
             <tr>
                <td><b>Rank</b></td>
                <td>Query on a particular rank (&rank=[subfamily, genus, species, subspecies])</td>
            </tr>
            <tr>
                <td><b>Subfamily</b></td>
                <td>Query on a particular subfamily</td>
            </tr>
            <tr>
                <td><b>Genus</b></td>
                <td>Query on a particular genus</td>
            </tr>
             <tr>
                <td><b>Species</b></td>
                <td>Query on a particular species</td>
            </tr>
             <tr>
                <td><b>Subspecies</b></td>
                <td>Query on a particular subspecies</td>
            </tr>
            <!-- tr>
                <td><b>Parent</b></td>
                <td>Query on a parent</td>
            </tr -->
            <tr>
                <td><b>Status</b></td>
                <td>Taxon status (&status=[valid, morphotaxon, ...])</td>
            </tr>            
        </table>
            <p><b>Examples:</b>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?taxonName=myrmicinaecrematogaster modiglianii<%= appendStr %>"><%= apiDomainApp %>/taxa?taxonName=myrmicinaecrematogaster modiglianii</a>                  	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?subfamily=myrmicinae&rank=subfamily<%= appendStr %>"><%= apiDomainApp %>/taxa?subfamily=myrmicinae&rank=subfamily</a>   	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?genus=camponotus&rank=genus"><%= apiDomainApp %>/taxa?genus=camponotus&rank=genus</a>
            <p class="link">This Query will return multiple species from different genera:
              <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?species=aberrans&rank=species&status=valid<%= appendStr %>"><%= apiDomainApp %>/taxa?aberrans&rank=species&status=valid</a>
            </p>          
           

<a name="distinctTaxa"></a><br><hr><br>

        <h2>Distinct Taxa</h2>
            <br>        
         	<b>Function:</b> Query for a list of distinct names for a taxonomic rank.
            <p><b>Parameters:</b>

        <table class="arguments">
            <tr>
                <td><b>Rank</b></td>
                <td>Return a list of unique names for a given rank. (?rank=genus)</td>
            </tr>
            <tr>
                <td><b>Country</b></td>
                <td>This uses a wildcard so a query for ?country=land will return any country containing the word land (e.g. England, Ireland etc)</td>
            </tr>
            <tr>
                <td><b>Habitat</b></td>
                <td>This uses a wildcard so a query for ?habitat=sand will return any habitat containing the word sand (e.g. sandstone)</td>
            </tr>
            <tr>
                <td><b>Min Date</b></td>
                <td>Query for specimen identified on or after a single date (?minDate=yyyy-mm-dd)</td>
            </tr>
             <tr>
                <td><b>Max Date</b></td>
                <td>Query for specimen identified on or before a single date (?maxDate=yyyy-mm-dd)</td>
            </tr>
            <tr>
                <td><b>Min Elevation</b></td>
                <td>This is measured in meters.  Query on specimen found at or above a specific elevation (?minElevation=1200)</td>
            </tr>
            <tr>
                <td><b>Max Elevation</b></td>
                <td>This is measured in meters.  Query on specimen found at or below a specific elevation (?maxElevation=1200)</td>
            </tr>
            <tr>
                <td><b>Status</b></td>
                <td>Taxon status (&status=[valid, morphotaxon, ...])</td>
            </tr>
        </table>
            <p><b>Examples:</b>
		<p class="link">Query for 200 genera in the set of all genera:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/distinctTaxa?rank=genus&limit=20<%= appendStr %>"><%= apiDomainApp %>/distinctTaxa?rank=genus&limit=200</a></P>
		<p class="link">Query for 200 spedies in the set of all valid species:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/distinctTaxa?rank=species&status=valid&limit=20<%= appendStr %>"><%= apiDomainApp %>/distinctTaxa?rank=species&status=valid&limit=20</a></P>


<a name="images"></a><br><hr><br>

        <h2>Images</h2>
            <br>
         	<b>Function:</b> Query for images in the antweb database.
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Since</b></td>
                <td>Return a list of images recently added to antweb, measured in days. (?since=7 returns all images added in the last 7 days)</td>
            </tr>
            <tr>
                <td><b>Shot Type</b></td>
                <td>Specify which images you would like to see.  H = head shots, D = dorsal shots, P = profile shots and L = labels. (?since=7&shotType=h returns all head shots added in the last 7 days)</td>
            </tr>
            <tr>
                <td><b>Specimen Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (?specimenCode=inb0003695883)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            <p class="link">Query for all images uploaded in the last 60 days of shotType p, list only 400:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/images?limit=400&since=60&shotType=p<%= appendStr %>"><%= apiDomainApp %>/images?limit=400&since=60&shotType=p</a>
            </p>
            

<a name="taxaImages"></a><br><hr><br>

        <h2>Taxa Images</h2>
            <br>
         	<b>Function:</b> Query for images of specified taxa.
            <p><b>Parameters:</b>
        <table class="arguments">    
            <tr>
                <td><b>Taxon Name</b></td>
                <td>Show all images of taxa using Antweb specific unique identifier for a given taxon (&taxonName= )</td>
            </tr>
            <tr>
                <td><b>Subfamily</b></td>
                <td>Query for images for a given subfamily.</td>
            </tr>
            <tr>
                <td><b>Genus</b></td>
                <td>Query for images for a given genus.</td>
            </tr>
            <tr>
                <td><b>Species</b></td>
                <td>Query for images for a given species.</td>
            </tr>
            <tr>
                <td><b>Subspecies</b></td>
                <td>Query for images for a given subspecies.</td>
            </tr>
            <tr>
                <td><b>Specimen Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (?specimenCode=inb0003695883)</td>
            </tr>
            <tr>
                <td><b>Image ID</b></td>
                <td>Antweb specific unique identifier of a given image (&imageId= )</td>
            </tr>
            <!-- tr>
                <td><b>Upload Date</b></td>
                <td>See images uploaded on a given date.</td>
            </tr -->
            <tr>
                <td><b>Shot Type</b></td>
                <td>Specify which images you would like to see.  H = head shots, D = dorsal shots, P = profile shots and L = labels. (?since=7&shotType=h returns all head shots added in the last 7 days)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?taxonName=myrmicinaecataulacus oberthueri<%= appendStr %>"><%= apiDomainApp %>/taxaImages?taxonName=myrmicinaecataulacus oberthueri</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?subfamily=Myrmicinae&limit=500<%= appendStr %>"><%= apiDomainApp %>/taxaImages?subfamily=Myrmicinae&limit=500</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?genus=Cataulacus<%= appendStr %>"><%= apiDomainApp %>/taxaImages?genus=Cataulacus&limit=50</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?species=oberthueri<%= appendStr %>"><%= apiDomainApp %>/taxaImages?species=oberthueri&limit=50</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?imageId=22777<%= appendStr %>"><%= apiDomainApp %>/taxaImages?imageId=22777</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?specimenCode=casent0435930<%= appendStr %>"><%= apiDomainApp %>/taxaImages?specimenCode=casent0435930</a>
 
            </p>


<a name="geolocales"></a><br><hr><br>

        <h2>Geolocales</h2>
            <br>
         	<b>Function:</b> Query for Geolocales (Region, subregion, country, adm1).
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Geolocale ID</b></td>
                <td>Antweb unique identifier for a particular geolocale (&geolocaleId= )</td>
            </tr>
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Name of the particular geolocale (geolocaleName="California")</td>
            </tr>
            <tr>
                <td><b>Parent</b></td>
                <td>The name of the geolocale's parent (parent=United States)</td>
            </tr>
             <tr>
                <td><b>Georank</b></td>
                <td>Query according to georank (region, subregion, country, adm1 [state/province], adm2 [county]) (georank=adm1)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            <p class="link">Query for California:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?geolocaleName=California&parent=United States<%= appendStr %>"><%= apiDomainApp %>/geolocales?geolocaleName=California&parent=United States</a>                  	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?geolocaleId=392<%= appendStr %>"><%= apiDomainApp %>/geolocales?geolocaleId=392</a>
            </p>                  	

            <p class="link">Query for all Adm1 of a particular country:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?georank=adm1&parent=United States<%= appendStr %>"><%= apiDomainApp %>/geolocales?georank=adm1&parent=United States</a>
            </p>                  	

            <p class="link">Query for all countries in a given subregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?parent=Central America&georank=country<%= appendStr %>"><%= apiDomainApp %>/geolocales?parent=Central America&georank=country</a>
            </p>                  	
        	

<% if (true || AntwebProps.isDevMode()) { %>

<a name="geolocaleTaxa"></a><br><hr><br>

        <h2>Geolocale Taxa</h2>
            <br>
         	<b>Function:</b> Query for taxa (subfamily, genus, species, subspecies) found in given geolocations (region, subregion, country, adm1, adm2).
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Geolocale ID</b></td>
                <td>Antweb unique identifier for a particular geolocale (geolocaleId= )</td>            
            </tr>
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Query on the antweb unique name for geolocale (geolocaleName="California")</td>
            </tr>
            <tr>
                <td><b>Geolocale Rank</b></td>
                <td>Query on the antweb unique geolocale rank (&georank=[region, subregion, country, adm1])</td>
            </tr>
            <tr>
                <td><b>Region</b></td>
                <td>Query on the region (&region=[Africa, Americas, Asia, Europe, Oceania, Antarctica_region])</td>
            </tr>
            <tr>
                <td><b>Subregion</b></td>
                <td>Query on the subregion</td>
            </tr>            
            <tr>
                <td><b>Country</b></td>
                <td>Query on the country</td>
            </tr>
            <tr>
                <td><b>Adm1</b></td>
                <td>Query on the adm1</td>
            </tr>
            <tr>
                <td><b>Taxon Name</b></td>
                <td>Antweb unique identifier for a particular taxon (&taxonName= )</td>
            </tr>
            <tr>
                <td><b>Rank</b></td>
                <td>Query on the taxon rank (&rank=[subfamily, genus, species [will return species and subspecies], subspecies])</td>
            </tr>         
            <tr>
                <td><b>Subfamily</b></td>
                <td>Query on a particular subfamily</td>
            </tr>
            <tr>
                <td><b>Genus</b></td>
                <td>Query on a particular genus</td>
            </tr>
             <tr>
                <td><b>Species</b></td>
                <td>Query on a particular species</td>
            </tr>
             <tr>
                <td><b>Status</b></td>
                <td>Query on a particular taxon status (valid, morphotaxon, ...)</td>
            </tr>
        </table>
            <p><b>Examples:</b>
            <p class="link">All valid species in the country of Comoros:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?rank=species&country=Comoros&status=valid<%= appendStr %>"><%= apiDomainApp %>/geolocaleTaxa?rank=species&country=Comoros&status=valid</a>   
            </p>
            <p class="link">All countries that contain the species: Crematogaster modiglianii:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&genus=crematogaster&species=modiglianii&georank=country<%= appendStr %>"><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&genus=crematogaster&species=modiglianii&georank=country</a>                  	
            </p>
            <p class="link">All countries that have species in the Myrmicinae subfamily:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=country<%= appendStr %>"><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=country</a>   	
            </p>      
            <p class="link">All subregions that have species in the Myrmicinae subfamily:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=subregion<%= appendStr %>"><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=subregion </a>   	
            </p>              
                       
            </p>   

<% } %>


<% if (LoginMgr.isAdmin(accessLogin)) { %>
 
<a name="geolocaleSpeciesStats"></a><br><hr><br>

        <h2>Geolocale Species Stats</h2>
            <br>
         	<b>Function:</b> Query for the statistics of species (species and subspecies) found in given geolocations (country or adm1).
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Geolocale ID</b></td>
                <td>Antweb unique identifier for a particular geolocale (geolocaleId= )</td>            
            </tr>
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Query on the antweb unique name for geolocale (geolocaleName="California")</td>
            </tr>
        </table>
            <p><b>Examples:</b>
            <p class="link">Counts of all species in the species list of Comoros:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geoSpeciesStats?geolocaleName=Comoros<%= appendStr %>"><%= apiDomainApp %>/geolocaleSpeciesStats?country=Comoros</a>   
            </p>                       
            </p>   

<% } %>


<a name="bioregions"></a><br><hr><br>

        <h2>Bioregions</h2>
            <br>
         	<b>Function:</b> Query for Bioregions.
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Bioregion Name</b></td>
                <td>Name of the particular bioregion (&bioregionName=[Afrotropical, Antarctica, Australasia, Indomalaya, Malagasy, Nearctic, Neotropical, Oceania, Palearctic])</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            <p class="link">Query for Nearctic:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregions?bioregionName=Nearctic<%= appendStr %>"><%= apiDomainApp %>/bioregions?bioregionName=Nearctic</a>                  	
            </p>                  	          	
            <p class="link">Query for all bioregions:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregions?<%= appendStr %>"><%= apiDomainApp %>/bioregions?</a>                  	
            </p>                  	          	


<a name="bioregionTaxa"></a><br><hr><br>

        <h2>Bioregion Taxa</h2>
            <br>
         	<b>Function:</b> Query for taxa (subfamily, genus, species, subspecies) found in given bioregion.
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Bioregion Name</b></td>
                <td>Query on the antweb unique name for bioregion</td>
            </tr>
            <tr>
                <td><b>Taxon Name</b></td>
                <td>Antweb unique identifier for a particular taxon</td>
            </tr>
            <tr>
                <td><b>Rank</b></td>
                <td>Query on the taxon rank (subfamily, genus, species [will return species and subspecies], subspecies)</td>
            </tr>         
            <tr>
                <td><b>Subfamily</b></td>
                <td>Query on a particular subfamily</td>
            </tr>
            <tr>
                <td><b>Genus</b></td>
                <td>Query on a particular genus</td>
            </tr>
             <tr>
                <td><b>Species</b></td>
                <td>Query on a particular species</td>
            </tr>
        </table>
            <p><b>Examples:</b>
            <p class="link">All valid species (the first 100) in the Nearctic bioregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic&limit=100<%= appendStr %>"><%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic&limit=100</a>   
            </p>                       
            <p class="link">All valid genera (the first 100) in the Nearctic bioregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregionTaxa?rank=genus&bioregionName=nearctic<%= appendStr %>"><%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic</a>   
            </p>                       
            </p>   



<a name="unimagedGeolocaleTaxa"></a><br><hr><br>

        <h2>Unimaged Geolocale Taxa</h2>
            <br>
         	<b>Function:</b> Query for taxa (subfamily, genus, species, subspecies) found in given geolocale that are not imaged.
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Query on the antweb unique name for geolocale</td>
            </tr>
            <tr>
                <td><b>Rank</b></td>
                <td>Query on the taxon rank (subfamily, genus, species [will return species and subspecies], subspecies)</td>
            </tr>         
            <tr>
                <td><b>byCaste</b></td>
                <td>Show the castes that are not imaged. (&byCaste=1)</td>
            </tr>
        </table>
            <p><b>Examples:</b>
            <p class="link">Show all unimaged taxa (and which castes are unimaged) for species and subspecies in the California geolocale:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1<%= appendStr %>"><%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1</a>   
            </p>                       
            <p class="link">Show all unimaged taxa (and which castes are unimaged) for subspecies in the California geolocale:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1&rank=subspecies<%= appendStr %>"><%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1&rank=subspecies</a>   
            </p>                       
            <p class="link">Show all unimaged taxa (and which castes are unimaged) for morphotaxa in the California geolocale:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1&status=morphotaxon<%= appendStr %>"><%= apiDomainApp %>/unimagedGeolocaleTaxa?geolocaleName=California&byCaste=1&status=morphotaxon</a>   
            </p>                       
            </p>    



<% if (true || AntwebProps.isDevMode()) { %>
<% } %>


    </div>        
<br><hr><br>

