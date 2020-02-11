 
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
  //String apiDomainApp = "http://10.2.22.50/apiV3";
  //String apiDomainApp  = "https://www.antweb.org/apiV3";
  String apiDomainApp  = "http://api.antweb.org/v3";
  //String apiDomainApp  = "https://api.antweb.org/v3";
  if (AntwebProps.isDevMode()) {
    apiDomainApp = "http://localhost:5000";  
  }
%>
    <div class="container">
        <br><br>
        <h1>AntWeb API Version 3</h1>
        <div class="versionx">Latest Version is [<a href='<%= AntwebProps.getDomainApp() %>/api.do'><b><font color="green">here</font></b></a>].</div>
        <div class="versionx">Version 2 is <a href="https://www.antweb.org/api/v2/">here</a></div>

<% if (AntwebProps.isDevMode()) { %>
        <p><b>Dev Note:</b>On dev, the urls will contains localhost:5000 but on the live site, they will seamlessly redirect to the api server. To launch the dev server, go to the antweb/api/v3 directory and invoke: python3.6 api.py</p>
<% } %>

        <p>AntWeb.org is the world's largest online database of images, specimen records, and natural history information on ants. It is community driven and open to contribution from anyone with specimen records, natural history comments, or images.</p> 
        
        <p>This API allows for programatic access to the Antweb database, making the antweb data available to the public in an Json format.</p>
	
        <p><b>NOTE:</b> To prevent encumbering the server. Large requests may not be fulfilled. Using limits and offsets is encouraged.</p>

        <p>There are different ways to use the api, documented below...
           <br>&nbsp;&nbsp;<a href="#specimens">Specimens</a>
           <br>&nbsp;&nbsp;<a href="#geoSpecimens">Specimens (with Geolocation)</a>

           <br>&nbsp;&nbsp;<a href="#taxa">Taxa</a>
           <br>&nbsp;&nbsp;<a href="#distinctTaxa">Distinct Taxa</a>

           <br>&nbsp;&nbsp;<a href="#recentImages">Recent Images</a>
           <br>&nbsp;&nbsp;<a href="#taxaImages">Taxa Images</a>

           <br>&nbsp;&nbsp;<a href="#geolocales">Geolocales</a>
           <br>&nbsp;&nbsp;<a href="#geolocaleTaxa">Geolocale Taxa</a>

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
                <td><b>Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (?code=inb0003695883)</td>
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
                <td><b>Type</b></td>
                <td>(e.g. holotype), this is a wildcard</td>
            </tr>
            <tr>
                <td><b>Georeferenced</b></td>
                <td>This is a boolean argument to filter for only geo referenced specimen (?georeferenced=1)</td>
            </tr>
            <tr>
                <td><b>Bbox</b></td>
                <td>This is a bounded box of decimal coordinates in the format ?bbox=x1,y1,x2,y2</td>
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
                <td><b>Museum</b></td>
                <td>Return only specimens of a specific museum (museum=CASC)</td>
            </tr>
            <tr>
                <td><b>Owned By</b></td>
                <td>Return only specimens of a specific Owner (ownedby=NHMW, Vienna, Austria)</td>
            </tr>
            <tr>
                <td><b>Located At</b></td>
                <td>Return only specimen of a specific location (locatedat=JTLC)</td>
            </tr>
            <tr>
                <td><b>Collected By</b></td>
                <td>Return only specimen collected by (collectedby=J. Longino)</td>
            </tr>
            <tr>
                <td><b>Caste</b></td>
                <td>Return only specimen of specified caste (male, worker or queen)</td>
            </tr>   
            <tr>
                <td><b>Fossil</b></td>
                <td>Return only specimen that are fossils (&fossil=true or &fossil=false)</td>
            </tr>   
            <tr>
                <td><b>Status</b></td>
                <td>Return only specimen that are of taxa that match the status (&status=[valid, morphotaxon, ...])</td>
            </tr>              
            <tr>
                <td><b>Valid Subfamily</b></td>
                <td>Return only specimen that are of taxa that are in valid subfamilies (&validSubfamily=true)</td>
            </tr>              
            <tr>
                <td><b>Valid Genus</b></td>
                <td>Return only specimen that are of taxa that are in valid genera (&validGenus=true)</td>
            </tr>   
            <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
        </table>
            <p><b>Examples:</b>
        <p class="link">A query for all specimen of the genus Tetramorium that were identified in the 1970's:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium"><%= apiDomainApp %>/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium</a></p>

        <p class="link">A query for all queens of the genus Tetramorium:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium"><%= apiDomainApp %>/specimens?caste=queen&genus=Tetramorium</a></p>
            
        <p class="link">A query for all holotypes in the database that have geo data:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?type=holotype&georeferenced=1"><%= apiDomainApp %>/specimens?type=holotype&georeferenced=1</a></p>
                        
		<p class="link">A query for all specimen of genus Acanthognathus with a limit of 100 records returned and an offset of 200 records (meaning records 201 &ndash; 300 were returned):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?genus=acanthognathus&limit=100&offset=200"><%= apiDomainApp %>/specimens?genus=acanthognathus&limit=100&offset=200</a></p>
            
        <p class="link">A query for the specimen with code of casent0922626:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?code=casent0922626"><%= apiDomainApp %>/specimens?code=casent0922626</a></p>
           
        <p class="link">A query for the specimen over 100m elevation, in Australian rainforest, that is a hologype, is georeferenced, and collected in the 1970's:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?minElevation=0&maxElevation=100&country=Australia&habitat=rainforest&type=holotype&georeferenced=1&minDate=1970-01-01&maxDate=1979-12-31:"><%= apiDomainApp %>/specimens?minElevation=0&maxElevation=100&country=Australia&habitat=rainforest&type=holotype&georeferenced=1&minDate=1970-01-01&maxDate=1979-12-31</a></p>            

		<p class="link">A query for the Technomyrmex within the bounding box (w, s, e, n):
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/specimens?bbox=37.77,-122.46,37.76,-122.47&genus=Technomyrmex"><%= apiDomainApp %>/specimens?coords=37.77,-122.46,37.76,-122.47&genus=Technomyrmex</a></p>

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
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
            <tr>
                <td><b>Distinct</b></td>
                <td>You can select a list of distinct species, genus or subfamilies in the area. (?coord=latitude,longitude&r=radius&distinct=rank)</td>
            </tr>
        </table>            
        <p><b>Examples:</b>
        
        <p class="link">Query for all specimen of genus Technomyrmex found within two kilometers of The California Academy of Sciences:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geoSpecimens?coords=37,%20-122&limit=8000&radius=2"><%= apiDomainApp %>/geoSpecimens?coords=37,%20-122&limit=8000&radius=2</a></p>

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
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
        </table>
            <p><b>Examples:</b>
		<p class="link">Query for all genera:
        <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/distinctTaxa?rank=genus&limit=20"><%= apiDomainApp %>/distinctTaxa?rank=genus&limit=200</a></P>

<a name="recentImages"></a><br><hr><br>

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
                <td><b>Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (?code=inb0003695883)</td>
            </tr>
             <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            <p class="link">Query for all iages uploaded in the last 60 days of shotType p, list only 400:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/images?limit=400&since=60&shotType=p"><%= apiDomainApp %>/images?limit=400&since=60&shotType=p</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/images?code=casent0922626"><%= apiDomainApp %>/code=casent0922626</a>
            </p>


<a name="taxa"></a><br><hr><br>

        <h2>Taxa</h2>
            <br>
         	<b>Function:</b> Query for taxa (subfamily, genus, species, subspecies).
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>Taxon Name</b></td>
                <td>Antweb unique identifier for a particular taxon</td>
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
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?taxonName=myrmicinaecrematogaster modiglianii"><%= apiDomainApp %>/taxa?taxonName=myrmicinaecrematogaster modiglianii</a>                  	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?subfamily=myrmicinae&rank=subfamily"><%= apiDomainApp %>/taxa?subfamily=myrmicinae&rank=subfamily</a>   	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?genus=camponotus&rank=genus"><%= apiDomainApp %>/taxa?genus=camponotus&rank=genus</a>
            <p class="link">This Query will return multiple species from different genera:
              <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxa?species=aberrans&rank=species&status=valid"><%= apiDomainApp %>/taxa?aberrans&rank=species&status=valid</a>
            </p>          
            
<a name="taxaImages"></a><br><hr><br>

        <h2>Taxa Images</h2>
            <br>
         	<b>Function:</b> Query for images of specified taxa.
            <p><b>Parameters:</b>
        <table class="arguments">    
            <tr>
                <td><b>Taxon Name</b></td>
                <td>Show all images of taxa using Antweb specific unique identifier for a given taxon</td>
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
                <td><b>Code</b></td>
                <td>The unique identifier of a particular specimen on Antweb (?code=inb0003695883)</td>
            </tr>
            <tr>
                <td><b>UID</b></td>
                <td>Antweb specific unique identifier of a given image.</td>
            </tr>
            <!-- tr>
                <td><b>Upload Date</b></td>
                <td>See images uploaded on a given date.</td>
            </tr -->
            <tr>
                <td><b>Shot Type</b></td>
                <td>Specify which images you would like to see.  H = head shots, D = dorsal shots, P = profile shots and L = labels. (?since=7&shotType=h returns all head shots added in the last 7 days)</td>
            </tr>
            <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of specimen returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?taxonName=myrmicinaecataulacus oberthueri"><%= apiDomainApp %>/taxaImages?taxonName=myrmicinaecataulacus oberthueri</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?subfamily=Myrmicinae&limit=500"><%= apiDomainApp %>/taxaImages?subfamily=Myrmicinae&limit=500</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?genus=Cataulacus"><%= apiDomainApp %>/taxaImages?genus=Cataulacus&limit=50</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?species=oberthueri"><%= apiDomainApp %>/taxaImages?species=oberthueri&limit=50</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?imageId=22777"><%= apiDomainApp %>/taxaImages?imageId=22777</a>
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/taxaImages?code=casent0435930"><%= apiDomainApp %>/taxaImages?code=casent0435930</a>
 
            </p>

<a name="geolocales"></a><br><hr><br>

        <h2>Geolocales</h2>
            <br>
         	<b>Function:</b> Query for Geolocales (Region, subregion, country, adm1).
            <p><b>Parameters:</b>
        <table class="arguments">
            <tr>
                <td><b>ID</b></td>
                <td>Antweb unique identifier for a particular geolocale</td>
            </tr>
            <tr>
                <td><b>Name</b></td>
                <td>Name of the particular geolocale (name="California")</td>
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
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?name=California&parent=United States"><%= apiDomainApp %>/geolocales?name=California&parent=United States</a>                  	
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?id=392"><%= apiDomainApp %>/geolocales?id=392</a>
            </p>                  	

            <p class="link">Query for all Adm1 of a particular country:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?georank=adm1&parent=United States"><%= apiDomainApp %>/geolocales?georank=adm1&parent=United States</a>
            </p>                  	

            <p class="link">Query for all countries in a given subregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocales?parent=Central America&georank=country"><%= apiDomainApp %>/geolocales?parent=Central America&georank=country</a>
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
                <td>Antweb unique identifier for a particular geolocale</td>            
            </tr>
            <tr>
                <td><b>Geolocale Name</b></td>
                <td>Query on the antweb unique name for geolocale</td>
            </tr>
            <tr>
                <td><b>Geolocale Rank</b></td>
                <td>Query on the antweb unique geolocale rank</td>
            </tr>
            <tr>
                <td><b>Region</b></td>
                <td>Query on the region (Africa, Americas, Asia, Europe, Oceania, Antarctica_region)</td>
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
             <tr>
                <td><b>Status</b></td>
                <td>Query on a particular taxon status (valid, morphotaxon, ...)</td>
            </tr>
            <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of geolocale/taxa returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>               
        </table>
            <p><b>Examples:</b>
            <p class="link">All valid species in the country of Comoros:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?rank=species&country=Comoros&status=valid"><%= apiDomainApp %>/geolocaleTaxa?rank=species&country=Comoros&status=valid</a>   
            </p>
            <p class="link">All countries that contain the species: Crematogaster modiglianii:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&genus=crematogaster&species=modiglianii&georank=country"><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&genus=crematogaster&species=modiglianii&georank=country</a>                  	
            </p>
            <p class="link">All countries that have species in the Myrmicinae subfamily:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=country"><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=country</a>   	
            </p>      
            <p class="link">All subregions that have species in the Myrmicinae subfamily:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=subregion "><%= apiDomainApp %>/geolocaleTaxa?subfamily=myrmicinae&rank=subfamily&georank=subregion </a>   	
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
                <td><b>Name</b></td>
                <td>Name of the particular bioregion (Afrotropical, Antarctica, Australasia, Indomalaya, Malagasy, Nearctic, Neotropical, Oceania, Palearctic)</td>
            </tr>
        </table>
            <p><b>Examples:</b>  
            <p class="link">Query for Nearctic:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregions?name=Nearctic"><%= apiDomainApp %>/bioregions?name=Nearctic</a>                  	
            </p>                  	          	
            <p class="link">Query for all bioregions:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregions"><%= apiDomainApp %>/bioregions</a>                  	
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
            <tr>
                <td><b>Limit</b></td>
                <td>Limit the number of bioregion/taxa returned on large requests (?limit=100)</td>
            </tr>
            <tr>
                <td><b>Offset</b></td>
                <td>Used to paginate large requests when paired with the limit argument (?limit=100&offset=300 would return records 301-400)</td>
            </tr>            
        </table>
            <p><b>Examples:</b>
            <p class="link">All valid species (the first 100) in the Nearctic bioregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic&limit=100"><%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic&limit=100</a>   
            </p>                       
            <p class="link">All valid genera (the first 100) in the Nearctic bioregion:
            <br>&nbsp;&nbsp;&nbsp;<a href="<%= apiDomainApp %>/bioregionTaxa?rank=genus&bioregionName=nearctic"><%= apiDomainApp %>/bioregionTaxa?rank=species&bioregionName=nearctic</a>   
            </p>                       
            </p>   

<% if (true || AntwebProps.isDevMode()) { %>
<% } %>


    </div>        
<br><hr><br>

