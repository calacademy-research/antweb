<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.Group" %>
<%@ page import = "org.calacademy.antweb.Login" %>
<% String antwebRelease = "0"; %>

 <% //if (HttpUtil.isIphone(request)) return; %>

 <% if (!AntwebProps.isDevMode() && !AntwebProps.isStageMode()) {
      if (!AntwebProps.isProtocolSecure()) { %>
	    <script>
	    if (location.protocol != 'https:') {
		  location.href = 'https:' + window.location.href.substring(window.location.protocol.length);
		  //location.href = 'https://www.antweb.org';
          //console.log("academyHeader.jsp location.href:" + location.href);
	    }
	    // This does not seem to work. OK, I think.
        if (location.href.includes("https://antweb.org")) {
          location.href= "https://www.antweb.org" + location.href.substring(17);
          //console.log("academyHeader.jsp location.href:" + location.href);
        }
        //console.log("academyHeader.jsp protocolLength:" + window.location.protocol.length + " location.href:" + location.href);
	    </script>

   <% } else { %>
	    <script>
        if (location.href.includes("https://antweb.org")) {
          location.href= "https://www.antweb.org" + location.href.substring(18);
          //console.log("academyHeader.jsp location.href:" + location.href);
        }
        //console.log("academyHeader.jsp protocolLength:" + window.location.protocol.length + " location.href:" + location.href);
	    </script>
   <% }
    }

    if (!AntwebMgr.isPopulated()) { %>
      <b>Server Initializing</b>
<%      return;
    }

    String contextPath = request.getContextPath();
    Utility util = new Utility();
    String domainApp = (new Utility()).getDomainApp();

    String logo = AntwebProps.getDomainApp() + "/image/1x1.gif";
    logo = AntwebProps.getDomainApp() + "/image/AntWeb.png";

    int logoWidth = 0;    

    int n = 1;
    if (true || AntwebProps.isDevMode()) {
      if (n == 1) {
        logo = AntwebProps.getDomainApp() + "/image/antwebHeadIcon1.png";    
        logoWidth = 172;
      }      
      if (n == 2) {
        logo = AntwebProps.getDomainApp() + "/image/antwebHeadIcon2.png";    
        logoWidth = 128;
      }
      if (n == 3) {
        logo = AntwebProps.getDomainApp() + "/image/antwebHeadIcon3.png";    
        logoWidth = 125;
      }
      if (n == 4) {
        logo = AntwebProps.getDomainApp() + "/image/antwebHeadIcon4.png";    
        logoWidth = 125;
      }
    }
    //AntwebUtil.log("academyHeader.jsp logo:" + logo);
/*    */
%>

<%@include file="/documentation/releaseNum.jsp" %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
    //A.log("academyHeader.jsp accessGroup:" + accessGroup);

    if (accessLogin != null && accessLogin.isAdmin()) { %>
      <%@include file="/common/adminAlertInc.jsp" %>    
 <% } %>

<div id="masthead">

    <div class="header">
        <div id="logo"><a href="<%= AntwebProps.getDomainApp() %>/"><img src="<%= logo %>" width='<%= logoWidth %>' title="[AntWeb]"></a></div>

        <div id="version">
            <a href="<%= domainApp %>/browse.do?name=formicidae&rank=family&project=allantwebants">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;v</a><a href="<%= domainApp %>/documentation/releaseNotes.jsp"><%=antwebRelease %></a>
        </div>
        
 <% if (!HttpUtil.isMobile(request)) { %>             
        <div id="site_links">
			<ul>
				<li><a href="<%= domainApp %>/about.do">About</a></li>
				<li><a href="<%= domainApp %>/documentation.do">Participate</a></li>
				<li><a href="<%= domainApp %>/antblog/index.html" target="new">AntBlog</a></li>
				<li><a href="<%= domainApp %>/antShare.do" target="new">AntShare</a></li>  
				<li><a href="<%= domainApp %>/press.do">Press</a></li>
				<li><a href="<%= domainApp %>/favs.do">Favorites</a><img src='<%= AntwebProps.getDomainApp() %>/image/yellow-star-md.png' width=10/></li>
				<li><a href="<%= domainApp %>/contact.do">Contact</a></li>
				<li><a href="http://www.antcat.org/" target="new">AntCat</a></li>
				<li><a href="<%= domainApp %>/api.do" target="new">API</a></li>
				<li><a href="https://www.facebook.com/Antweb.org" target="new"><img src="<%= AntwebProps.getDomainApp() %>/image/fb.png" title="Follow AntWeb on Facebook"></a>&nbsp;&nbsp;&nbsp;</li>
			</ul>
        </div>

        <div id="login">    
            <%@ include file="/common/loginPanel.jsp" %>    
        </div> <!-- login -->
<% } %>

    </div> <!-- header -->

</div> <!-- masthead -->



