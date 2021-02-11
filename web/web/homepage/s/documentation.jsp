<%@ page errorPage = "/error.jsp" %><%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%
if (!org.calacademy.antweb.util.HttpUtil.isInWhiteListCheck(request.getQueryString(), response)) return;     
String domainApp = (new Utility()).getDomainApp(); 
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">	<tiles:put name="title" value="AntWeb Participation" />	<tiles:put name="body-content" type="string"><div id="page_contents">	   <h1>AntWeb Participation</h1>  <div class="clear"></div>  <div class="page_divider"></div></div><div id="page_data">  <div id="overview_data">	   	   <style type="text/css">
#overview_data ul li:first-child { width: 100%; } #overview_data ul li { float: none; }</style>
<h2>
	How to Participate</h2>
<h3>
	Images</h3>
<ul>
	<li>
		<a href="http://www.antweb.org/web/homepage/FMNH_Imaging_Manual_2013.pdf" target="_blank">Field Museum imaging manual</a> using LAS 4.2. 1 March 2013</li>
	<li>
		<a href="http://www.antweb.org/web/homepage/Imaging_Manual_LAS38_v03.pdf" target="_blank" title="How to Montage using Leica 3.8">How we Image ants</a> using LAS 3.8 Leica software&nbsp;DFC450 camera (PDF) 20 Dec 2011</li>
	<li>
		<a href="http://www.youtube.com/watch?v=xlY0-8_1DEE" title="How to video">How to image: taking lateral shots using LAS 3.8</a> (video) 6 Jan 2012</li>
	<li>
		<a href="http://www.youtube.com/watch?v=_pkRAk039Nc" title="YouTube video">Positioning for better Automontage images using LAS 3.8</a> (video) 15 Feb 2012</li>
	<li>
		<a href="http://youtu.be/-89pLoE533c" title="YouTube video">How to Image Labels</a> (video) 3 May 2012</li>
	<li>
		<a href="http://youtu.be/HUVuha-J8Dc" target="_blank" title="Ant Spinner video">A new tool for imaging, the Ant Spinner</a> (youtube video link) 20 Dec 2011</li>
	<li>
		<a href="AntWeb%20Imaging%20guidelines%20v01.pdf" target="new">How to Automontage</a>&nbsp;using the JVC camera and stand alone Automontage&nbsp;(PDF) 11/06/2010</li>
	<li>
		<a href="Photoshop_AntWeb.pdf" target="new">How to Photoshop images before uploading</a> (PDF) 16/04/2010</li>
	<li>
		<a href="Submitting_image.pdf" target="new">How to upload images</a> (PDF) 04/05/2010</li>
	<li>
		<a href="Image_Picker.pdf" target="new">How to use the Image Picker Tool</a> (PDF) 26/03/2010</li>
</ul>
<h3>
	Specimens</h3>
<ul>
	<li>
		<a href="http://www.antweb.org/web/homepage/Specimen data overview_v04.pdf" target="_blank">Overview of specimen data</a> capture and submission (PDF) 2012-02-20</li>
	<li>
		<a href="http://www.antweb.org/web/homepage/AntWeb specimen data requirements_2015v1.xlsx" target="_blank">Specimen template example</a> and instructions(XLSX) 2015-04-12</li>
</ul>
<h3>
	How to Develop a Regional Species List</h3>
<ul>
	<li>
		<a href="http://www.antweb.org/web/homepage/RegionalProject species list instructions_v03.pdf" target="_blank">How to develop and update region/project</a> species list and home page (PDF) 2012-02-20</li>
	<li>
		<a href="http://www.antweb.org/web/homepage/Antweb_reginal+species_list_example.xls" target="_blank">Species list example</a> (XLS) 2012-02-20</li>
	<li>
		<a href="https://www.antweb.org/bioregionCountryList.do" target="_blank">List of Countries and adm1s</a> for each Bioregion</li>
	<li>
		<a href="https://www.antweb.org/countryAdm1List.do" target="_blank">List of Country names and alternate names</a></li>
</ul>

    </div>
</div>	
</tiles:put>
</tiles:insert>