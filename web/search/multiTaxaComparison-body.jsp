<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.search.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- MultiTaxaComparison-body.jsp -->

<script src="<%= AntwebProps.getDomainApp() %>/openAndFocus.js" type="text/javascript"></script>
<script type="text/javascript">

function submitAndFocus(theForm) {
  theForm.target="compare";
  openAndFocus("","compare");
  theForm.submit();
}
</script>

<%
    java.util.ArrayList allShots = new java.util.ArrayList();
    allShots.add("h");
    allShots.add("p");
    allShots.add("d");
    allShots.add("l");
    allShots.add("v");

    java.util.ArrayList allSpecimenShots = new java.util.ArrayList();
    allSpecimenShots.add("h1");
    allSpecimenShots.add("p1");
    allSpecimenShots.add("d1");
    allSpecimenShots.add("l1");
    allSpecimenShots.add("v1");

    java.util.ArrayList shotsToShow;
%>
 
<jsp:useBean id="taxaToCompare" scope="session" class="java.util.TreeMap" />

<div id="page_contents">
    <h1>Compare Selected Results</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
    <div id="totals_and_tools_container">
        <div id="totals_and_tools">
			<h2><%= taxaToCompare.size() %> specimens to compare.</h2>
			<div id="thumb_toggle"><b><a href="#" onclick="history.back(); return false;">Back</a></b></div>
			<div class="clear"></div>
			<html:form action="compareResults">
			<div class="tools" id="compare_tools">
				<div class="tool_select_toggle" title="Click to select all">
					<input type="checkbox" name="selectall" id="selectall">
				</div>
				<div class="tool_text" id="multi_compare">
					Select ants, and view, and click "Compare Selected".
					<p>
					View:&nbsp;&nbsp; <input id="head_top" type="radio" name="shot" value="Head" checked> Head
					&nbsp;&nbsp; <input id="profile_top" type="radio" name="shot" value="Profile"> Profile
					&nbsp;&nbsp; <input id="dorsal_top" type="radio" name="shot" value="Dorsal"> Dorsal
					&nbsp;&nbsp; <input id="ventral_top" type="radio" name="shot" value="Ventral"> Ventral
					&nbsp;&nbsp; <input id="label_top" type="radio" name="shot" value="Label"> Label
                    &nbsp;&nbsp; <input id="compare_form" class="submit" type="submit" value="Compare Selected">
					</p>
				</div>
				<div class="clear"></div>
			</div>
        </div>
    </div>
</div>
<!-- /div -->

<div id="page_data">
<%
int position = 0;
int first = 1;
int fourth = 4;

  ArrayList<Taxon> resultsList = new ArrayList<Taxon>(taxaToCompare.keySet());

  A.log("multiTaxaComparison-body.jsp taxaToCompare:" + taxaToCompare);

  // was session Feb2020
  TreeMap<Taxon, Integer> theTaxaToCompare = (TreeMap<Taxon, Integer>) session.getAttribute("taxaToCompare");
  for (java.util.Map.Entry<Taxon,Integer> entry : theTaxaToCompare.entrySet()) {
    Taxon taxon = (Taxon) entry.getKey();
     
    //A.log("multiTaxaComparison-body.jsp taxaToCompare entry:" + entry + " taxon:" + taxon + " hasImages:" + taxon.getHasImages() + " rank:" + taxon.getRank());     

    if (taxon.getHasImages() && taxon.getImages() != null) {

      //A.log("2 taxon:" + taxon + " images:" + taxon.getImages()); 
      if (entry.getKey().getRank().equals("specimen")) {
 %>
            <div class="data_checkbox" style="float:left; margin-top:2px;"><input type="checkbox" name="chosen" value="<%= entry.getValue() %>"></div>

            <div class="ratio_name"> <a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=<%= taxon.getFullName() %>"><%= taxon.getPrettyName() %></a>
			<% Formatter format = new Formatter();
			   String genus, species, niceGenus, subspecies;
			   if ((taxon.getGenus() != null) && (taxon.getSpecies() != null)) {

				 //A.log("multiTaxaComparison-body.jsp FIX the subspecies here.");
				 //Where is the subspecies?  How to replicate this.  Let's find out...    
				 //  the_name = taxon.getTaxonNameDisplay();
			%>
				   <a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= taxon.getTaxonName() %>"> : <%= taxon.getTaxonNameDisplay() %></a>
                   &nbsp<font size=3><%= ((Specimen) taxon).getCasteStr() %></font>
			<% } %>
            </div> 
            <div class="clear"></div>

			<% //A.log("multiTaxaComparison-body.jsp allSpecimenShots:" + allSpecimenShots); %>

            <logic:iterate id="shot" collection="<%=allSpecimenShots %>" >
				<%
				  ++position;
				  if (position == 5) {
					  position = 1;
				  }
				%>
				<logic:iterate id="element" collection="<%= taxon.getImages() %>">
				  <logic:equal value="<%= (String) shot %>" name="element" property="key">
					<div class="slide medium <% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %> ratio">
						<bean:define id="imgCode" name="element" property="value.code" />
						<%
						String bigImgLink = AntwebProps.getDomainApp() + "/bigPicture.do?name=" + imgCode + "&shot=" + ((String) shot).substring(0, 1) + "&number=" + ((String) shot).substring(1);
						%>
						<div class="adjust"><img class="medres" src="<%= AntwebProps.getImgDomainApp() %><bean:write name="element" property="value.thumbview" />" onclick="window.location='<%= bigImgLink %>';"></div>
					</div>
				  </logic:equal>
				</logic:iterate>

			</logic:iterate>

            <div class="clear"></div>

        <% }  else { %>
        
          <div class="data_checkbox" style="float:left; margin-top:2px;"><input type="checkbox" name="chosen" value="<%= entry.getValue() %>"></div> 
          <div class="ratio_name"><a href="<%= AntwebProps.getDomainApp() %>/description.do?<%= taxon.getBrowserParams() %>"><%= taxon.getPrettyName() %></a></div>

          <div class="clear"></div>
        
          <logic:iterate id="shot" collection="<%=allShots %>" >
            <%
			  ++position;
			  if (position == 5) {
				  position = 1;
			  }
			  if (taxon.getImages() != null) { 
			%>
				<logic:iterate id="element" collection="<%= taxon.getImages() %>">
				  <logic:equal value="<%= (String) shot %>" name="element" property="key">
					<div class="slide medium <% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %> ratio">
					  <div class="adjust"><img class="medres" src="<%= AntwebProps.getImgDomainApp() %><bean:write name="element" property="value.thumbview" />" onclick="window.location='<%= AntwebProps.getDomainApp() %>/specimen.do?name=<bean:write name="element" property="value.code" />&shot=<%= (String) shot %>';"></div>
					</div>
				  </logic:equal>
				</logic:iterate>

           <% } %>

          </logic:iterate>

          <div class="clear"></div>
   <% } // if taxon rank specimen %>
 <% } // if taxon has images %>
<%
  } // through the taxa %>

</html:form>
</div>

