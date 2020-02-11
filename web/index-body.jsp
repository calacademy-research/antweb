<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.util.*" %>

<logic:present parameter="project">
   <% session.removeAttribute("project"); %>
</logic:present>

<%
    A.log("index-body.jsp target:" + HttpUtil.getTarget(request));

    String domainApp = org.calacademy.antweb.util.AntwebProps.getDomainApp(); 

   // if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
 %>

<logic:present name="mode">
<div class="msg">
    <div class="msg_alert"></div>
    <div class="msg_type">Preview</div>
    <div class="msg_pipe"></div>
    <div class="msg_copy"> This is what the Homepage will look like. If it is correct, click <b>Save</b> below - if you want to make changes, click <b>Edit</b> below.</div>
    <div class="clear"></div>
</div>
</logic:present>

<div id="page_data" class="home">

<div id="home_upper">
    <div id="stats">
        <b>Current Statistics</b><br />
        <%= FileUtil.getContent("web/genInc/statistics.jsp") %>
        <span class="right"><a href="<%= domainApp %>/statsPage.do">More &#187;</a></span> 
    </div>
    <div id="mission">
        <p>AntWeb is the world's largest online database of images, 
specimen records, and natural history information on ants. It is community driven and open to contribution from anyone with specimen records, natural history comments, or images. </p>
<p> </p>
<p>Our mission is to publish for the scientific community 
high quality images of all the world&#39;s ant species.  AntWeb 
provides tools for submitting images, specimen records, annotating species 
pages, and managing regional species lists. <a href="<%= domainApp %>/about.do">More...</a></p>
    </div>
    <div id="recent_activity">
        <b>Recent Images:</b><br />
        <%= FileUtil.getContent("web/genInc/recentImages_gen_inc.jsp") %>
        <span class="right"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30">More &#187;</a></span>
        <div class="clear"></div>
        <b>Recent Edits:</b><br />
        <%= FileUtil.getContent("web/genInc/recentDescEdits.jsp") %>
        <div class="clear"></div>
    </div>
    <div class="clear"></div>
    <div id="background_attr">Background image: Specimen: <a id="specimen_link" href=""><span class="uppercase" id="the_specimen"></span></a> Species: <a id="species_link" href=""><span id="the_species"></span></a></div>
    <div class="clear"></div>
    <div id="slideshow">
        <!-- we don't use fileUtils.getContent() here because this file contains imports and stuff... -->
        <jsp:include page="/common/slideshow_homepage.jsp"/>
    </div>

<script>
$(document).ready(function() {
var root = $("#autoscroll").scrollable({speed: 700, circular: true}).autoscroll({ autoplay: false});
window.api = root.data("scrollable");
});
</script>

    <div class="clear"></div>

    </div>
    <div class="clear"></div>
</div>

<div id="home_lower">
    <div class="lower_home_gradient">

<div id="contributor_container">
    <div id="contributors">
<h3><u>Antweb Community</u></h3>
       <br>&nbsp;&nbsp;&nbsp;<b><a href="<%= AntwebProps.getDomainApp() %>/groups.do?orderBy=specimens">Specimen Contributors</a></b><br />
       <br>&nbsp;&nbsp;&nbsp;<b><a href="<%= AntwebProps.getDomainApp() %>/curators.do?sortBy=specimenUploadCount">Curators</a></b><br />
       <br>&nbsp;&nbsp;&nbsp;<b><b><a href="<%= AntwebProps.getDomainApp() %>/museums.do">Museums</a></b><br />
       <br>&nbsp;&nbsp;&nbsp;<b><a href="<%= AntwebProps.getDomainApp() %>/artists.do">Artists (Photographers)</a></b><br />
    </div>


    <div id="get_involved">
        <b><a href="/staff.do">Meet the rest of the team!</a></b><br />
            <p>Many curators already contribute to AntWeb - would you like to join us?  Curators can edit the home page of the geographic section they curate, upload specimen data and authority files, and control a number of other aspects of their project. Learn how to <a href=http://www.antweb.org/documentation.do> submit data to Antweb</a>.

<p>If you would like to join us, contact us at - <a href="mailto:antweb@calacademy.org">antweb@calacademy.org</a>.
       </div>
   <div class="clear"></div>
</div>

<div id="promo_container">
    <div class="promo">
        <b><a href=http://www.calacademy.org/scientists/ant-course>Enroll in Ant Course</a></b><br />
        Ant Course is a 10-day workshop designed for systematists, ecologists, behaviorists, conservation biologists, whose research require a greater understanding of ant taxonomy and field research techniques. Emphasis is on the evolution, classification and identification of ant genera.  <a href=http://www.calacademy.org/scientists/ant-course>Read More &#187;</a>

    </div>
    <div class="promo middle">
        <b><a href=/page.do?name=bayarea>Bay Area Ants Survey</a></b><br />
        The 11-county Bay Area is home to more 100 types of ant species. Visit AntWeb's <a href=/page.do?name=bayarea>Bay Area Ant Survey</a> to find out how to become a Citizen Naturalist and help discover and learn about the ants in your backyard, schools and local Bay Area parks.
        </div>
    <div class="promo">
        <b><a href=/worldants.do>World Ant Collections</a></b><br />
        We at AntWeb have been busy taking photos of many of the world's great ant collections.  Visit the AntWeb <a href=/worldants.do>World Ant Collection</a> to see the collections and get information about where they are housed.  
    </div>
    <div class="clear"></div>
</div>

</div>

<logic:present name="mode">
<div class="clear"></div>
<br />
<br />
<div class="msg">
    <div class="msg_actions" align="center">
    </div>
</div>
<form action="saveHomePage.do" method="post">
<input border="0" type="image" src="<%= domainApp %>/image/orange_done.gif" width="98" height="36" value="Save This Project">
<a href="<%= domainApp %>/editHomePage.do"><img border=0" src="<%= domainApp %>/image/grey_edit.gif" width="98" height="36"</a>
</form>
</logic:present>

    </div>
</div>
