package org.calacademy.antweb.search;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.calacademy.antweb.util.DownloadLimits;

/**
 * Paging of the advanced search specimen list (Brian Fisher, 2026-10-21). Before, a search computed
 * up to 50,000 specimens and showed the first 2,000 on one page (9 MB, 11 s for Madagascar), with a
 * "See all" link for 10,000. Now each page is PAGE_SIZE specimens, read with LIMIT/OFFSET in the order
 * chosen (orderBy: the sort applies before paging), and the total comes from a COUNT query.
 *
 * Kept in the session as "advancedSearchPaging" with the search (SearchParameters), so that the
 * Download Data box (getSearchList.do) can read the same specimens again, at most 5,000.
 */
public class SearchPaging implements Serializable {

    public static final int PAGE_SIZE = 200;

    /** Request parameter of the page number (1 is the first page). */
    public static final String PAGE_PARAM = "page";

    private final int total;
    private final int page;
    private final String sortBy;
    private final String sortOrder;
    private final transient SearchParameters searchParameters;
    /** The search's query string (advancedSearch.do?...), so that advancedSearchResults.do can show it again (F25). */
    private String queryString;

    public SearchPaging(int total, int page, String sortBy, String sortOrder, SearchParameters searchParameters) {
        this.total = Math.max(0, total);
        this.page = Math.max(1, Math.min(page, lastPage(this.total)));
        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
        this.searchParameters = searchParameters;
    }

    public int getTotal() { return total; }
    public int getPage() { return page; }
    public String getSortBy() { return sortBy; }
    public String getSortOrder() { return sortOrder; }
    public SearchParameters getSearchParameters() { return searchParameters; }
    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public int getOffset() { return offset(page); }
    public int getLastPage() { return lastPage(total); }
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < lastPage(total); }

    /** "Showing 1–200 of 141,728" (en dash). */
    public String getShowingText() { return showingText(page, total); }

    public String getOrderBy() { return orderBy(sortBy, sortOrder); }

    public static int offset(int page) {
        return (Math.max(1, page) - 1) * PAGE_SIZE;
    }

    public static int lastPage(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Page number from the request parameter: 1 when missing or not a plain number. */
    public static int pageFromParam(String value) {
        if (value == null || !value.matches("\\d{1,7}")) return 1;
        return Math.max(1, Integer.parseInt(value));
    }

    public static String showingText(int page, int total) {
        if (total <= 0) return "Showing 0 of 0";
        int first = offset(page) + 1;
        int last = Math.min(total, offset(page) + PAGE_SIZE);
        return "Showing " + DownloadLimits.count(first) + "–" + DownloadLimits.count(last) + " of " + DownloadLimits.count(total);
    }

    // The sort choices of the list (specimenReport.jsp "Sort by") and their columns. Unknown values
    // sort by specimen code. sp.code is always last, so that the order is the same on every page.
    private static final Map<String, String> SORT_COLUMNS = new LinkedHashMap<>();
    static {
        SORT_COLUMNS.put("code", "sp.code");
        SORT_COLUMNS.put("taxonname", "sp.genus, sp.species, sp.subspecies");
        SORT_COLUMNS.put("name", "sp.genus, sp.species, sp.subspecies");
        SORT_COLUMNS.put("bioregion", "sp.bioregion");
        SORT_COLUMNS.put("caste", "sp.caste, sp.subcaste");
        SORT_COLUMNS.put("collectedby", "sp.collectedby");
        SORT_COLUMNS.put("collection", "sp.collectioncode");
        SORT_COLUMNS.put("datecollected", "sp.datecollectedstart");
        SORT_COLUMNS.put("country", "sp.country");
        SORT_COLUMNS.put("databy", "ant_group.name");
        SORT_COLUMNS.put("determinedby", "sp.determinedby");
        SORT_COLUMNS.put("dna", "sp.dnaextractionnotes");
        SORT_COLUMNS.put("elevation", "sp.elevation + 0");
        SORT_COLUMNS.put("habitat", "sp.habitat");
        SORT_COLUMNS.put("latitude", "sp.decimal_latitude");
        SORT_COLUMNS.put("lifestage", "sp.life_stage");
        SORT_COLUMNS.put("location", "sp.country, sp.adm1, sp.adm2, sp.localityname");
        SORT_COLUMNS.put("locatedat", "sp.locatedat");
        SORT_COLUMNS.put("longitude", "sp.decimal_longitude");
        SORT_COLUMNS.put("medium", "sp.medium");
        SORT_COLUMNS.put("method", "sp.method");
        SORT_COLUMNS.put("microhabitat", "sp.microhabitat");
        SORT_COLUMNS.put("museum", "sp.museum");
        SORT_COLUMNS.put("images", "imagecount");
        SORT_COLUMNS.put("ownedby", "sp.ownedby");
        SORT_COLUMNS.put("specimennotes", "sp.specimennotes");
        SORT_COLUMNS.put("type", "sp.type_status");
        SORT_COLUMNS.put("created", "sp.created");
        SORT_COLUMNS.put("uploadid", "sp.upload_id");
    }

    private static final java.util.Set<String> NUMERIC = java.util.Set.of("sp.elevation + 0", "sp.decimal_latitude", "sp.decimal_longitude", "sp.upload_id", "sp.datecollectedstart", "sp.created");

    // Sorts that, as before, list the largest or latest first when no order is chosen.
    private static final java.util.Set<String> DESCENDING_FIRST = java.util.Set.of("images", "datecollected", "created", "type", "elevation", "latitude", "longitude");

    /** ORDER BY of a sort choice ("up"/"down", as the arrow links send it). Only listed columns reach SQL. */
    public static String orderBy(String sortBy, String sortOrder) {
        String key = sortBy == null ? "code" : sortBy.toLowerCase();
        String columns = SORT_COLUMNS.get(key);
        if (columns == null) { key = "code"; columns = SORT_COLUMNS.get("code"); }
        boolean desc = DESCENDING_FIRST.contains(key);
        if ("down".equals(sortOrder)) desc = !desc;
        String dir = desc ? " desc" : "";
        StringBuilder sb = new StringBuilder();
        for (String c : columns.split(",")) {
            c = c.trim();
            if (sb.length() > 0) sb.append(", ");
            // Specimens without a value come last, in either order.
            if (c.equals("sp.code") || c.equals("imagecount")) {
                // never empty
            } else if (NUMERIC.contains(c)) {
                sb.append("(").append(c).append(") is null, ");
            } else {
                sb.append("coalesce(").append(c).append(", '') = '', ");
            }
            sb.append(c).append(dir);
        }
        if (!"code".equals(key)) sb.append(", sp.code");
        return sb.toString();
    }

    /**
     * Where advancedSearchResults.do sends the browser (2026-10-27, finding F25). The session keeps one page of
     * the search (200 rows), so showing the session list again said "Showing 1-200 of 200" with no Next link
     * for a search of 3,000. When the session holds a paged search with its query string and the request has
     * no parameters of its own, the answer is that search again at the same page: path?query&page=N.
     * Null (show the session list as before) when the request has parameters (Map and Compare views, the
     * sort of an unpaged list), when there is no paged search, or when its query string is unknown.
     */
    public static String reshowUrl(String path, String requestQueryString, SearchPaging paging) {
        if (requestQueryString != null && !requestQueryString.isEmpty()) return null;
        if (paging == null) return null;
        String q = paging.getQueryString();
        if (q == null || q.isEmpty()) return null;
        return pageUrl(path, q, paging.getPage());
    }

    /**
     * Link to another page of the same search: the query string with the page parameter replaced.
     * Path is e.g. "https://www.antweb.org/advancedSearch.do".
     */
    public static String pageUrl(String path, String queryString, int page) {
        StringBuilder q = new StringBuilder();
        if (queryString != null) {
            for (String part : queryString.split("&")) {
                if (part.isEmpty() || part.equals(PAGE_PARAM) || part.startsWith(PAGE_PARAM + "=")) continue;
                if (q.length() > 0) q.append('&');
                q.append(part);
            }
        }
        if (q.length() > 0) q.append('&');
        q.append(PAGE_PARAM).append('=').append(URLEncoder.encode(String.valueOf(Math.max(1, page)), StandardCharsets.UTF_8));
        return path + "?" + q;
    }
}
