<!-- taxonReport.jsp -->


<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.sort.*" %>

<%

/*
  taxonReport.jsp is displayed by the browse.do requests.  It is included in showBrowse-body.jsp
  in the cases where the children are not specimen (family, subfamily, genus).
*/
    //A.log("taxonReport.jsp XXX");
    //ArrayList<Taxon> childrenList = taxon.getChildren();
    if (childrenList == null) childrenList = new ArrayList<>();   // no children: an empty list (2026-10-29)
    int tChildrenCount = childrenList.size();
    Taxon[] trChildrenArray = new Taxon[tChildrenCount];
    childrenList.toArray(trChildrenArray);
    
    ArrayList<Taxon> children = new ArrayList<Taxon>(childrenList);

    String thisTarget = HttpUtil.getTarget(request);

    boolean isWithSpecimen = false;
    boolean isOnlyShowUnImaged = false;    
    java.util.Set<String> unimagedNames = null;   // taxonomicPage.do?isImaged=false only (Q12)
    org.calacademy.antweb.home.GenusOnly genusOnly = null;   // taxonomicPage.do only (2026-10-28)
    // the genus rows of a subfamily in a place: specimens collected here not on the species list (BrowseAction, 2026-10-30)
    org.calacademy.antweb.PlaceTaxonNotes.OtherNames otherNames = (org.calacademy.antweb.PlaceTaxonNotes.OtherNames) request.getAttribute(org.calacademy.antweb.PlaceTaxonNotes.OTHER_NAMES);
%>

<%@ include file="/taxonReportBody.jsp" %>



