<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%
  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);

  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>
<div id="page_contents">
<a name="top"></a>
<h1>AntWeb User Guide</h1>
<jsp:include page="about_nav.jsp" />
<div class="clear"></div>
<div class="page_divider"></div>
</div>
<!-- /div -->
<div id="page_data">
<div id="overview_data">

<h2>Welcome to the new AntWeb!</h2>
We here at AntWeb have been busy working on our newest (and most ambitious) version of the site - and there are lots of great new things! Which means there are lots of changes (don't worry, they're all for the best).

<p>We've put together a handy little guide to show you all the new features and enhancements to get you on your way. Click on any of the topics below to find out more.</p>

<p>We hope you like this new version of AntWeb - and <a href="mailto:antweb@calacademy.org">we'd love to hear your feedback!</a><br />
- The AntWeb Team

<div id="scroll"></div>

<h3 class="ue_link">&#187; Highlights</h3>
<div class="ue_content">
<b>&nbsp;&middot;&nbsp;</b>Images. Lots of them. And bigger. And all kinds of ways to view them.
<p><b>&nbsp;&middot;&nbsp;</b>An improved user interface that allows you to more easily navigate through our data.</p>
<p><b>&nbsp;&middot;&nbsp;</b>And easier access to tools.</p>
</div>

<h3 class="ue_link">&#187; Page Context</h3>
<div class="ue_content">
One of the biggest improvements we've made is to provide consistency in navigation. At the top of all pages is the context of the infomation you are viewing, as seen below. As you scroll down the page to view the data, this context locks to the top of the page, so you always have direct access to it.</p>
<p><img src="/image/ue_page_context.jpg"></p>

<p><b>From the top:</b></p>
<b>Current View</b> is the current section of AntWeb you are viewing (All AntWeb, Bolton World Catalog, or a given region, such as Florida). Depending on your current view, you can change toggle between views.

<p><b>To the far right</b> we have <b>Cite this page</b>, which makes it easier to cite AntWeb - both for print publications and website attribution. More details provided in the"Citing AntWeb" section below.</p>

<p><b>Next (here represented by "Genus: Monomorium")</b> we show you what item in the taxonomy you are looking at, and if the name is a valid one.</p>

<p><b>To the right of that</b> we have: <b>Overview | Images | List | Map</b>. "Overview" brings you to the description of the given item, "Images" brings you to a display of its images, "List" brings you to a list view of its children, and "Map" brings you to a large map view of its geographic distribution. More details provided in the "Browse Mode" section below.</p>

<p><b>View in AntCat</b> allows you to see the catalog and bibliography of the taxonomy of the given level in the taxonomy on <a href="http://www.antcat.org/" target="new">AntCat</a>.</p>

<p><b>Below that</b> we have <b>Classification</b>. This shows the taxonomic hiearchy of where you are (and provides a quick way to traverse back up the taxonomy).</p>

<p><b>Finally</b> we have <b>Tools</b>, allowing you to, depending on the level in the taxonomy, <b>Compare Images</b>, <b>Map Children</b>, <b>Create a Field Guide</b>, and <b>Download Data</b> (KML and tab-delimited).</p>

</div>

<h3 class="ue_link">&#187; Browse Mode</h3>
<div class="ue_content">
Directly related to the Page Context improvements is how you can switch your browsing mode while maintaining context. Say you were looking at the Overview for the genus Monomorium, and you wanted to see all of its images. Click on <b>Images</b> (next to <b>Overview</b>), and you'll do just that. And then, as you click deeper into the taxonomy, you'll stay in the image browsing mode. 

<p>But say you get down to the species level, and want to view the data as a list, without any images. Easy - just click on <b>List</b> (next to <b>Images</b>), and you'll see the same set of data, but as a list, and clicking deeper into the taxonomy will keep you in the list browsing mode.</p>

<p>And tied into the browsing mode is how the taxonomic hierarchy in <b>Classification</b> works - whichever mode you're in will be maintained as you traverse back up the hierarchy.</p>

<img src="/image/ue_overview.jpg" style="float:left; margin-right:12px; margin-bottom:12px;">
<img src="/image/ue_images.jpg" style="margin-bottom:12px;">
<div class="clear"></div>
<img src="/image/ue_list.jpg" style="float:left; margin-right:12px; margin-bottom:12px;">
<img src="/image/ue_map.jpg" style="margin-bottom:12px;">
</div>

<h3 class="ue_link">&#187; Image Thumbnail Options</h3>
<div class="ue_content">
We knew we were changing a lot when we introduced the new Image mode, and we wanted people to have as much control as possible on how they view images. You can change which view you browse by by selecting it from the <b>Browse Images by:</b> list on the far right of the page in Image mode (see example below). Your choices are <b>Head</b>, <b>Profile</b>, <b>Dorsal</b>, and <b>All</b>. Once you've selected that, that's the way you'll see all images on AntWeb (and you can change your view again at any time).
<p align="center"><img src="/image/ue_choose_view.jpg"></p>
</div>

<h3 class="ue_link">&#187; Image Slideshow</h3>
<div class="ue_content">
When you're navigating through the site in the Image mode, you can quickly view the head, profile, dorsal, and label images by clicking on the little slideshow icon (located on the upper right hand side of the image - see example below).
<p align="center"><img src="/image/ue_ratio_slideshow.jpg"></p>
</div>

<h3 class="ue_link">&#187; Citing AntWeb</h3>
<div class="ue_content">
We've made it easier to cite AntWeb by providing a <b>Cite this page</b> tool at the top right of each page. By clicking on that, you'll be able to copy the citation to use (find out more about citing AntWeb <a href="/citing_antweb.jsp">here</a>).
</div>

<h3 class="ue_link">&#187; Browsing Projects</h3>
<div class="ue_content">
We've heard from a lot of people that they didn't realize that they could browse through the various ant collections by region and project. Simply click on <b>Global</b>, <b>Regions</b>, or <b>Projects</b> next to the word <b>Browse</b> up there in the header, and you're off!
</div>

<h3 class="ue_link">&#187; Advanced Search</h3>
<div class="ue_content">
While not a lot has changed with our <b><a href="/advSearch.do">Advanced Search</a></b>, we just wanted to point out that it's infinitely more powerful than the generic search from the top of the page. Go on, click on in, and really focus what you're looking for. And it goes without saying that the tools mentioned above are there to apply to your search results.
</div>

<h3 class="ue_link">&#187; Homepage</h3>
<div class="ue_content">
And, finally, the <b><a href="/">Homepage</a></b>. From there you get a great snapshot view of AntWeb's statistics, recent images, access to the team and events, a mini-slideshow of all the subfamilies, and a great big picture of one of our curator's favorite ants each day of the week.
<p><img src="/image/ue_new_homepage.jpg"></p>
</div>

<div class="page_spacer"></div>

</div>
