<!--This defines the basic layout of the antweb site. It defines five main tiles elements:
    * title: the page title
    * adminHeader: the header for the page, which covers the first row.
    * body-content: the main body content that appears in the main portion of the page, below the header to the right of the menu bar
    
    All space filling elements except the body-content appear in the web/common folder.
-->
    

<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% Utility util = new Utility(); %>
<% 
//String domainApp = (new Utility()).getDomainApp(); 
// domainApp is now defined in the including class.  adminLayout.jsp or secureAdminLayout.jsp
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html:html locale="true">
<head>
<meta charset="utf-8"/>
<title><tiles:getAsString name="title" /></title>
<!-- tiles:getAsString name="meta" / --> 
<html:base/>
<link rel="shortcut icon" href="<%= AntwebProps.getDomainApp() %>/image/favicon.ico" >
</head>

<link href="<%= domainApp %>/common/antweb_style.css" rel="stylesheet" type="text/css">
<link href="<%= domainApp %>/common/admin_style.css" rel="stylesheet" type="text/css">

<script type="text/javascript">
function swapClass(section) {
var aP=document.getElementsByTagName('p');
for(var i=0; i<aP.length; i++) {
    if(aP[i].className==='thumbs_wrapper') {
            aP[i].className='hidden';
            toggleIframe(section);
        } else {
            toggleIframe(section);
        }
    }
}

function toggleIframe(section_name) {
  var section = document.getElementById(section_name);
  if (section.style.display == "") {
    section.style.display = "none";
  } else {
    section.style.display = "";
    section.className = "thumbs_wrapper";
  }
}

function toggleSection(section_name) {
  var section = document.getElementById(section_name);
  if (section.style.display == "") {
    section.style.display = "none";
  } else {
    section.style.display = "";
  }
}

function get_img_name(the_name,to_where) {
    var name=the_name;
    document.getElementById(to_where).value=name;
}
</script>

<script type="text/javascript" src="<%= domainApp %>/ckeditor/ckeditor.js"></script>

<!-- end adminLayout head -->
</head>
<body leftmargin=0 topmargin=0 marginheight=0 marginwidth=0>

<div align="center">
<div id="admin_layout">

<tiles:insert attribute="adminHeader"/>

<tiles:insert attribute="body-content"/>

<!-- Footer -->
<div class="clear"></div>

<br />
<br />
<br />
<br />
<br />
<br />
<br />
<br />
<br />

</div>
</div>

<script type="text/javascript">
var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
</script>
<script type="text/javascript">
var pageTracker = _gat._getTracker("UA-4802256-1");
pageTracker._initData();
pageTracker._trackPageview();
</script>
</body>
</html:html>
