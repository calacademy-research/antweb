<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
          
<!-- 
Found in search/advancedSearch-body.jsp.  
Should have defined: statusSetDefault. 
-->

  <%
  if (true) {
     //Login accessLoginSSD = LoginMgr.getAccessLogin(request);
     
     //A.log("statusSetSelectDisplay() valid:" + advancedSearchForm.getValidNames());
     String target = HttpUtil.getTargetMinusParam(request, "statusSet");
  %>
Statuses: <select name="statusSet" id="statusSet" class="input_150" >

    <% for (String statusSet : StatusSet.getStatusSets()) {
         if (accessLogin == null && !( statusSet.equals(StatusSet.ALL) || statusSet.equals(StatusSet.VALID) ) ) continue;
         String selected = "";
         String lookFor = null;
         if (statusSetDefault != null) {
           lookFor = statusSetDefault;
         } else {
           if ((accessLogin != null) && (accessLogin.getId() == 23)) {
             lookFor = StatusSet.COMPLETE; // Esposito Exception: Michele always wants complete
           } else {
             lookFor = StatusSet.ALL;
           }
         }
         if (lookFor.equals(statusSet)) selected = "selected";
         //if (statusSet.equals(StatusSet.VALID)) statusSet = "Valid (with fossil)";
         //A.log("statusSetSelectDisplay() statusSet:" + statusSet);
     %>
         &nbsp;&nbsp;&nbsp;<option value="<%= statusSet %>" <%= selected %>><%= (new Formatter()).capitalizeFirstLetter(statusSet) %></option>
    <% } %>

    </select>
    
    <% } %>
