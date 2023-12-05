package org.calacademy.antweb;

import java.sql.*;

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
