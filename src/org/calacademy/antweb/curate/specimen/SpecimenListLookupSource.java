package org.calacademy.antweb.curate.specimen;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.calacademy.antweb.Specimen;

/**
 * Where the Specimen List Lookup gets specimens by code: SpecimenListLookupAction uses SpecimenDb (table
 * specimen); the unit test passes a map. Tests: test/.../curate/specimen/SpecimenListLookupServiceTest.java.
 */
public interface SpecimenListLookupSource {
    Map<String, Specimen> findByCodes(List<String> codes) throws SQLException;
}
