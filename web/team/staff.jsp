<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
if (!org.calacademy.antweb.util.HttpUtil.isInWhiteListCheck(request.getQueryString(), response)) return;
String domainApp = AntwebProps.getDomainApp();
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
<tiles:put name="title" value="AntWeb Staff" />	<tiles:put name="body-content" type="string">

<div id="page_contents">	   <h1>AntWeb Staff</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>

<div id="page_data"><div id="overview_data">
<p>
	<img alt="Brian Fisher" src="<%= domainApp %>/team/brian.jpg" /><br />
	<strong><a href="mailto:bfisher@calacademy.org">Brian Fisher</a>, AntWeb Project Leader</strong><br />
	Curator of Entomology and expert on African and Malagasy ants.</p>
<p>&nbsp;</p>
	<img alt="Michele Esposito" src="<%= domainApp %>/team/Michelev3.jpg" /><br />
	<strong><a href="mailto:mesposito@calacademy.org">Michele Esposito</a>, &quot;Data Tsar&quot;</strong></p>
<p>&nbsp;</p>
	<img alt="Michelle Koo" class="border " src="<%= domainApp %>/team/michelle.gif" style="border-width: 0px; border-style: solid;" /><br />
	<a href="mailto:mkoo@calacademy.org"><strong>Michelle Koo</strong></a><strong>, GIS consultant</strong><br />
	Using ESRI GIS software, geospatial data and ant specimens make great maps!</p>
<p>&nbsp;</p>
	<img alt="David Thau" src="<%= domainApp %>/team/thau.jpg" /><br />
	<strong><a href="mailto:thau@learningsite.com">David Thau</a>, Software Engineer</strong><br />
	Thau developed the AntWeb software and technical architecture.</p>
<p>&nbsp;</p>
	<img alt="Mark Johnson" src="<%= domainApp %>/team/mark.jpg" style="width: 225px;" /><br />
	<b><a href=" mailto:mark@inforaction.com">Mark Johnson</a>, Software Engineer</b><br />
	Mark is the principal software engineer developing and maintaining Antweb.<span>&nbsp;</span></p>

</div>
</tiles:put>
</tiles:insert>
