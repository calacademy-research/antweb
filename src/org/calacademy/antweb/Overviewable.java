package org.calacademy.antweb;

import java.util.Hashtable;
import java.sql.*;

public interface Overviewable {

    public String getTaxonSetTable();
    public String getTable();
    public String getHeading();
    public String getPluralTargetDo();
    public String getTargetDo();
    public String getThisPageTarget();

    public String getName();
    public String getDisplayName();
    public String getShortDisplayName();

    public String getParams();

    public String getSearchCriteria();
  
    public String getFetchChildrenClause();
    public String getSpecimenTaxonSetClause();
//    public String getChosenImageClause();

    public String getImageCountQuery(String taxonName);

    public TaxonSet getTaxonSet(String taxonName, String rank, Connection connection);


}
