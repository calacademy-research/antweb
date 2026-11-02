<%-- Tiles page of group.do?id= (or name=) (GroupAction): the group's page, title "Ant Group" + the group, body
     group-body.jsp. A direct request of the JSP prints "Invalid URL.  Direct JSP calls unsupported."
     (HttpUtil.isStaticCallCheck). Docs: docs/numbers.md row 91. --%><%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
        
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Group group = (Group) request.getAttribute("group");

	String titleString = "Ant Group" + group.toString();

%>
<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
	<tiles:put name="body-content" value="/group-body.jsp" />	
</tiles:insert>
