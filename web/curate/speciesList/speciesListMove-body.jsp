<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.calacademy.antweb.*" %>

<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    Taxon fromTaxon = (Taxon) request.getAttribute("fromTaxon");
    Taxon toTaxon = (Taxon) request.getAttribute("toTaxon");

    Login accessLogin = LoginMgr.getAccessLogin(request);
      
    String message = (String) request.getAttribute("message");
    if (message != null) out.println(message);
%>
<br><br>

<html:form method="GET" action="speciesListMove" enctype="multipart/form-data">

<input type="hidden" id="fromTaxonName" name="fromTaxonName" value="<%= fromTaxon.getTaxonName() %>"/>
<input type="hidden" id="toTaxonName" name="toTaxonName" value="<%= toTaxon.getTaxonName() %>"/>
     
<table><tr><td>      
<b>Rename</b> taxon:   
</td></tr>
<tr>
<td></td>

<td>
<% Taxon thisChild = fromTaxon; %>
<%@include file="/common/statusDisplayChild.jsp" %>
Subfamily:<html:text styleClass="input_150" property="fromSubfamily" value='<%= fromTaxon.getSubfamily() %>' disabled="true"/>
</td><td>
Genus:<html:text styleClass="input_150" property="fromGenus" value='<%= fromTaxon.getGenus() %>' disabled="true"/>
</td><td>
Species:<html:text styleClass="input_150" property="fromSpecies" value='<%= fromTaxon.getSpecies() %>' disabled="true"/>
</td><td>
Subspecies:<html:text styleClass="input_150" property="fromSubspecies" value='<%= fromTaxon.getSubspecies() %>' disabled="true"/>
</td>
</tr>
<tr><td>
<br><br><b>&nbsp;&nbsp; &nbsp;&nbsp; &nbsp;&nbsp;&nbsp;To</b> taxon:
</td></tr>
<tr><td>

</td>

<td>
<% thisChild = toTaxon; %>
<%@include file="/common/statusDisplayChild.jsp" %>Subfamily:<html:text styleClass="input_150" property="toSubfamily" value='<%= toTaxon.getSubfamily() %>' disabled="true"/>
</td><td>
Genus:<html:text styleClass="input_150" property="toGenus" value='<%= toTaxon.getGenus() %>' disabled="true"/>
</td><td>
Species:<html:text styleClass="input_150" property="toSpecies" value='<%= toTaxon.getSpecies() %>' disabled="true"/>
</td><td>
Subspecies:<html:text styleClass="input_150" property="toSubspecies" value='<%= toTaxon.getSubspecies() %>' disabled="true"/>
</td></tr>
</table>


<br><br>

<input type="submit" name="action" value="back"> 
&nbsp;&nbsp;&nbsp;
<input type="submit" name="action" value="save"> 

</html:form>

<br><hr><br>


