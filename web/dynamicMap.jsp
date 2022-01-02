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
    String taxonRank = null;
    String prettyName = null;
    //Taxon taxon = (Taxon) session.getAttribute("taxon");
    if (taxon != null) {
        taxonRank = taxon.getRank();
        if (taxonRank == null) {
            AntwebUtil.log("warn", "dynamicMap.jsp taxon.getRank() is null. Taxon name: " + taxon.getTaxonName() +
                    " requestInfo:" + HttpUtil.getRequestInfo(request));
        }
        prettyName = (String) session.getAttribute("title");  //taxon.getPrettyName();

        if (prettyName == null) {
            AntwebUtil.log("warn", "dynamicMap.jsp taxon.getPrettyName() is null. Taxon name: " + taxon.getTaxonName() +
                    " requestInfo:" + HttpUtil.getRequestInfo(request));
        }
    }
    if ((taxonRank == null) || (prettyName == null)) {
        taxonRank = "Selected Results";
        prettyName = "";
    }
    String titleString = "Map of " + taxonRank + " " + prettyName + " from AntWeb";

    String metaString = "<meta name='keywords' content='" + prettyName + ", AntWeb, ants,ant,formicidae '/>";
    metaString += "<meta name='description' content='Map of " + taxonRank + " " + prettyName + " from AntWeb.'/>";
%>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="<%= titleString %>"/>
    <tiles:put name="meta" value="<%= metaString %>"/>
    <tiles:put name="body-content" value="/dynamicMap-body.jsp"/>
</tiles:insert>

