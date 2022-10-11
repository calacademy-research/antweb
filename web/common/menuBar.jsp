<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<!-- menuBar.jsp -->
<%   
  if (true) {    
    String requestURI = request.getRequestURI();
    boolean condition = (requestURI.indexOf("index.jsp") != -1) || (requestURI.indexOf(".jsp") == -1);
    if (condition) { %>
<div id="navigation" style="margin-bottom:0;">
 <% } else { %>
<div id="navigation">
 <% } %>
 
 <%
   if (requestURI.indexOf("login.jsp") > -1) { 

    } else {
      // Add in the menus %>
    
    <div class="header">


        <div id="browseby" class="browse">
            <ul>
                <!-- li style="margin-right:12px;"><a href="< %= AntwebProps.getDomainApp() % >/browse.do?name=formicidae&rank=family&project=allantwebants">Browse:</a></li -->
                    <li class="menu_l1" data-submenu="global"><a class="browse_options">Global</a></li>
                    <li class="menu_l1" data-submenu="georegions"><a class="browse_options">Georegions</a></li>   <!-- was class="browse_options_green -->
                    <li class="menu_l1" data-submenu="bioregions"><a class="browse_options">Bioregions</a></li>
                    <li class="menu_l1" data-submenu="projects"><a class="browse_options">Projects</a></li>
                    <li class="menu_l1" data-submenu="museums"><a class="browse_options">Museums</a></li>
            </ul>
        </div>

        <% if (!HttpUtil.isMobile(request)) { %>
            <div class="searchBoxes1">
            <%@ include file="/common/searchBar.jsp" %>
            </div>
        <% }  %>

        <div class="clear"></div>
    </div>
    <div id="subnavigation">
        <div class="header">

<%@ include file="/common/globalMenu.jsp" %>
<%@ include file="/common/georegionMenu.jsp" %>
<%@ include file="/common/regionMenu.jsp" %>
<%@ include file="/common/projectMenu.jsp" %>
<%@ include file="/common/bioregionMenu.jsp" %>
<%@ include file="/common/museumMenu.jsp" %>

        </div>

        <div class="clear"></div>
        
    </div>
<% } %>    
    <div class="clear"></div>
</div>

 <%
    //if (AntwebProps.isDevMode()) AntwebUtil.log("menuBar.jsp none RequestURI:" + request.getRequestURI());
    if ((request.getRequestURI().indexOf("index.jsp") != -1) || (request.getRequestURI().indexOf(".jsp") == -1)) {    
    %>
<div id="contents" class="container home">
<div class="home_gradient">
 <% } else { %>
<div id="contents" class="container">
 <% } 
  }
 %>

