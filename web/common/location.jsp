
<%@ page import="org.calacademy.antweb.util.*" %>

 <% //if (HttpUtil.isIphone(request)) return;

%>

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



              // The double call appears to be happening in the jsp includes. Before taxonomicPage.jsp but after the jsp are called.
              if (false && HttpUtil.getTarget(request).contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request))) {
                  A.log("EXIT()");
                  return;
              }


%>
