<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<!-- statusesDisplay.jsp  Found in showBrowse-body.jsp, taxonomicPage-body.jsp, taxonomicPageImages-body.jsp and taxonChildImages.jsp -->

<!-- Must have String statusSet (the list's choice, StatusSet.getStatusSet) and overview defined -->

<%
  // The status selector of a list, Brian Fisher 2026-10-30 (docs/numbers.md "Status choice of lists"): four plain
  // choices (Valid species, Valid species + morphospecies, All names, Fossil species), each the rows of a number of the
  // place page; the AntCat list and Fossil Ants offer their own sets. A choice links to the same page with statusSet
  // replaced (statusSetSize, the old "more options", is dropped). Not shown on a species or subspecies page (browse.do,
  // images.do): it lists the specimens of one name, and the choice does not apply there (BrowseAction).
  String sdRank = (String) request.getParameter("rank");
  String initialTarget = HttpUtil.getTarget(request);

  // getTargetMinusParam removes every parameter that starts with the name: statusSet and statusSetSize both go.
  String sdTarget = HttpUtil.getTargetMinusParams(initialTarget, "statusSet", "statusSetSize");

  sdTarget = HttpUtil.getUniquedTarget(request, sdTarget);

  boolean sdListPage = initialTarget.contains("taxonomicPage");
  String sdRowRank = sdListPage ? sdRank : Rank.getNextRank(sdRank);
  String sdDisplayChildRank = Formatter.initCap(sdRowRank);
  // if no rank parameter, we will be vague. It is for display on a page like: http://localhost/antweb/browse.do?genus=myrmecina
  if (sdDisplayChildRank == null) sdDisplayChildRank = "Children";

  boolean sdSpecimenRows = !sdListPage && (Rank.SPECIES.equals(sdRank) || Rank.SUBSPECIES.equals(sdRank));
  boolean showStatuses = !sdSpecimenRows && (sdTarget.contains("browse.do")
      || sdTarget.contains("taxonomicPage.do")
      || sdTarget.contains("images.do"));

  if (showStatuses) {
    String sdOverviewName = overview == null ? null : overview.getName();
    String[] sdChoices = StatusSet.choicesFor(sdOverviewName);
    String sdJoin = sdTarget.contains("?") ? "&" : "?";
    // a subfamily list: "Valid subfamilies, extant and fossil" only when the place has a fossil one (2026-10-30)
    boolean sdFossilSubfamilies = StatusSet.fossilSubfamilies(request);
%>
       <div id="status_toggle">
           <div class="left"><a href=<%= AntwebProps.getDomainApp() %>/statusDisplayPage.do target="new"><%= sdDisplayChildRank %> Status</a>:</div>
           <div id="change_status" class="has_options" data-status-set="<%= Html.escAttr(statusSet) %>">
               <span id="which_status"><span><%= Html.esc(StatusSet.choiceLabel(statusSet, sdRowRank, sdOverviewName, sdFossilSubfamilies)) %></span></span>
               <div id="status_choice" class="options">
                   <ul>
<%
    for (String aStatusSet : sdChoices) {
%>
                     <li><a data-status-choice="<%= Html.escAttr(aStatusSet) %>" href="<%= Html.escAttr(sdTarget + sdJoin + "statusSet=" + Html.url(aStatusSet)) %>"><span><%= Html.esc(StatusSet.choiceLabel(aStatusSet, sdRowRank, sdOverviewName, sdFossilSubfamilies)) %></span></a></li>
<%  } %>
                   </ul>
               </div>
           </div>
       </div>
<%
  } // end showStatuses
%>
