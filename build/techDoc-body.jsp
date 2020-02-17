<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<div id="page_contents">
<br>
<h1>AntWeb Technical Document</h1>
<div class="clear"></div>

<div class="page_divider"></div>
</div>
<!-- /div -->
<div id="page_data">
	<div id="overview_data">

To regenerate all geolocale overview page statistics, run:
<br>&nbsp;&nbsp;&nbsp;<%= AntwebProps.getDomainApp() %>/utilData.do?action=updateGeolocaleCounts
<br>&nbsp;&nbsp;&nbsp;This is too long to put in the Scheduler.
<br><%= AntwebProps.getDomainApp() %>/utilData.do?action=countCrawl&num=442

	</div>
</div>
