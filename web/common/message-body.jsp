<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>


<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>



<div class="wide_left">

<%

    //AntwebUtil.log("execute() name:" + TaxonMgr.getPrettyTaxaNames(taxonName) + " species:" + species);

    //DateUtil.runTests();

    //A.log("message-body.jsp andCriteria:" + StatusSet.getAndCriteria(Project.ALLANTWEBANTS));
    //A.log("message-body.jsp andCriteria:" + StatusSet.getAndCriteria(Project.WORLDANTS));

    // Test this way: http://localhost/antweb/util.do?action=testMessage

    String message = (String) request.getAttribute("message"); 
    //A.log("message-body.jsp message:" + message);
    LogMgr.appendDataLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));
%>
<br>
<br>
<b><%= message %></b>
</div>

<%
if (AntwebProps.isDevMode() || LoginMgr.isDeveloper(request)) {
  if (true) {
    //String str = "amblyoponinaemystrium mysticum-a";
    // String str = "amblyoponinaemystrium mysticum-0?";
    String str = "formicinaegaesomyrmex hörnesii";
    boolean hasSC = false;

    if (false && AntwebProps.isDevMode()) {
       //java.util.Map<String, String> headers = HttpUtil.getHeadersInfo(request);
       
		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
            AntwebUtil.log("message-body.jsp key:" + key + " value:" + value);
		}
		       
/*    
      hasSC = Formatter.hasSpecialCharacter(str);
    } else {
      String extras = "";
      extras = "äáëéìöü";
      Pattern p = Pattern.compile("[^A-Za-z0-9 " + extras + "()/_.-]");
      Matcher m = p.matcher(str);
      hasSC = m.find();   
*/
    }    
    //out.println("message-body.jsp hasSC:" + hasSC + " str:" + str);
  } 
}
%>
