package org.calacademy.antweb;

import java.sql.*;

/**
 * What a place (an overview: project, museum, bioregion, geolocale) gives the pages and queries: its list table and
 * table, heading, page targets, names, link parameters, search criteria, SQL clauses of its children and specimens,
 * the image count query of a name and getTaxonSet (a taxon's row in its list). Implemented by Overview; used by
 * util/OverviewMgr.setOverview and Taxon.initTaxonSet. Docs: docs/flows/places.md.
 */
public interface Overviewable {

    String getTaxonSetTable();
    String getTable();
    String getHeading();
    String getPluralTargetDo();
    String getTargetDo();
    String getThisPageTarget();

    String getName();
    String getDisplayName();
    String getShortDisplayName();

    String getParams();

    String getSearchCriteria();
  
    String getFetchChildrenClause();
    String getSpecimenTaxonSetClause();
//    public String getChosenImageClause();

    String getImageCountQuery(String taxonName);

    TaxonSet getTaxonSet(String taxonName, String rank, Connection connection) throws SQLException;


}
