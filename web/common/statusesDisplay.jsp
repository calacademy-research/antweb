<%-- The view switch of a list (Brian Fisher, 2026-11-02): four buttons, Valid | Curated | All specimen names | Fossil, a
     segmented control (not a menu), the same on every list; it replaces the status menu of 2026-10-30 and the "Names
     from" selector. Statically included by showBrowse-body.jsp, taxonomicPage-body.jsp, taxonomicPageImages-body.jsp and
     taxonChildImages.jsp (uses their statusSet and overview): each button links to the same page with statusSet
     replaced (StatusSet.choicesFor, choiceLabel; hover text ListView.choiceTitle); the current one is marked
     aria-current. The Fossil button is shown only when the list has fossil names here (ListView.HAS_FOSSIL_ATTR, one
     EXISTS query; 2026-11-02). The AntCat list and Fossil Ants show their own choices the same way. Shown on browse.do,
     taxonomicPage.do and images.do, not on a species or subspecies page. Docs: docs/numbers.md "Views of lists",
     docs/flows/places.md. Tests: tests/e2e/test_status_choice.py. --%><%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<!-- statusesDisplay.jsp  Found in showBrowse-body.jsp, taxonomicPage-body.jsp, taxonomicPageImages-body.jsp and taxonChildImages.jsp -->

<!-- Must have String statusSet (the list's view, StatusSet.getStatusSet) and overview defined -->

<%
  // Not shown on a species or subspecies page (browse.do, images.do): it lists the specimens of one name, and the view
  // does not apply there (BrowseAction). statusSetSize (the old "more options") and specimen (the old "Names from")
  // are dropped from the buttons' links.
  String sdRank = (String) request.getParameter("rank");
  String initialTarget = HttpUtil.getTarget(request);

  // getTargetMinusParams removes every parameter that starts with the name: statusSet and statusSetSize both go.
  String sdTarget = HttpUtil.getTargetMinusParams(initialTarget, "statusSet", "statusSetSize");
  sdTarget = HttpUtil.removeParam(sdTarget, "specimen");

  sdTarget = HttpUtil.getUniquedTarget(request, sdTarget);

  boolean sdListPage = initialTarget.contains("taxonomicPage");
  String sdRowRank = sdListPage ? sdRank : Rank.getNextRank(sdRank);

  boolean sdSpecimenRows = !sdListPage && (Rank.SPECIES.equals(sdRank) || Rank.SUBSPECIES.equals(sdRank));
  boolean showStatuses = !sdSpecimenRows && (sdTarget.contains("browse.do")
      || sdTarget.contains("taxonomicPage.do")
      || sdTarget.contains("images.do"));

  if (showStatuses) {
    String sdOverviewName = overview == null ? null : overview.getName();
    String sdJoin = sdTarget.contains("?") ? "&" : "?";
    String[] sdChoices = StatusSet.choicesFor(sdOverviewName, sdRowRank, true);
    String sdCurrent = statusSet;
    String sdPlural = sdRowRank == null ? null : Rank.getPluralRank(sdRowRank);
%>
       <div id="status_toggle" class="view-switch" role="group" aria-label="View">
           <span class="view-switch-label"><a href="<%= AntwebProps.getDomainApp() %>/statusDisplayPage.do" target="new" title="What the name statuses (valid, morphospecies, indet ...) mean">View</a>:</span>
           <span id="change_status" class="view-buttons" data-status-set="<%= Html.escAttr(sdCurrent) %>">
<%
    // the Fossil button only when the list has fossil names here (Brian Fisher, 2026-11-02; ListView.HAS_FOSSIL_ATTR)
    Object sdHasFossil = request.getAttribute(org.calacademy.antweb.ListView.HAS_FOSSIL_ATTR);
    for (String aStatusSet : sdChoices) {
      boolean sdIsCurrent = aStatusSet.equals(sdCurrent);
      if (StatusSet.LIST_FOSSIL.equals(aStatusSet) && !org.calacademy.antweb.ListView.showsFossilButton(sdHasFossil, sdCurrent)) continue;
      String sdTitle = org.calacademy.antweb.ListView.choiceTitle(aStatusSet, sdPlural);
%>
             <a class="view-btn<%= sdIsCurrent ? " is-current" : "" %>" data-status-choice="<%= Html.escAttr(aStatusSet) %>"<%= sdIsCurrent ? " aria-current=\"true\"" : "" %><%= sdTitle == null ? "" : " title=\"" + Html.escAttr(sdTitle) + "\"" %> href="<%= Html.escAttr(sdTarget + sdJoin + "statusSet=" + Html.url(aStatusSet)) %>"><%= Html.esc(StatusSet.choiceLabel(aStatusSet, sdRowRank, sdOverviewName, true)) %></a>
<%  } %>
           </span>
       </div>
<%
  } // end showStatuses
%>
