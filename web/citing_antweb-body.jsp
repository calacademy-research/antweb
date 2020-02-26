<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>
<%
  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);

  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<div id="page_contents">
<h1>Citing AntWeb</h1>
<jsp:include page="about_nav.jsp" />
    <div class="clear"></div>
     <div class="page_divider"></div>
</div>
<!-- /div -->

<div id="page_data">
<div id="overview_data">

<p>AntWeb content is licensed under a <a href="https://creativecommons.org/licenses/by/3.0/" target="new">Creative Commons Attribution License</a>. We encourage use of AntWeb images.</p> 

<p>In print, each image must include attribution to its photographer, the specimen code of the image, and "from <a href="https://www.antweb.org/">www.antweb.org</a>" in each figure caption. 
<p>For websites, images must be clearly identified as coming from www.antweb.org, with a backward link to the respective source page. Photographer and other copyright information is provided on the big image page. Some photos and drawing belong to the indicated persons or organizations and have their own copyright statements. Photos and drawings with CCBY, CC-BY-NC or CC-BY-SA can be used without further permission, as long as guidelines above for attribution are followed.</p>

<p>We've made it easier to cite AntWeb by providing a "Cite this page" tool at the top right of each page. By clicking on that, you'll be able to copy the citation to use.</p>

<p><b>To cite AntWeb as a whole (example):</b></p>

<p><jsp:include page="/common/citeInclude.jsp" flush="true"/></p>

<b>To cite individual sections (example):</b>

<p><pre>Ward, P. S., editor (2013). AntWeb: Ants of California. Available from: https://www.antweb.org/california.jsp. Accessed <span class="today"></span></pre></p> 

<p>In both cases, the "Accessed" date should be the date you accessed the content, in "day Month Year" format.</p>

</div>
