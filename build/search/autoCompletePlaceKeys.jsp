<%@page import="java.util.Iterator"%>
<%@page import="java.util.List"%>
<%@page import="org.calacademy.antweb.home.*"%>
<%@page import="org.calacademy.antweb.util.*"%>
<%
	//A.log("autoCompletePlaceKeys.jsp");

/* UFT stuff, not working found here, last line of file for testing, and in placeNameSearchBox.jsp
  < %@page pageEncoding="UTF-8"% >
*/

    //HttpUtil.setUtf8(request, response);

	String text = request.getParameter("q");	
	if (text == null) {
	  out.println("Must enter ?q= parameter");
	  return;
	}
	
    List<String> autoCompletePlaceKeys = GeolocaleMgr.getPlaceNames(text);

    if (autoCompletePlaceKeys == null) {
      out.println("Click <a href='" + AntwebProps.getDomainApp() + "/uptime.do'>here</a>");
      return;
    }

	//A.log("AutoCompletePlaceKeys.jsp autoCompletePlaceKeys:" + autoCompletePlaceKeys + " text:" + text);

	for (String next : autoCompletePlaceKeys) {
		out.println(next);
	}
	
	//out.println("Al Maḩwīt");
%>