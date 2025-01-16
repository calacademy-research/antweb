<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@include file="/common/antweb_admin-defs.jsp" %>


<%
   String header = (String) request.getAttribute("header");
   if (header == null) header = "testMessage";

   String target = HttpUtil.getTarget(request);
   if (target.contains("userAgents.do")) { %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="User Agents" />
	<tiles:put name="body-content" value="/util/userAgents-body.jsp" />	
</tiles:insert>

<% } else {
      AntwebUtil.logInfo("Unexpected target:" + target);
   } %>



