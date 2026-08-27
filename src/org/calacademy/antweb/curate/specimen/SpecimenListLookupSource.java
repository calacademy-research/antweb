package org.calacademy.antweb.curate.specimen;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.calacademy.antweb.Specimen;

public interface SpecimenListLookupSource {
    Map<String, Specimen> findByCodes(List<String> codes) throws SQLException;
}
