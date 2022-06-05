<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<!-- statusesDisplay.jsp  Found in showBrowse-body.jsp, taxonomicPage-body.jsp, and taxonomicPageImages-body.jsp -->

<!-- Must have String statusSet and statusSetSize defined -->

<%
  // Do not display for species if a browse.do request.
  String sdRank = (String) request.getParameter("rank");
  String initialTarget = HttpUtil.getTarget(request);

  String sdTarget = HttpUtil.getTargetMinusParams(initialTarget, "statusSet", "statusSetSize");

  sdTarget = HttpUtil.getUniquedTarget(request, sdTarget);
  //A.log("statusesDisplay initialTarget:" + initialTarget + " after:" + sdTarget);

  String sdDisplayChildRank = null;
  if (initialTarget.contains("taxonomicPage")) {
    sdDisplayChildRank = Formatter.initCap(sdRank);
  } else {
    sdDisplayChildRank = Formatter.initCap(Rank.getNextRank(sdRank));
    //A.log("*** sdDisplayChildRank:" + sdDisplayChildRank + " sdRank:" + sdRank);
  }
  // if no rank parameter, we will be vague. It is for display on a page like: http://localhost/antweb/browse.do?genus=myrmecina&statusSet=all
  if (sdDisplayChildRank == null) sdDisplayChildRank = "Children";

  if (false && Rank.SPECIES.equals(sdRank) && !(sdTarget.contains("taxonomicPage.do"))) {
    // Do nothing. False added Oct 14, 2018 to handle missing specimens here:
    //   http://localhost/antweb/browse.do?genus=hypoponera&species=us-ca01&rank=species&project=allantwebants&statusSet=all
    //A.log("statusesDisplay.jsp sdRank:" + sdRank);
  } else {
    boolean showStatuses = sdTarget.contains("browse.do") 
      || sdTarget.contains("taxonomicPage.do")
      || sdTarget.contains("images.do");

    if (showStatuses) { %>
    
       <div id="status_toggle">
           <div class="left"><a href=<%= AntwebProps.getDomainApp() %>/common/statusDisplayPage.jsp target="new"><%= sdDisplayChildRank %> Status</a>:</div>
           <div id="change_status" class="has_options">
               <span id="which_status"><span style="text-transform:capitalize;"><%= statusSet %></span></span>
               <div id="status_choice" class="options">
                   <ul>
 <%
      ArrayList<String> statusSets = null;
      if ("min".equals(statusSetSize)) {
        statusSets = new ArrayList<String>();
        statusSets.add(StatusSet.VALID_EXTANT);
        statusSets.add(StatusSet.VALID);
        if (Project.WORLDANTS.equals(overview.getName())) {
          statusSets.add(StatusSet.COMPLETE);
        } else {
          statusSets.add(StatusSet.ALL);         
        }
      } else {
        statusSets = StatusSet.getStatusSets();
      }
      for (String aStatusSet : statusSets) { 
        if (aStatusSet.equals(Status.VALID)) aStatusSet = "Valid (with fossils)";
%>
                     <li><a href="<%= sdTarget + "&statusSet=" + aStatusSet %>"><span style="text-transform:capitalize;"><%= aStatusSet %></span></a></li>
   <% }
      //A.log("statusesDisplay() statusSets:" + statusSets + " showStatus:" + showStatuses + " statusSet:" + statusSet + " statusSetSize:" + statusSetSize + " sdTarget:" + sdTarget);

      if ("min".equals(statusSetSize)) { %>
                     <li><a href="<%= sdTarget + "&statusSetSize=max" + "&statusSet=" + statusSet %>">Click for more options!</a></li> 
   <% } else { %>
                     <li><a href="<%= sdTarget + "&statusSetSize=min" + "&statusSet=" + statusSet %>">Click for less options!</a></li> 
   <% } %>
                   </ul>
               </div>
           </div>
       </div>
  <% 
    } // end showStatuses
  }  
 %>

