<%@page import="java.util.Iterator"%>
<%@page import="java.util.List"%>
<%@page import="org.calacademy.antweb.home.*"%>
<%@page import="org.calacademy.antweb.util.*"%>
<%
	//A.log("autoCompleteData.jsp");

    //List<String> autoCompleteData = (List<String>) request.getAttribute("autoCompleteData", autoCompleteData);

	String text = request.getParameter("q");	
	if (text == null) {
	  out.println("Must enter ?q= parameter");
	  return;
	}
	
	//AutoCompleteDb autoCompleteDb = new AutoCompleteDb();
	//List<String> autoCompleteData = autoCompleteDb.getData(text);
	//List<String> autoCompleteData = autoCompleteDb.getDataOld(text);
    List<String> autoCompleteKeys = TaxonMgr.getPrettyTaxaNames(text);
    //List<String> autoCompleteData = request.getAttribute("autoCompleteData");

    if (autoCompleteKeys == null) {
      out.println("Click <a href='" + AntwebProps.getDomainApp() + "/uptime.do'>here</a>");
      return;
    }

	//A.log("AutoCompleteKeys.jsp autoCompleteKeys:" + autoCompleteKeys + " text:" + text);

	for (String next : autoCompleteKeys) {
		out.println(next);
	}
%>