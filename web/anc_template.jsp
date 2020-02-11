<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
   if (!org.calacademy.antweb.util.HttpUtil.isInWhiteListCheck(request.getQueryString(), response)) return;  
   String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="[% title %]" />
	<tiles:put name="body-content" type="string">

    <div id="page_contents">
           <h1>[% title %]</h1>

      <div class="clear"></div>

      <div class="page_divider"></div>

    </div>

    <div id="page_data">
      <div id="overview_data">
       
           [% contents %]

           <%
            AncFile ancFile = (AncFile) session.getAttribute("ancFile");	   
        
            Login accessLogin = LoginMgr.getAccessLogin(request);

            if (accessLogin != null) {
              String requestURL = request.getRequestURL().toString();
              String accessIdStr = "/" + (new Integer(accessLogin.getId())).toString() + "/";
              if ( (accessLogin.isAdmin())
                || (accessLogin.getProjectNames().contains("[% project %]"))   
                || (requestURL.contains(accessIdStr))
                || (requestURL.contains("curators"))	            
                 ) {
           %>
           <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=[% id %]" />
                <input type="submit" value="Edit Page">
            </form>
             <% if (!(session.getAttribute("ancFile") == null)) { %>
            <form method="POST" action="<%= domainApp %>/ancPageSave.do"> 
                <input type="submit" value="Save Page">
            </form>
             <% } %>
           <% } %>	   	
         <% } %>
        
      </div>
    </div>
	</tiles:put>
</tiles:insert>
