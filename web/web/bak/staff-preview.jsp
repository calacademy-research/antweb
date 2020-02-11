<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">	
<tiles:put name="title" value="AntWeb Staff" />	
<tiles:put name="body-content" type="string">	   
<div class="left">	   <h1>AntWeb Staff</h1>	   	   
<hr>
 
<p><img  src="/homepage/brian.jpg" alt="Brian Fisher"><br>
<strong><a href="mailto:bfisher@calacademy.org">Brian Fisher</a>, AntWeb Project Leader</strong><br>
Curator of Entomology and expert on African and Malagasy ants.

</p><p><img  src="/homepage/Michelev3.jpg" alt="Michele Esposito"><br>
<strong><a href="mailto:mesposito@calacademy.org">Michele Esposito</a>, "Data Tsar"</strong><br>.

</p><p><img  src="/homepage/Erin.jpg" alt="Erin Prado"><br>
<strong><a href="mailto:eprado@calacademy.org">Erin Prado</a>, Image Specialist</strong><br> .
 
</p><p><img  src="/homepage/phil.jpg" alt="Phil Ward"><br>
<strong><a href="mailto:psward@ucdavis.edu">Phil Ward</a>, Professor, Entomology, University of California, Davis</strong><br>
Ant systematist and project leader for Ants of California. 
 
</p><p><img  class="border " src="/homepage/michelle.gif" alt="Michelle Koo" border="0"><br>
<a href="mailto:mkoo@calacademy.org"><strong>Michelle Koo</strong></a><strong>, GIS consultant</strong><br>
Using ESRI GIS software, geospatial data and ant specimens make great maps!
 
</p><p><img  src="/homepage/thau.jpg" alt="David Thau"><br>
<strong><a href="mailto:thau@learningsite.com">David Thau</a>, Software Engineer</strong><br>
Thau developed the AntWeb software and technical architecture.

</p><img  src="/homepage/mark.jpg" style="width: 225px; " alt="&gt;&lt;br&gt;&lt;span&gt;&nbsp;&lt;b&gt;&lt;a href=" mailto:mjohnson@calacademy.org"="&gt;Mark Johnson, Software Engineer&lt;br&gt;&nbsp;Mark is the principal software engineer developing and maintaining Antweb.&lt;span&gt;&nbsp;&lt;/span&gt;&lt;p&gt;&lt;img src=" homepage="" lloyd%20and%20pheidole%20militicida%20crop.jpg"=""><br><a href="mailto:mjohnson@calacademy.org" style="font-weight: bold; ">Mark Johnson</a><strong>,</strong><strong style="font-weight: bold; ">Software Engineer</strong><br>Mark is the principal software engineer developing and maintaining Antweb.<a href="mailto:ants@gru.net" style="font-weight: bold; "><br></a><a href="mailto:ants@gru.net" style="font-weight: bold; "><br><meta http-equiv="Content-Type" content="text/html;charset=UTF-8"><img  src="http://www.antweb.org/staff/lloydAndPheidoleMiliticidaCrop.jpg" alt="Lloyd Davis"><br><span>&nbsp;</span>Lloyd Davis</a><strong>, Florida Ant Mafia </strong><br> Lloyd is an expert on North American ants and oversees content for the NA region.
 
<p></p><p><img  src="/homepage/James250.jpg" alt="James Trager"><br>
<strong><a href="mailto:James.Trager@mobot.org">James Trager</a>, Missouri Botanical Garden </strong><br> Ant systematist and project leader for Ants of Illinois and Missouri.

</p><p><img  src="/homepage/jack250.jpg" alt="Jack Longino"><br>
<strong><a href="mailto:longinoj@evergreen.edu">Jack Longino</a>, Evergreen State College</strong><br> Ant systematist and project leader for Ants of Central America.
</p>	   	   
<%	     
        Login accessLogin = LoginMgr.getAccessLogin(request);	      
        if ((accessLogin != null) && (accessLogin.isAdmin() || (accessLogin.getProjects().contains("homepage")))) {	   
%>

	   	   <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=9" />	   		<input type="submit" value="Edit Page">	   	</form>	   	
	   	   <form method="POST" action="<%= domainApp %>/ancPageSave.do">	   		<input type="submit" value="Save Page">	   	</form>	   		   	
<% } %>
           </div>	
</tiles:put></tiles:insert>