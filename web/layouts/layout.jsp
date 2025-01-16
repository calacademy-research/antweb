<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Project" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<% String theTarget = HttpUtil.getTarget(request); %>

<!doctype html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:fb="http://ogp.me/ns/fb#">

<!-- Must be used by including basicLayout.jsp or maybe secureLayout.jsp -->
<!--[if lt IE 9]><html lang="en" class="ie"><![endif]-->
<!--[if gte IE 9]><!--><html lang="en"><!--<![endif]-->
<head>
<title><tiles:getAsString name="title" /></title>
<html:base/>
<meta charset="utf-8"/>
<tiles:getAsString name="meta" ignore="true" /> 
<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1"/>

<% if (HttpUtil.isMobile(request)) { %>
  <meta name="viewport" content="width=device-width, initial-scale=1"/> <!--  minimum-scale=1.0, maximum-scale=1.0,  -->
<% } else { %>
  <meta name="viewport" content="width=device-width, minimum-scale=1.0, maximum-scale=1.0, initial-scale=1"/> <!--   -->
<% } %>

<meta name="apple-mobile-web-app-capable" content="yes" />

<meta name="referrer" content="strict-origin-when-cross-origin" />

<% 
   String ogTitle = (String) request.getAttribute("ogTitle"); // OpenGraphMgr.getOGTitle();
   if (ogTitle == null) ogTitle = "Antweb";
   String ogImage = (String) request.getAttribute("ogImage"); // OpenGraphMgr.getOGImage();
   if (ogImage == null) ogImage = "/image/new_antweb_logo.png";
   String ogDesc = (String) request.getAttribute("ogDesc"); // OpenGraphMgr.getOGDesc();
   
   //if (LoginMgr.isDeveloper(request)) AntwebUtil.log("layout.jsp ogTitle:" + ogTitle + " ogImage:" + ogImage + " ogDesc:" + ogDesc);
%>
<meta property="og:image" content="<%= AntwebProps.getImgDomainApp() %><%= ogImage %>" />
 <!-- was: /image/new_antweb_logo.png" was: http://www.antweb.org -->
<meta property="og:title" content="<%= ogTitle %>" />

<% if (ogDesc != null) { %>
<meta property="og:description" content="<%= ogDesc %>" />
<% } %>
<%
  //  if (AntwebProps.isDevMode()) {
  // If we go to a page, using projectName, and that project has a displayKey, add canonical tag to display key.
  // functionality removed. Temporarily to be found in untitled 63
%>


<!-- See:
  layout.jsp antweb.css antweb-defs.jsp
  -->
<% if (false) { %>
  <link rel="stylesheet" href="<%= AntwebProps.getDomainApp() %>/common/hamburgers-master/dist/hamburgers.css">
<% } %>


<link rel="image_src" type="image/png" href="<%= domainApp %>/image/new_antweb_logo.png" />
<link rel="shortcut icon" href="<%= domainApp %>/image/favicon.ico" />
<link rel="stylesheet" href="<%= domainApp %>/common/base.css"/>

<% 
   
   // This doesn't actually get called by mobile requests. See layoutMobile.css
   //String tTarget = HttpUtil.getTarget(request);
   //if (tTarget != null && (tTarget.contains("testMobile.do") || tTarget.contains("mobile.do"))) AntwebUtil.log("layout.jsp mobile:" + HttpUtil.isMobile(request));

   if (HttpUtil.isMobile(request)) { %>
    <link rel="stylesheet" href="<%= domainApp %>/common/antwebMobile.css?v1.0"/>
<% } else { %>
    <link rel="stylesheet" href="<%= domainApp %>/common/antweb.css?v1.0"/>
<% } %>

<% if ((theTarget.contains("getSearchList.do")) || (theTarget.contains("getSpecimenList.do"))) { %>
<link rel="stylesheet" href="<%= domainApp %>/common/clear_style.css"/>
<% } %>
<% if (theTarget.contains("description.do")) { %>
<link rel="stylesheet" href="<%= domainApp %>/common/plazi.css"/>
<% } %>

<%
 if (HttpUtil.isOnline()) { %>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js"></script>
<% } %>

<script type="text/javascript" src="<%= domainApp %>/common/jquery.tools.min.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/jshashtable-2.1.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/jquery.numberformatter-1.2.3.jsmin.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/jquery.cookie.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/antweb.jquery.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/purl.js"></script>
<script type="text/javascript" src="<%= domainApp %>/common/jquery.jqURL.js"></script>
<% if ((request.getRequestURI().contains("taxonPage.jsp")) 
    || (request.getRequestURI().contains("dynamicMap.jsp")) 
    || (request.getRequestURI().contains("specimen.jsp")))  { %>
<% } else { %>
<script type="text/javascript" src="<%= domainApp %>/common/specimen_list.js"></script>
<% } 

   String requestUri = HttpUtil.getRequestURI(request);
   Object o = request.getParameterValues("rank");
   String rank = "";
   if (o != null) rank = ((String[]) o)[0];
%>
<script>
if (!$.cookie('ftue')) {
    $.cookie('ftue', '1', { path: '/', expires: 365 });
} else {
    $.cookie('ftue');
}
$(function() {
<% /* 
    if ($.cookie('ftue') != 2) {
        $('#ftue_container').show();
    }
*/ %>
    $('#show_me').click(function(e) {
        e.preventDefault();
        $('#ftue_container').hide();
        $.cookie('ftue', '2', { path: '/', expires: 10*365 });
        window.location = '/user_guide.jsp';
    });
    $('#no_thanks').click(function(e) {
        e.preventDefault();
        $('#ftue_container').hide();
        $.cookie('ftue', '2', { path: '/', expires: 10*365 });
    });
});

if (!$.cookie('browse')) {
    $.cookie('browse', '1', { path: '/', expires: 365 });
} else {
    $.cookie('browse');
}
$(function() {
    if ($.cookie('browse') != 2) {
        $('#browse_hint').show();
    }
    $('#close_browse_hint').click(function(e) {
        e.preventDefault();
        $('#browse_hint').hide();
        $.cookie('browse', '2', { path: '/', expires: 10*365 });
    });
});

if ($.cookie('thumbs')) {
    var which_thumbs = $("#which_thumbs");
    if (which_thumbs) {
        var which_thumb = $.cookie('thumbs');
        if (which_thumb == 'h') {
            $(function() {
                $("#which_thumbs").html("Head");
        	});
        } else if (which_thumb == 'p') {
            $(function() {
                $("#which_thumbs").html("Profile");
            });
        } else if (which_thumb == 'd') {
            $(function() {
                $("#which_thumbs").html("Dorsal");
            });
        } else if (which_thumb == 'a') {
            $(function() {
                $("#which_thumbs").html("All");
            });
        }
    }
} else {
    $.cookie('thumbs', 'h', { path: '/', expires: 365 });
}
<% if (request.getParameter("sortBy") !=null) {

// was (but then added sort: String sortByExtras = HttpUtil.getQueryStringNoQuestionMark(request);
    String targetMinusSort = HttpUtil.getTargetMinusParam(request, "sortOrder");
    String sortByExtras = HttpUtil.getAfterQuestionMark(targetMinusSort);
%>

<!-- layout.jsp A dubious piece of javascript code. This logic should be in a jsp not as javascript. It is for the sort
     function on the browse.do specimen page and advanced search results. Also see layoutMobile.jsp -->

$(function() {
    var full_query = '<%= sortByExtras %>';
    var regex = '[?&]sortBy=([^&]*)'; 
    var regex2 = '[?&]t=([^&]*)'; 
    var re = new RegExp(regex, "g");
    var re2 = new RegExp(regex2, "g");
    var clear_it = ''; 
    var cleaned_query = ''; 
    cleaned_query = full_query.replace(re,clear_it);
    cleaned_query = cleaned_query.replace(re2,clear_it);
    $("#ns_sortby_extras").val(cleaned_query);
});
<% } %>
<% 
    if ((theTarget.contains("getComparison.do"))
       || (theTarget.contains("mapComparison.do"))
       || (theTarget.contains("fieldGuide.do"))
    ) { 
%>
<%     if (request.getParameter("pr") !=null) { %>
        var clean_map = $("a.clean_url.map");
        $(function() {
            var clean_overview = $("a.clean_url.overview").attr("href");
            var edit_overview = clean_overview.replace(/&pr=.*/, '');
            $("a.clean_url.overview").attr("href", edit_overview);

            var clean_images = $("a.clean_url.images").attr("href");
            var edit_images = clean_images.replace(/&pr=.*/, '');
            $("a.clean_url.images").attr("href", edit_images);

            var clean_list = $("a.clean_url.list").attr("href");
            var edit_list = clean_list.replace(/&pr=.*/, '');
            $("a.clean_url.list").attr("href", edit_list);

            var clean_return = $("#thumb_toggle a").attr("href");
            var edit_return = clean_return.replace(/&pr=.*/, '');
            $("#thumb_toggle a").attr("href", edit_return);

            if (clean_map) {
                var clean_map = $("a.clean_url.map").attr("href");
                var edit_map = clean_map.replace(/&pr=.*/, '');
                $("a.clean_url.map").attr("href", edit_map);
            }
});
<%     } %>
<% } %>
</script>
</head>
<body>
<% /*
<div id="ftue_container">
    <div class="ftue_screen"></div>
    <div class="disable"></div>
    <div id="ftue_overlay">
            <div class="ftue_gradient"></div>
            <div class="ftue_contents">
                <h1>Welcome to the new AntWeb!</h1>
                <div class="page_divider"></div>
                We here at AntWeb have been busy working on our newest (and most ambitious) version of the site - and there are lots of great new things! Which means there are lots of changes (don't worry, they're all for the best). 

                <p>And we've put together a handy little guide to show you all the new features and enhancements - why don't you have a quick look to check out all the new features and enhancements?</p>
                <div align="center" style="margin-top:18px; margin-bottom:12px;">
                    <input id="show_me" class="submit" value="Show me!"> <span id="no_thanks">No thanks</span>
                </div>
            <div class="clear"></div>
            </div>
    </div>
</div>
*/ %>

<tiles:insert attribute="academyHeader"/>
<tiles:insert attribute="siteNav"/>

<%
    // Determine if we should block all users (to prevent bot traffic bringing down server).
    boolean blockUnLoggedInUsers = false;
    String reqPage = HttpUtil.getTarget(request);
    if ((reqPage != null) && !(reqPage.contains("login") || !(reqPage.contains("index")))) {    // index might return null;

      if (!LoginMgr.isLoggedIn(request)) {
          blockUnLoggedInUsers = true;
        } else {
          AntwebUtil.log("Logged in Page:" + reqPage);
      }
    } else {
      AntwebUtil.log("Page is:" + reqPage);
    }

    if (blockUnLoggedInUsers) {
          out.println("<br><h2>Due to current Bot traffic, we are supporting logged in users. Login: <a href=" +  AntwebProps.getDomainApp() + "/login.do>Login</a></h2>");
    } else { %>

        <tiles:insert attribute="body-content"/>

 <% } %>


<div class="clear"></div>

<%  if ((request.getRequestURI().indexOf("index.jsp") != -1) || (request.getRequestURI().indexOf(".jsp") == -1)) { %>
<div id="footer" class="home lower_home_gradient">
<% } else { %>
<div id="footer">
<% } %>
<tiles:insert attribute="copyright"/>
</div>

<% if (HttpUtil.isOnline()) { %>

<script type="text/javascript">
var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
</script>
<script type="text/javascript">
var pageTracker = _gat._getTracker("UA-4802256-1");
pageTracker._initData();
pageTracker._trackPageview();
</script>

<% } %>

<%  if ((request.getRequestURI().indexOf("index.jsp") != -1) || (request.getRequestURI().indexOf(".jsp") == -1)) { %>
</div>
<% } %>

</div>
</body>
</html>
