<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
   if (!org.calacademy.antweb.util.HttpUtil.isInWhiteListCheck(request.getQueryString(), response)) return;  
   String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Key to Odontomachus species of Madagascar" />
	<tiles:put name="body-content" type="string">

    <div id="page_contents">
           <h1>Key to Odontomachus species of Madagascar</h1>

      <div class="clear"></div>

      <div class="page_divider"></div>

    </div>

    <div id="page_data">
      <div id="overview_data">
       
           <p><strong>See also Lucid matrix based key at: </strong><a href="http://idlifedev.cbit.uq.edu.au/server-player/player.jsp?datasetId=odontomachus">http://idlifedev.cbit.uq.edu.au/server-player/player.jsp?datasetId=odontomachus</a></p><p><strong>Key to workers and queens of Malagasy <em>Odontomachus</em></strong></p> 
 <p>1. Head narrow behind eyes; mandible with long, acute apical and preapical teeth; vertex of head coarsely, transversely striate..................................................................................................... <strong><em>coquereli</em></strong></p> 
 <p> Head only slightly narrower across vertex than across eyes, with distinct extraocular furrows and temporal ridges; apical and preapical teeth of mandible short and blunt; vertex finely striate longitudinally, diverging behind..................................................................................................................................... 2</p> 
 <p>2. ... Metasternal process acute, forming paired, slender spines, often unequal in length (Fig. 13a). Petiole spine notably bent posteriorly at base.............................................................................. <strong><em>troglodytes</em></strong></p> 
 <p> Metasternal process low, rounded (Fig. 13b). Petiole spine slightly curved posteriorly, comma but not noticeably bent posteriorly at base of spine.............................................................................. <strong><em>simillimus</em></strong></p> 
 <p><strong>Key to males of Malagasy <em>Odontomachus</em> </strong></p> 
 <p>1 Shortest distance between lateral ocellus and margin of compound eye smaller than maximum length of ocellus. Antenna with suberect setae; declivitous surface of propodeum without distinct rugae (Madagascar) ................................................................................................................................... <strong><em>coquereli</em></strong></p> 
 <p> Shortest distance between lateral ocellus and margin of compound eye distinctly greater than maximum length of ocellus. Antenna with very short appressed to decumbent setae; declivitous surface of propodeum with distinct rugae directed towards margins ............................................................................... 2</p> 
 <p>2 Body brownish yellow. Tarsal claw with small subapical tooth (Madagascar).......... <strong><em>troglodytes</em></strong></p> 
 <p> Body blackish or brown. Tarsal claw without subapical tooth (Seychelles)............... <strong><em>simillimus</em></strong></p>



           <%
            AncFile ancFile = (AncFile) session.getAttribute("ancFile");	   
        
            Login accessLogin = LoginMgr.getAccessLogin(request);

            if (accessLogin != null) {
              String requestURL = request.getRequestURL().toString();
              String accessIdStr = "/" + (new Integer(accessLogin.getId())).toString() + "/";
              if ( (accessLogin.isAdmin())
                || (accessLogin.getProjectNames().contains("madants"))   
                || (requestURL.contains(accessIdStr))
                || (requestURL.contains("curators"))	            
                 ) {
           %>
           <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=6" />
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
