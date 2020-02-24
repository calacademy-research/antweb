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
    int tChildrenCount = childrenList.size();
    Taxon[] trChildrenArray = new Taxon[tChildrenCount];
    childrenList.toArray(trChildrenArray);
    
    ArrayList<Taxon> children = new ArrayList<Taxon>(childrenList);

    String thisTarget = HttpUtil.getTarget(request);

    boolean isWithSpecimen = false;
    boolean isOnlyShowUnImaged = false;    
%>

<%@ include file="/taxonReportBody.jsp" %>



