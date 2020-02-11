<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<% String domainApp = (new Utility()).getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">	
<tiles:put name="title" value="Other anomalies" />	
<tiles:put name="body-content" type="string">	   
<div class="left">	   
    <h1>Other anomalies</h1>	   	   
    Homeotic mutants...	   	   
    <%	      
    Login accessLogin = LoginMgr.getAccessLogin(request);	      
    if ((accessLogin != null) && (accessLogin.isAdmin() || (accessLogin.getProjects().contains("anomalousants")))) { %>	   	   
        <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=39" />	   		
            <input type="submit" value="Edit Page">	   	
        </form>	   	
        <% if (!(session.getAttribute("ancFile") == null)) { %>	   	
        <form method="POST" action="<%= domainApp %>/ancPageSave.do"> 	   		
            <input type="submit" value="Save Page">	   	
        </form>        
        <% } %>
    <% } %>	   		   	
</div>	
</tiles:put>
</tiles:insert>