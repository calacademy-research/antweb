<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<div id="page_contents">
    <h1>AntWeb Technology</h1>
    <jsp:include page="about_nav.jsp" />
    <div class="clear"></div>
    <div class="page_divider"></div>

    <div id="page_data">
    <div id="overview_data">

    AntWeb integrates information from a variety of sources: taxonomic authority files in Excel, specimen data in Biota, and images taken using Automontage and a Leica microscope. This information is combined and stored in a MySQL database, and dynamically served to the Web using Apache's Tomcat servlet container. AntWeb's software is written in Java, and built using the aptly named Apache Ant. 

</div> 
