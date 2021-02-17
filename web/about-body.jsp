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
<h1>About AntWeb</h1>
<jsp:include page="about_nav.jsp" />
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>

<!-- /div -->
<div id="page_data">
<div id="overview_data">

AntWeb is driven by specimen records. Images and and collection data are linked by specimen unique identifiers. Taxonomic names in AntWeb are managed by AntCat.org
Natural history information and field images  are linked directly to taxonomic names.
Distribution maps and field guides are generated automatically. All data
in AntWeb are downloadable by users. AntWeb also provides specimen-level
data, images, and natural history content to the Global Biodiversity
Information Facility (GBIF) and Wikipedia.
<p><p><p><span style="text-decoration:underline"><b>Examples:</b></span></p>
<p>Species pages: <a href="https://www.antweb.org/description.do?rank=species&amp;name=apache&amp;genus=pseudomyrmex&amp;project=worldants"><span><span style="text-decoration:underline">https://www.antweb.org/description.do?rank=species&amp;name=apache&amp;genus=pseudomyrmex&amp;project=worldants</span></span></a></p>
<p>Regional lists: <a href="https://www.antweb.org/page.do?name=california"><span><span style="text-decoration:underline">https://www.antweb.org/page.do?name=california</span></span></a></p>
<p>Projects: <a href="https://www.antweb.org/page.do?name=floridakeys"><span><span style="text-decoration:underline">https://www.antweb.org/page.do?name=floridakeys</span></span></a></p>
<p>Field guides: <a href="https://www.antweb.org/mapComparison.do?rank=genus&amp;name=adetomyrma&amp;project=madants"><span><span style="text-decoration:underline">https://www.antweb.org/mapComparison.do?rank=genus&amp;name=adetomyrma&amp;project=madants</span></span></a></p>

<br><span style="text-decoration:underline"><b>How to Reuse Content and Cite AntWeb:</b></span></p>

<p>AntWeb content is licensed under a <a href="http://creativecommons.org/licenses/by/3.0/" target="new">Creative Commons Attribution License</a>. We encourage use of AntWeb images.</p>

<p>In print, each image must include attribution, the specimen code of the image, to its photographer and "from <a href="https://www.antweb.org">www.antweb.org</a>" in each figure caption.</p>

<p>For websites, images must be clearly identified as coming from www.antweb.org, with a backward link to the respective source page. Photographer and other copyright information is provided on the big image page. Some photos and drawing belong to the indicated persons or organizations and have their own copyright statements. Photos and drawings with CCBY, CC-BY-NC or CC-BY-SA can be used without further permission, as long as guidelines above for attribution are followed.</p>

<p>We've made it easier to cite AntWeb by providing a "Cite this page" tool at the top right of each page. By clicking on that, you'll be able to copy the citation to use.</p>

<p><b>To cite AntWeb as a whole (example):</b></p>

<p><jsp:include page="/common/citeInclude.jsp" flush="true"/></p>

 the "Accessed" date should be the date you accessed the content, in "day Month Year" format.</p>

<br><span style="text-decoration:underline"><b>Taxonomic names in Antweb</b></span></p>
<p>Taxonomic names in Antweb are provided from two
sources: AntCat.org for valid names and names used for specimen records. You can filter from viewing just valid names or viewing valid names and morphospecies. </p>
<p> </p>
<p>Because identifications and images are linked to specimens
it is important to submit the unique specimen identifier along with comments
on images or specimens. <a name="0.1__GoBack"></a></p>

</div>
</div>
