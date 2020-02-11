<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.Date" %>
<% 
  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);

  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

&copy; <%= year %> California Academy of Sciences. All rights reserved. <br />

<br><a href="https://www.calacademy.org/privacy-policy">Privacy Policy</a>  -  <a href="https://www.calacademy.org/privacy-policy/#rights">Your California Privacy Rights</a>
  -  <a href="https://www.calacademy.org/terms-of-use">Terms of Use</a>

<p>AntWeb content is licensed under a <a href="http://creativecommons.org/licenses/by/4.0/" target="new">Creative Commons Attribution License</a>. We encourage use of AntWeb images. In print, each image must include attribution to its photographer, the specimen code of the image, and "from www.antweb.org" in each figure caption.  For websites, images must be clearly identified as coming from <a href="<%= AntwebProps.getDomainApp() %>">www.antweb.org</a>, with a backward link to the respective source page.  <a href="<%= AntwebProps.getDomainApp() %>/citing_antweb.jsp">See How to Cite AntWeb</a>.</p>

<p>Antweb is funded from private donations and from grants from the National Science Foundation, DEB-0344731, EF-0431330 and DEB-0842395. &nbsp;<%= new Date() %>.

<div id="calacademy">
AntWeb is hosted and supported by the <a href="http://www.calacademy.org/">California Academy of Sciences</a>.
</div>

<jsp:include page="/util/developerDetail.jsp" flush="true"/>

</p>


	  <!--
	  <rdf:RDF xmlns="http://web.resource.org/cc/"
		  xmlns:dc="http://purl.org/dc/elements/1.1/"
		  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
	  <Work rdf:about="">
	  <license rdf:resource="http://creativecommons.org/licenses/by-nc-sa/1.0/" />
	  </Work>
	  <License rdf:about="http://creativecommons.org/licenses/by-nc-sa/1.0/">
		 <requires rdf:resource="http://web.resource.org/cc/Attribution" />
		 <requires rdf:resource="http://web.resource.org/cc/ShareAlike" />
		 <permits rdf:resource="http://web.resource.org/cc/Reproduction" />
		 <permits rdf:resource="http://web.resource.org/cc/Distribution" />
		 <permits rdf:resource="http://web.resource.org/cc/DerivativeWorks" />
		 <prohibits rdf:resource="http://web.resource.org/cc/CommercialUse" />
		 <requires rdf:resource="http://web.resource.org/cc/Notice" />
	  </License>
	  </rdf:RDF>
	  -->
