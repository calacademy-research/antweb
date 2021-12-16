
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
  //Taxon taxon = (Taxon) session.getAttribute("taxon");
  Taxon taxon = (Taxon) request.getAttribute("taxon");
  //A.log("taaxonPage-body.jsp taxon:" + taxon);
  if (taxon == null) return;

  
  Specimen specimen = null; //(Specimen) session.getAttribute("specimen");

  String thePage = HttpUtil.getTarget(request);
  
  Overview overview = OverviewMgr.getOverview(request);  

  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
     
  Hashtable desc = taxon.getDescription();   
   
  String guiDefaultContent = AntwebProps.guiDefaultContent; 
    
  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);
  
  //String thisPageTarget = java.net.URLEncoder.encode("description.do?" + taxon.getBrowserParams());  
  String thisPageTarget = "description.do?" + taxon.getBrowserParams();
  String editField = request.getParameter("editField");
  if (editField == null) editField = "none";

  // This will be used in the included taxonEditField.jsp.
  Login accessLogin= LoginMgr.getAccessLogin(request);
  //Group accessGroup = GroupMgr.getAccessGroup(request);
  
  if (accessLogin != null) { %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/ckeditor/ckeditor.js"></script>
<%
  } %>

<!-- taxonPage-body.jsp -->

<%
//A.log("taxonPage-body.jsp genus:" + TaxonMgr.getGenus("myrmicinaemyrmica"));
//A.log("taxonPage-body.jsp subspecies:" + TaxonMgr.getTaxon("myrmica scabrinodis ahngeri"));
//Taxon t = TaxonMgr.getTaxon("myrmicinaemyrmica scabrinodis ahngeri");
//A.log("taxonPage-body.jsp taxonName:" + t.getTaxonName() + " name:" + t.getName() + " species:" + t.getSpecies() + " source:" + t.getSource());
//A.log("taxonPage-body.jsp taxon.source:" + taxon.getSource());

  String dagger = "";
  if (taxon.getIsFossil()) dagger = "&dagger;";

/* Here we output the Major heading of this Taxon  */

  org.calacademy.antweb.Map map = taxon.getMap();
  
  // if (AntwebProps.isDevMode()) AntwebUtil.log("taxonPage-body.jsp map:" + map);

  String object = "taxonName";
  String objectName = taxon.getTaxonName();
  String mapType = "taxon";
%>

    <input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
    <input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
    <div id="page_contents">
    <%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li>Overview</li>
                <li><a href="browse.do?<%= taxon.getBrowserParams() %>"><%= Rank.getNextPluralRank(taxon.getRank()) %></a></li>
                <li><a href="images.do?<%= taxon.getBrowserParams() %>">Images</a></li>
				<% 

                  //A.log("taxonPage-body.jsp map:" + taxon.getMap() + " hasMap:" + taxon.hasMap() + " map:" + map);
	
				  if (taxon.hasMap() && map != null) { %>
                  <li><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?taxonName=<%= taxon.getTaxonName() %>&<%= overview.getParams() %>">Map</a></li>
				<% } %>
            </ul>
        </div>
 
        <%@ include file="/common/viewInAntCat.jsp" %>
        <div class="clear"></div>
    </div>
    <!-- 
        taxonName:<%= taxon.getTaxonName() %> 
        details:<%= taxon.getDetails() %>        
        simpleName:<%= taxon.getSimpleName() %>
        prettyName:<%= taxon.getPrettyName() %>
        fullName:<%= taxon.getFullName() %>
        name:<%= taxon.getName() %>
        execTime:<%= taxon.getExecTime() %>
    -->
<%@ include file="/common/taxonomicHierarchy.jsp" %>

<%
  String projectStr = "";
%>

    <div id="totals_and_tools_container">
        <div id="totals_and_tools">
            <%@ include file="/common/pageToolLinks.jsp" %>
            <%@ include file="/common/data_download_overlay.jsp" %>
        </div>
    </div>
<!-- /div -->

<%@ include file="/common/flagInclude.jsp" %>

<div id="page_data">
    <div id="overview_data">

<!-- Editable by identified users: Distribution, Identification, Biology,

Yes: Taxonomic Notes
     , References, TextAuthor, RevDate, 

      Similarspecies?

* Add comments.
* Check database for taxonomicnotes.  Is it just notes?  Notes look like taxonomic notes, are they same?
   Do they overlap?  Can we make them all taxonomic notes?  Research.
* Move author and revDate down, and make editable

Has references:      
      /description.do?rank='species&name=oregonensis&genus=amblyopone&project=californiaants'
      
To do:
   Properly percolate database layer exceptions, or no records modified, back to client.
   Logout should pass target also
   Investigate why the html will not save.  That seems significant.  Could we encode?
   Integrate notes with taxonomicnotes
   Add in fossil feature.
-->

<%  String currentValidNameLink = "";

  if (desc != null) { 
    String thisDesc = null;
    String descHeader = null; 
    String descNotes = null;
      
    boolean hasExtraPrivs = false;
    if (LoginMgr.isAdmin(accessLogin)) hasExtraPrivs = true;
    if (LoginMgr.isJack(accessLogin)) hasExtraPrivs = true;
        // We give extra privs to Jack - he can modify notes

    if (taxon.getCurrentValidName() != null && !taxon.getCurrentValidName().equals(taxon.getTaxonName())) {
      currentValidNameLink = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getCurrentValidTaxonName() + "'>" + Taxon.displayTaxonName(taxon.getCurrentValidName()) + "</a>";
      %> 
      <br><h2>Current Valid Name:</h2>  <%= currentValidNameLink %><br> 
 <% }      

    String alsoDatabased = taxon.getAlsoDatabased();
    if (alsoDatabased != null) { %>
        <h2>Also Databased as Synonym: <%= alsoDatabased %>&nbsp;<img src="<%= AntwebProps.getDomainApp() %>/image/valid_name.png" border="0" title="Valid Name"> </h2> <p>
 <% } %>

 <% Vector<String> homonymAuthorDates = taxon.getHomonymAuthorDates();
    if (homonymAuthorDates != null && !homonymAuthorDates.isEmpty()) { %>
        <h3>Homonyms:</h3>
   <% int i = 0;
      for (String authorDate : homonymAuthorDates) { 
        ++i;
        if (i > 1) out.println(", ");
      %>        
          <a href="<%= taxon.getUrl() %>&authorDate=<%= authorDate %>"><%= authorDate %></a>
   <% } %>
       <br>
 <% } %>
   
 <% String seeAlso = taxon.getSeeAlso();
    if (seeAlso != null) { %>
      <h3>See Also:</h3> <%= seeAlso %><br>
 <% }

    if (LoginMgr.isAdmin(request)) {
      String commonNames = CommonNames.getCommonNames(taxon.getTaxonName());
      //A.log("Common names:" + commonNames);
      if (commonNames != null) out.println("<br><b>Common Names:</b> " + commonNames + " <br>");
    }
    
    if (desc.containsKey("taxonomichistory")) { %>
      <br><h2>Taxonomic History (provided by Barry Bolton, <%= year %>)</h2>
                  <% String words = new Formatter().removeTag(new Formatter().replaceAttribute("style", (String) desc.get("taxonomichistory"),""), "br"); %>
                    <%= new Formatter().removeTag((String) words, "o:p") %>
 <% }
 
    thisDesc = "images";       
    descHeader = "Taxon Page Images"; %>
 <%@ include file="common/descEdit/taxonImageEditField.jsp" %>

 <% thisDesc = "videos";
    descHeader = "Taxon Page Video"; %>
 <%@ include file="common/descEdit/taxonVideoEditField.jsp" %>

<%
   // This is a one-off.  Should get a system.  Perhaps generify Taxon Page Images to support Youtube, Vimeo.
    if (false &&
      "myrmicinaesolenopsis invicta".equals(taxon.getTaxonName())) { %>
<p><h3>Contributed Video</h3>
<iframe src="https://player.vimeo.com/video/125974638" width="500" height="281" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe> <p><a href="https://vimeo.com/125974638">Entomology Animated Episode 1: RIFA Madness!</a> from <a href="https://vimeo.com/bloopatone">Eric Keller</a> on <a href="https://vimeo.com">Vimeo</a>.</p>
 <% } %>

 <% thisDesc = "overview"; 
    descHeader = "Overview"; 
    descNotes = "A brief, global overview of select aspects of taxon biology (distribution, biology, identification). Intended to be brief -- not more than 400 or 500 words -- and present highlights of the taxon to engage the reader.";   %>
   <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>

// Distribution
   <%@ include file="distribution.jsp" %>


 <% thisDesc = "distribution"; 
    descHeader = "Distribution Notes"; 
    descNotes = "A description of the geographic distribution, or range, of the taxon. Be explicit about whether you are referring to global, regional, or political aspects and whether the taxon is native or introduced in portions of the range.";   %>
   <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %> 

 <% thisDesc = "biology";       
    descHeader = "Biology"; 
    descNotes = "A comprehensive description of the characteristics of the taxon (including all aspects of biology, not just physical descriptions). Used primarily when many of the subject categories are treated together in one object, but at length.";   %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>

 <% thisDesc = "identification";  
    descHeader = "Identification"; 
    descNotes = "A description of the features that distinguish this taxon from close relatives or other similar species. May include, but is not restricted to, synapomorphies.";   %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>

 <% thisDesc = "comments";       
    descHeader = "Comments"; %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>
 
 <% thisDesc = "taxanomicnotes";       
    descHeader = "Taxonomic Notes"; 
    descNotes = "Information about taxonomic history, nomenclatural issues, type specimens, etc. that is not already referred to in the &#39;Taxonomic History&#39; section.";   %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>

 <!-- to be removed after Eli transfers to comments or Taxonomic Notes -->
 <% thisDesc = "notes";       
    descHeader = "Notes"; 
    descNotes = "A category intended as a place for content that is difficult to fit into available subject headings or that contains content intended for a wide variety of subject headings.";   %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>
  
 <% thisDesc = "references";       
    descHeader = "References"; %>
 <%@ include file="common/descEdit/taxonEditFieldCK.jsp" %>

 <!-- insert author history here -->
 <% ArrayList<String> hist = (ArrayList) request.getAttribute("descEditHist");
    if (!(hist == null)) {
      if (!hist.isEmpty()) { %>
        <p><h3>Taxon Page Author History</h3>  
      <%
        int eventCount = 0;
        int eventDisplayCount = 3;
        String toolTip = "";
        for (String event : hist) {
          ++eventCount;
          if (eventCount > eventDisplayCount) { 
            int eventDisplayCounter = 0;
            for (String tipEvent : hist) {
              ++eventDisplayCounter;
              if (eventDisplayCounter > eventDisplayCount)   
                toolTip += "\r\n" + tipEvent;
            }
            break;
          }
          out.println(event + "<br>");
        }
        if (!"".equals(toolTip)) {
          %>  <a title="<%= toolTip %>">...</a><br> <%          
        }
      }
    } // end desc edit history

    if (AntwebProps.isDevMode() || false && LoginMgr.isAdmin(accessLogin)) {
      // No longer displayed.
      if (desc.containsKey("taxonomictreatment")) { %>
        <p><h3>(Admin only) Taxonomic Treatment (provided by <a href="http://plazi.org/" target="new">Plazi</a>)</h3>
        <div id="plazi">
        
        <% 
            String taxonomicTreatment = (String) desc.get("taxonomictreatment");
            //String taxonomicTreatment = new Formatter().convertFromUTF8((String) desc.get("taxonomictreatment"));
            //if (AntwebProps.isDevOrStageMode()) AntwebUtil.log("taxonPage-body.jsp utf8 taxonomicTreatment:" + taxonomicTreatment); %>
        <%= taxonomicTreatment %>
                    <!-- %= new Formatter().convertFromUTF8((String) desc.get("taxonomictreatment")) % -->
                    <!-- % String words = new Formatter().removeTag(new Formatter().replaceAttribute("style", (String) desc.get("taxonomictreatment"),""), "br"); % -->
                    <!-- %= new Formatter().removeTag((String) words, "o:p") % -->
        </div>

<%    } // taxonomic treatment
    }

    if (desc.containsKey("plaziData")) { %>
        <p><h3>Taxonomic Treatment (provided by <a href="http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:<%= taxon.getHolId() %>" target="new">Plazi</a>)</h3>
        <div id="plazi">
        
        <% 
      String taxonomicTreatment = (String) desc.get("plaziData");
            //String taxonomicTreatment = new Formatter().convertFromUTF8((String) desc.get("taxonomictreatment"));
            //if (AntwebProps.isDevOrStageMode()) AntwebUtil.log("taxonPage-body.jsp utf8 taxonomicTreatment:" + taxonomicTreatment); %>
        <%= taxonomicTreatment %>
        </div>
<%  } // plaziData

  } // desc != null

  if (taxon.hasSpecimenDataSummary()) {  %>   
    <p><h3>Specimen Habitat Summary</h3>
    <p><%= taxon.getSpecimenDataSummary(10) %>
<% 
  } %>


<% if (LoginMgr.isAdmin(accessLogin)) { %>
<br clear=all>
<p><h3>Admin Information:</h3>
<div>
<div id=data_left2>

<div class="clear"></div>
<div id=data_title2>
<b>TaxonName:</b>
</div>
<div id=data_items2>
<%
  String taxonNameUrl = AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getTaxonName();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("TaxonNameUrl:" + taxonNameUrl);
%>
<a href="<%= taxonNameUrl %>"><%= taxon.getTaxonName() %></a>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Display TaxonName:</b>
</div>
<div id=data_items2>
<%= taxon.getTaxonNameDisplay() %>
</div>


<div class="clear"></div>
<div id=data_title2>
<b>Name:</b>
</div>
<div id=data_items2>
<%= taxon.getName() %> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
<b>ParentTaxonName:</b>
</div>
<div id=data_items2>
<%
  taxonNameUrl = AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getParentTaxonName();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("TaxonNameUrl:" + taxonNameUrl);
%>
<a href="<%= taxonNameUrl %>"><%= taxon.getParentTaxonName() %></a>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Rank:</b>
</div>
<div id=data_items2>
<%= taxon.getRank() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Kingdom</b>
</div>
<div id=data_items2>
<%= Formatter.initCap(taxon.getKingdomName()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>PhylumName:</b>
</div>
<div id=data_items2>
<%= Formatter.initCap(taxon.getPhylumName()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>OrderName:</b>
</div>
<div id=data_items2>
<%= Formatter.initCap(taxon.getOrderName()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>ClassName:</b>
</div>
<div id=data_items2>
<%= Formatter.initCap(taxon.getClassName()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Family:</b>
</div>
<div id=data_items2>
<%= Formatter.initCap(taxon.getFamily()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Subfamily:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(Formatter.initCap(taxon.getSubfamily())) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Tribe:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(Formatter.initCap(taxon.getTribe())) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Genus:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(Formatter.initCap(taxon.getGenus())) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Subgenus:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(Formatter.initCap(taxon.getSubgenus())) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Species Group:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(Formatter.initCap(taxon.getSpeciesGroup())) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Species:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(taxon.getSpecies()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Subspecies:</b>
</div>
<div id=data_items2>
<%= Utility.notBlankValue(taxon.getSubspecies()) %>
</div>
<div class="clear"></div>
<div id=data_title2>
<b>Default Specimen:</b>
</div>
<div id=data_items2>
Worker: <%= taxon.getDefaultSpecimen(Caste.WORKER) %>
Male: <%= taxon.getDefaultSpecimen(Caste.MALE) %>
Queen: <%= taxon.getDefaultSpecimen(Caste.QUEEN) %>
</div>

<%
TaxonSet taxonSet = taxon.getTaxonSet();
%>
<div class="clear"></div>
<div id=data_title2>
<b>TaxonSet:</b>
</div>
<div id=data_items2>
<br><%= taxonSet %> &nbsp;
<br><%= overview.getCountCrawlLink() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Created:</b>
</div>
<div id=data_items2>
<%= (new Formatter()).clearNull(taxon.getCreated()) %> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
<b>ImageCount (deprecated):</b>
</div>
<div id=data_items2>
<%= taxon.getImageCount() %> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
<b>ImageCount (deprecated):</b>
</div>
<div id=data_items2>
<%= taxon.getImageCount() %> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
<b>HasImageCount (deprecated):</b>
</div>
<div id=data_items2>
<%= taxon.getHasImagesCount() %> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Source:</b>
</div>
<div id=data_items2>
<%= taxon.getSource() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Line Number:</b>
</div>
<div id=data_items2>
<%= taxon.getLineNumLink() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Insert Method:</b>
</div>
<div id=data_items2>
<%= taxon.getInsertMethod() %>
</div>

  <div class="clear"></div>
<div id=data_title2>
<b>ExecTime:</b>
</div>
<div id=data_items2>
<%= taxon.getExecTime() %>
</div>

  <div class="clear"></div>
<div id=data_title2>
<b>Fossil:</b>
</div>
<div id=data_items2>
<%= taxon.getIsFossil() %>
</div>

  <div class="clear"></div>
<div id=data_title2>
<b>AntCat:</b>
</div>
<div id=data_items2>
<%= taxon.getIsAntCat() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Pending:</b>
</div>
<div id=data_items2>
<%= taxon.getIsPending() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Antcat ID:</b>
</div>
<div id=data_items2>
<a href='http://antcat.org/catalog/<%= taxon.getAntcatId() %>'><%= taxon.getAntcatId() %></a>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>HOL ID:</b>
</div>
<div id=data_items2>
<a href="http://hol.osu.edu/index.html?id=<%= taxon.getHolId() %>"><%= taxon.getHolId() %></a>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Plazi Treatment:</b>
</div>
<div id=data_items2>
<a href="http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:<%= taxon.getHolId() %>"><%= taxon.getHolId() %></a>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Author Date:</b>
</div>
<div id=data_items2>
<%= taxon.getAuthorDate() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Author Date Html:</b>
</div>
<div id=data_items2>
<%= taxon.getAuthorDateHtml() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Authors:</b>
</div>
<div id=data_items2>
<%= taxon.getAuthors() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Year:</b>
</div>
<div id=data_items2>
<%= taxon.getYear() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Status:</b>
</div>
<div id=data_items2>
<%= taxon.getStatus() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Available:</b>
</div>
<div id=data_items2>
<%= taxon.getIsAvailable() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Current Valid Name:</b>
</div>
<div id=data_items2>

<%= currentValidNameLink %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Current Valid Rank:</b>
</div>
<div id=data_items2>
<%= taxon.getCurrentValidRank() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Current Valid Parent:</b>
</div>
<div id=data_items2>
<%= taxon.getCurrentValidParent() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Original Combination:</b>
</div>
<div id=data_items2>
<%= taxon.getIsOriginalCombination() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Was Original Combo:</b>
</div>
<div id=data_items2>
<%= taxon.getWasOriginalCombination() %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>Group:</b>
</div>
<div id=data_items2>
<a href="<%= AntwebProps.getDomainApp() %>/group.do?id=<%= taxon.getGroupId() %>"><%= GroupMgr.getGroup(taxon.getGroupId()) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>is Quadrinomial:</b>
</div>
<div id=data_items2>
<%= taxon.isQuadrinomial(taxon.getTaxonName()) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>is Morpho:</b>
</div>
<div id=data_items2>
<%= taxon.isMorpho(taxon.getTaxonName()) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>is Indet:</b>
</div>
<div id=data_items2>
<%= taxon.isIndet(taxon.getTaxonName()) %>
</div>

<div class="clear"></div>
<div id=data_title2>
<b>chart Color:</b>
</div>
<div id=data_items2>
<font color="<%= taxon.getChartColor() %>"><%= taxon.getChartColor() %></font>
</div>

<% if (taxon.isSpeciesOrSubspecies()) { %>
<div class="clear"></div>
<div id=data_title2>
<b>Default Images:</b>
</div>
<div id=data_items2>
Male: <%= taxon.getDefaultSpecimenLink(Caste.MALE) %>
<br>Worker: <%= taxon.getDefaultSpecimenLink(Caste.WORKER) %>
<br>Queen: <%= taxon.getDefaultSpecimenLink(Caste.QUEEN) %>
</div>
<% } else if (Rank.GENUS.equals(taxon.getRank())) { %>
<div class="clear"></div>
<div id=data_title2>
Default Images:
</div>
<div id=data_items2>
(for genera, find at runtime)
</div>
<% } %>

<% if (Rank.GENUS.equals(taxon.getRank())) { %>
<div class="clear"></div>
<div id=data_title2>
<b>Native Bioregion Map:</b>
</div>
<div id=data_items2>
<%= taxon.getBioregionMap() %>
</div>
<% } %>

<% if (taxon.isSpeciesOrSubspecies() && TaxonPropMgr.isIntroducedSomewhere(taxon.getTaxonName())) { %>
<div class="clear"></div>
<div id=data_title2>
<b>Introduced Species 
<br>Native Bioregion Map:</b>
</div>
<div id=data_items2>
<%= taxon.getIntroducedMap() %>
</div>
<% } %>

</div> <!-- data_left2 -->

</div>  <!-- end Admin Information -->

<div class="clear"></div>

<% } %>

<% 
    String title = new Formatter().capitalizeFirstLetter(taxon.getRank()) + ":+" +  new Formatter().capitalizeFirstLetter(taxon.getPrettyName()); 
    //String extentString = null;
    String image = "";
    if (taxon.getImages() != null) {
      if ((taxon.getImages().containsKey("p")) || (taxon.getImages().containsKey("p1"))) {

        String key = "p";
        if (!taxon.getImages().containsKey("p")) {
            key = "p1";
        }
        image = ((org.calacademy.antweb.SpecimenImage) taxon.getImages().get(key)).getThumbview();
      }
    }
%>

<div class="clear"></div>
<br /><br />
<a href="mailto:antweb@calacademy.org?subject=Regarding AntWeb page <%= thePage %>">See something amiss? Send us an email.</a>

    </div>
    <div class="left">

<% if ((taxon.getImages() != null) && (taxon.getImages().containsKey("h"))) { %>
        <div class="slide medium last" style="background-image: url('<%= AntwebProps.getImgDomainApp() %><%=((org.calacademy.antweb.SpecimenImage) taxon.getImages().get("h")).getThumbview() %>');" onclick="window.location='bigPicture.do?name=<%= ((org.calacademy.antweb.SpecimenImage) taxon.getImages().get("h")).getCode() %>&shot=h&number=1<%= projectStr %>';">
        </div>
        <div class="clear"></div>        
<% } %>

    <!-- % @include file="/curate/defaultSpecimen/defaultSpecimenPanel.jsp" % --> 

        <div class="clear"></div>
 <%
   if (map != null) { %>
        <div class="small_map">
            <%@include file="/maps/googleMapPreInclude.jsp" %>  
            <%@include file="/maps/googleMapInclude.jsp" %>  
        </div>
<% } else { 
     if (AntwebProps.isDevMode()) AntwebUtil.log("taxonPage-body.jsp Map not supported for overview:" + overview);
   } 
%>
    </div>
    <div class="clear"></div>


</div>
