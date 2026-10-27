package test.org.calacademy.antweb.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.search.AdvancedSearchResults;
import org.calacademy.antweb.search.ResultItem;

/**
 * B54 (docs/numbers.md row 68), 2026-10-27: the species list of a search by species (also the list that Compare, Map
 * and the field guide of the search choose from) leaves out names not identified to genus (PlaceTotals.isIdentifiedToGenus,
 * Brian Fisher 2026-10-22); their specimens stay in the search and are counted apart. No database needed.
 */
public class SearchSpeciesListTest extends TestCase {

    private static ResultItem specimen(String code, String subfamily, String genus, String species) {
        ResultItem r = new ResultItem();
        r.setCode(code);
        r.setSubfamily(subfamily);
        r.setGenus(genus);
        r.setSpecies(species);
        r.setFullName(genus + " " + species);
        r.setPageParams("rank=species&genus=" + genus + "&species=" + species);
        return r;
    }

    private static AdvancedSearchResults search(ResultItem... items) {
        AdvancedSearchResults results = new AdvancedSearchResults();
        ArrayList<ResultItem> list = new ArrayList<>();
        for (ResultItem i : items) list.add(i);
        results.setResults(list);
        return results;
    }

    public void testNamesNotIdentifiedToGenusAreNotSpecies() {
        AdvancedSearchResults results = search(
            specimen("a1", "myrmicinae", "pheidole", "megacephala"),
            specimen("a2", "myrmicinae", "pheidole", "megacephala"),
            specimen("a3", "myrmicinae", "tetramorium", "simillimum"),
            specimen("b1", "myrmicinae", "(indet)", "indet"),
            specimen("b2", "formicinae", "(formicinae)", "indet"),
            specimen("b3", "(indet)", "((indet))", "indet"),
            specimen("b4", "ponerinae", "indet", "mg01"),
            specimen("b5", "ponerinae", "Undet", "sp1"),
            specimen("c1", "amblyoponinae", "amblyoponinae_genus8", "nz01"));
        ArrayList<ResultItem> species = results.getSpeciesList();
        ArrayList<String> names = new ArrayList<>();
        for (ResultItem r : species) names.add(r.getFullName());
        assertEquals("[amblyoponinae_genus8 nz01, pheidole megacephala, tetramorium simillimum]", names.toString());
        assertEquals(5, results.getSpecimensNotIdentifiedToGenus());
        // The specimens are not hidden: the note's N plus the specimens of the listed species is the search's total.
        int ofListed = 0;
        for (ResultItem r : results.getResults()) if (AdvancedSearchResults.countsAsSpecies(r)) ++ofListed;
        assertEquals(results.getResults().size(), ofListed + results.getSpecimensNotIdentifiedToGenus());
    }

    public void testOnlyUnidentifiedNamesGiveAnEmptyListNotAnError() {
        AdvancedSearchResults results = search(
            specimen("b1", "myrmicinae", "(indet)", "indet"),
            specimen("b2", "formicinae", "(formicinae)", "indet"));
        assertTrue(results.getSpeciesList().isEmpty());
        assertEquals(2, results.getSpecimensNotIdentifiedToGenus());
        assertEquals(2, results.getResults().size());
        assertTrue(search().getSpeciesList().isEmpty());
        assertEquals(0, search().getSpecimensNotIdentifiedToGenus());
    }

    public void testSpecimenViewQueryKeepsTheSearchAndListsBySpecimen() {
        Map<String, String[]> p = new HashMap<>();
        p.put("searchMethod", new String[] {"advancedSearch"});
        p.put("genus", new String[] {"indet"});
        p.put("country", new String[] {"Costa Rica"});
        p.put("resultRank", new String[] {"species"});
        p.put("output", new String[] {"list"});
        p.put("_csrf", new String[] {"secret"});
        p.put("org.apache.struts.taglib.html.TOKEN", new String[] {"secret"});
        assertEquals("country=Costa+Rica&genus=indet&searchMethod=advancedSearch&resultRank=specimen",
            AdvancedSearchResults.specimenViewQuery(p));
        assertEquals("resultRank=specimen", AdvancedSearchResults.specimenViewQuery(null));
    }
}
