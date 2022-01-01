<%@ page errorPage="error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.util.HttpUtil" %>

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon"/>
<jsp:setProperty name="taxon" property="*"/>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="common/antweb-defs.jsp" %>
<%
    String the_rank = null;
    String the_name = null;
    //Taxon taxon = (Taxon) session.getAttribute("taxon");
    if (taxon != null) {
        the_rank = taxon.getRank();
        if (the_rank == null) {
            AntwebUtil.log("warn", "dynamicMap.jsp taxon.getRank() is null. Taxon name: " + taxon.getTaxonName() +
                    " requestInfo:" + HttpUtil.getRequestInfo(request));
        }
        the_name = taxon.getPrettyName();

        if (the_name == null) {
            AntwebUtil.log("warn", "dynamicMap.jsp taxon.getPrettyName() is null. Taxon name: " + taxon.getTaxonName() +
                    " requestInfo:" + HttpUtil.getRequestInfo(request));
        }
    }
    if ((the_rank == null) || (the_name == null)) {
        the_rank = "Selected Results";
        the_name = "";
    }
    String titleString = "Map of " + the_rank + " " + the_name + " from AntWeb";

    String metaString = "<meta name='keywords' content='" + the_name + ", AntWeb, ants,ant,formicidae '/>";
    metaString += "<meta name='description' content='Map of " + the_rank + " " + the_name + " from AntWeb.'/>";
%>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="<%= titleString %>"/>
    <tiles:put name="meta" value="<%= metaString %>"/>
    <tiles:put name="body-content" value="/dynamicMap-body.jsp"/>
</tiles:insert>

