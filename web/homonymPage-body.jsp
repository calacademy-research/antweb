

<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<%  
    //A.log("homonymPage-body.jsp");
    
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    String thePage = HttpUtil.getTarget(request);
    
    Overview overview = OverviewMgr.getOverview(request);
    
    Hashtable desc = taxon.getDescription();   
   
    String guiDefaultContent = AntwebProps.guiDefaultContent;
   
    java.util.Calendar today = java.util.Calendar.getInstance();
    int year = today.get(java.util.Calendar.YEAR);
  
    //String thisPageTarget = java.net.URLEncoder.encode("description.do?" + taxon.getBrowserParams());  
    String thisPageTarget = "description.do?" + taxon.getBrowserParams();
    String editField = request.getParameter("editField");
    if (editField == null) editField = "none";

    String dagger = "";
    if (taxon.getIsFossil()) dagger = "&dagger;";

    //A.log("homonymPage-body.jsp taxon:" + taxon);
%>

<!-- homonymPage-body.jsp -->

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">

<%
//if (AntwebProps.isDevMode()) AntwebUtil.log("homonymPage.jsp taxonName:" + taxon.getFullName());
%>

<%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li></li><!-- Overview -->
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

<%
  String projectStr = "";
%>

<%@ include file="/common/taxonomicHierarchy.jsp" %>


<div id="page_data">
    <div id="overview_data">


<%  

    //A.log("homonymPage-body.jsp desc:" + desc + " family:" + taxon.getFamily() + " subfamily:" + taxon.getSubfamily() + " taxonName:" + taxon.getTaxonName());
    
    if (desc != null) { 
      String thisDesc = null;
      String descHeader = null; 
      String descNotes = null;
      
      boolean hasExtraPrivs = false;
%>

   <% String alsoDatabased = taxon.getAlsoDatabased();
      if (alsoDatabased != null) { %>
        <h2>Also Databased as Synonym: <%= alsoDatabased %></h2> <p>
   <% } %>
            
   <% // Print out the master taxon in bold and the other homonyms
      int i = 0;   
      Homonym homonym = (Homonym) taxon;      
      Taxon seniorHomonym = homonym.getSeniorHomonym();
      Vector<String> homonymAuthorDates = taxon.getHomonymAuthorDates();
      if (seniorHomonym != null || (homonymAuthorDates != null && !homonymAuthorDates.isEmpty())) { %>
        <h2>Homonyms:</h2>
     <%
      if (seniorHomonym != null) { 
          ++i; 
          %>
          <b><a href="<%= seniorHomonym.getUrl() %>"><%= (seniorHomonym.getAuthorDate() != null) ? seniorHomonym.getAuthorDate() : seniorHomonym.getPrettyName()  %>
          <% if (seniorHomonym.getIsValid()) { %>
            <img src="<%= AntwebProps.getDomainApp() %>/image/valid_name.png" border="0" title="Valid Name">          
          <% } %>
            </a></b>      
   <% }
        for (String authorDate : homonymAuthorDates) { 
          ++i;
          if (i > 1) out.println(", ");
          String taxonUrl = homonym.getTaxonUrl();
        %>        
          <a href="<%= taxonUrl %>&authorDate=<%= authorDate %>"><%= authorDate %></a>
     <% } %>
       <br>
   <% } %>            
              
   <% if (desc.containsKey("taxonomichistory")) { %>
        <br><h2>Taxonomic History (provided by Barry Bolton, <%= year %>)</h2>
                    <% String words = new Formatter().removeTag(new Formatter().replaceAttribute("style", (String) desc.get("taxonomichistory"),""), "br"); %>
                    <%= new Formatter().removeTag((String) words, "o:p") %>
   <% } %>

   <% String seeAlso = taxon.getSeeAlso();
      if (seeAlso != null) { %>
        <h3>See Also:</h3> <%= seeAlso %>
   <% }

    if (LoginMgr.isAdmin(request)) { 
   
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
<%    }
    }

    if (desc.containsKey("plaziData")) { 
    %>

        <p><h3>Taxonomic Treatment (provided by <a href="http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:<%= homonym.getHolId() %>" target="new">Plazi</a>)</h3>
        <div id="plazi">
        
        <% 
            String taxonomicTreatment = (String) desc.get("plaziData");
            //String taxonomicTreatment = new Formatter().convertFromUTF8((String) desc.get("taxonomictreatment"));
            //if (AntwebProps.isDevOrStageMode()) AntwebUtil.log("taxonPage-body.jsp utf8 taxonomicTreatment:" + taxonomicTreatment); %>
        <%= taxonomicTreatment %>
                    <!-- %= new Formatter().convertFromUTF8((String) desc.get("taxonomictreatment")) % -->
                    <!-- % String words = new Formatter().removeTag(new Formatter().replaceAttribute("style", (String) desc.get("taxonomictreatment"),""), "br"); % -->
                    <!-- %= new Formatter().removeTag((String) words, "o:p") % -->
        </div>
<% 
    }
  }

  if (LoginMgr.isAdmin(request)) { %>
<br clear=all>
<p><h3>Admin Information:</h3>
<div>
<div id=data_left2>

<div class="clear"></div>
<div id=data_title2>
TaxonName:
</div>
<div id=data_items2>
<%
  String taxonNameUrl = AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getTaxonName();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("TaxonNameUrl:" + taxonNameUrl);
%>
<b><a href="<%= taxonNameUrl %>"><%= taxon.getTaxonName() %></a></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Display TaxonName:
</div>
<div id=data_items2>
<b><%= taxon.getTaxonNameDisplay() %></b>
</div>


<div class="clear"></div>
<div id=data_title2>
Name:
</div>
<div id=data_items2>
<b><%= taxon.getName() %></b> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
ParentTaxonName:
</div>
<div id=data_items2>
<%
  taxonNameUrl = AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getParentTaxonName();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("TaxonNameUrl:" + taxonNameUrl);
%>
<b><a href="<%= taxonNameUrl %>"><%= taxon.getParentTaxonName() %></a></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Rank:
</div>
<div id=data_items2>
<b><%= taxon.getRank() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Kingdom</div>
<div id=data_items2>
<b><%= Formatter.initCap(taxon.getKingdomName()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
PhylumName:
</div>
<div id=data_items2>
<b><%= Formatter.initCap(taxon.getPhylumName()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
OrderName:
</div>
<div id=data_items2>
<b><%= Formatter.initCap(taxon.getOrderName()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
ClassName:
</div>
<div id=data_items2>
<b><%= Formatter.initCap(taxon.getClassName()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Family:
</div>
<div id=data_items2>
<b><%= Formatter.initCap(taxon.getFamily()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Subfamily:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(Formatter.initCap(taxon.getSubfamily())) %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Tribe:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(Formatter.initCap(taxon.getTribe())) %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Genus:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(Formatter.initCap(taxon.getGenus())) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Subgenus:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(Formatter.initCap(taxon.getSubgenus())) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Species Group:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(Formatter.initCap(taxon.getSpeciesGroup())) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Species:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(taxon.getSpecies()) %></b>
</div>
<div class="clear"></div>
<div id=data_title2>
Subspecies:
</div>
<div id=data_items2>
<b><%= Utility.notBlankValue(taxon.getSubspecies()) %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Created:
</div>
<div id=data_items2>
<b><%= (new Formatter()).clearNull(taxon.getCreated()) %></b> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
ImageCount (deprecated):
</div>
<div id=data_items2>
<b><%= taxon.getImageCount() %></b> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
ImageCount (deprecated):
</div>
<div id=data_items2>
<b><%= taxon.getImageCount() %></b> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
HasImageCount (deprecated):
</div>
<div id=data_items2>
<b><%= taxon.getHasImagesCount() %></b> &nbsp;
</div>

<div class="clear"></div>
<div id=data_title2>
Source:
</div>
<div id=data_items2>
<b><%= taxon.getSource() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Line Number:
</div>
<div id=data_items2>
<b><%= taxon.getLineNum() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Insert Method:
</div>
<div id=data_items2>
<b><%= taxon.getInsertMethod() %></b>
</div>

  <div class="clear"></div>
<div id=data_title2>
ExecTime:
</div>
<div id=data_items2>
<b><%= taxon.getExecTime() %></b>
</div>

  <div class="clear"></div>
<div id=data_title2>
Fossil:
</div>
<div id=data_items2>
<b><%= taxon.getIsFossil() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Antcat ID:
</div>
<div id=data_items2>
<b><a href="http://antcat.org/catalog/<%= taxon.getAntcatId() %>"><%= taxon.getAntcatId() %></a></b>
</div>

<div class="clear"></div>
<div id=data_title2>
HOL ID:
</div>
<div id=data_items2>
<b><a href="http://hol.osu.edu/index.html?id=<%= taxon.getHolId() %>"><%= taxon.getHolId() %></a></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Plazi Treatment:
</div>
<div id=data_items2>
<b><a href="http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:<%= taxon.getHolId() %>"><%= taxon.getHolId() %></a></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Pending:
</div>
<div id=data_items2>
<b><%= taxon.getIsPending() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Author Date:
</div>
<div id=data_items2>
<b><%= taxon.getAuthorDate() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Author Date Html:
</div>
<div id=data_items2>
<b><%= taxon.getAuthorDateHtml() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Authors:
</div>
<div id=data_items2>
<b><%= taxon.getAuthors() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Year:
</div>
<div id=data_items2>
<b><%= taxon.getYear() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Status:
</div>
<div id=data_items2>
<b><%= taxon.getStatus() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Available:
</div>
<div id=data_items2>
<b><%= taxon.getIsAvailable() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Current Valid name:
</div>
<div id=data_items2>
<b><%= taxon.getCurrentValidName() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Current Valid Rank:
</div>
<div id=data_items2>
<b><%= taxon.getCurrentValidRank() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Current Valid Parent:
</div>
<div id=data_items2>
<b><%= taxon.getCurrentValidParent() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Original Combination:
</div>
<div id=data_items2>
<b><%= taxon.getIsOriginalCombination() %></b>
</div>

<div class="clear"></div>
<div id=data_title2>
Was Original Combo:
</div>
<div id=data_items2>
<b><%= taxon.getWasOriginalCombination() %></b>
</div>


</div> <!-- data_left2 -->

</div>  <!-- end Admin Information -->

<div class="clear"></div>

<% } // end admin info 

//A.log("homonymPage-body.jsp end");
%>

<div class="clear"></div>
<br /><br />
<a href="mailto:antweb@calacademy.org?subject=Regarding AntWeb page <%= thePage %>">See something amiss? Send us an email.</a>


    </div>

</div>
