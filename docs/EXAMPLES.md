# AntWeb Modification Examples

## Purpose

This document provides **complete, step-by-step tutorials** for common AntWeb modifications. Each example walks through the entire process from database to UI.

**Use this when you want to:**
- Add a new field to an existing feature
- Create a new page or endpoint
- Modify existing functionality
- Understand the complete change workflow

---

## Example Index

1. [Add a Field to Bioregion](#example-1-add-a-field-to-bioregion)
2. [Add a Column to Specimen Upload](#example-2-add-a-column-to-specimen-upload)
3. [Create a New Web Page](#example-3-create-a-new-web-page)
4. [Add an API Endpoint](#example-4-add-an-api-endpoint)
5. [Modify Image Picker Logic](#example-5-modify-image-picker-logic)
6. [Add a New Search Filter](#example-6-add-a-new-search-filter)
7. [Create a New Statistics Calculation](#example-7-create-a-new-statistics-calculation)
8. [Add a User Permission](#example-8-add-a-user-permission)

---

## Example 1: Add a Field to Bioregion

**Goal:** Add a `last_survey_date` field to track when each bioregion was last surveyed.

### Step 1: Add Database Column

```sql
-- Create migration file: db/upgrade/8.106/2026-01-15-bioregion-survey-date.sql

ALTER TABLE bioregion 
ADD COLUMN last_survey_date DATE NULL
AFTER endemic_species_count;

CREATE INDEX idx_bioregion_survey_date 
ON bioregion(last_survey_date);
```

**Apply migration:**
```bash
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant < db/upgrade/8.106/2026-01-15-bioregion-survey-date.sql
```

### Step 2: Update Java Model Class

**File:** `src/org/calacademy/antweb/geolocale/Bioregion.java`

```java
// Add property
private Date lastSurveyDate;

// Add getter
public Date getLastSurveyDate() {
    return lastSurveyDate;
}

// Add setter
public void setLastSurveyDate(Date lastSurveyDate) {
    this.lastSurveyDate = lastSurveyDate;
}
```

### Step 3: Update Database Access Layer

**File:** `src/org/calacademy/antweb/home/BioregionDb.java`

Find the `getBioregion()` method and add:

```java
public Bioregion getBioregion(String bioregionName) throws SQLException {
    // ... existing code ...
    
    while (rset.next()) {
        bioregion.setName(rset.getString("name"));
        // ... existing fields ...
        
        // ADD THIS LINE:
        bioregion.setLastSurveyDate(rset.getDate("last_survey_date"));
        
        // ... rest of existing code ...
    }
    
    return bioregion;
}
```

Also update `getBioregions()` method the same way.

### Step 4: Add Update Method

**File:** `src/org/calacademy/antweb/home/BioregionDb.java`

```java
public void updateSurveyDate(String bioregionName, Date surveyDate) throws SQLException {
    String query = "UPDATE bioregion SET last_survey_date = ? WHERE name = ?";
    
    try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
        stmt.setDate(1, new java.sql.Date(surveyDate.getTime()));
        stmt.setString(2, bioregionName);
        stmt.executeUpdate();
    }
}
```

### Step 5: Update JSP Display

**File:** `web/bioregion.jsp`

```jsp
<%-- Find the bioregion info section and add: --%>

<div class="bioregion-info">
    <h2><bean:write name="bioregion" property="title"/></h2>
    
    <%-- ... existing fields ... --%>
    
    <%-- ADD THIS: --%>
    <logic:present name="bioregion" property="lastSurveyDate">
    <p><strong>Last Survey:</strong> 
       <bean:write name="bioregion" property="lastSurveyDate" format="yyyy-MM-dd"/>
    </p>
    </logic:present>
</div>
```

### Step 6: Add Admin Edit Interface (Optional)

**File:** `web/bioregionEdit.jsp` (create if doesn't exist)

```jsp
<html:form action="/updateBioregion">
    <html:hidden property="name" value="${bioregion.name}"/>
    
    <label>Last Survey Date:</label>
    <html:text property="lastSurveyDate" size="10"/>
    <br/>
    
    <html:submit value="Update"/>
</html:form>
```

### Step 7: Update API Response (Optional)

**File:** `api/v3/api.py`

```python
# Find bioregion serialization code and add:

def bioregion_to_dict(bioregion):
    return {
        'name': bioregion.name,
        'title': bioregion.title,
        # ... existing fields ...
        'last_survey_date': bioregion.last_survey_date.isoformat() if bioregion.last_survey_date else None
    }
```

### Step 8: Deploy and Test

```bash
# Rebuild and deploy Java code
docker-compose exec antweb ant deploy

# Restart API if modified
docker-compose restart api

# Test in browser
# Visit: http://localhost/bioregion.do?name=nearctic

# Test API
curl http://localhost:5000/api/v3/geolocales?bioregion=nearctic
```

### Step 9: Test with Data

```sql
-- Set a survey date
UPDATE bioregion 
SET last_survey_date = '2025-06-15' 
WHERE name = 'nearctic';

-- Verify
SELECT name, last_survey_date FROM bioregion;
```

**Refresh page and confirm date displays.**

---

## Example 2: Add a Column to Specimen Upload

**Goal:** Add a `preservation_method` field to specimen uploads (e.g., "dried", "alcohol", "slide mount").

### Step 1: Add Database Column

```sql
-- db/upgrade/8.106/2026-01-15-specimen-preservation.sql

ALTER TABLE specimen 
ADD COLUMN preservation_method VARCHAR(64) NULL
AFTER medium;

CREATE INDEX idx_preservation_method 
ON specimen(preservation_method);
```

**Apply:**
```bash
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant < db/upgrade/8.106/2026-01-15-specimen-preservation.sql
```

### Step 2: Update Upload File Format

**Document the new format:**

The specimen upload file now has **43 columns** (was 42).

**New column 9:** `PreservationMethod` (after `Medium`, before `SpecimenNotes`)

**Example:**
```
Count	SpecimenCode	Subfamily	Genus	SpeciesGroup	Species	LifeStageSex	Medium	PreservationMethod	SpecimenNotes	...
1	CASENT0106322	Myrmicinae	Gauromyrmex		MY01	1aq	pin	dried	Ant AToL voucher	...
```

### Step 3: Update Parser

**File:** `src/org/calacademy/antweb/upload/SpecimenUploadDb.java`

Find the `parseSpecimenLine()` method:

```java
private Specimen parseSpecimenLine(String line) throws SQLException {
    String[] fields = line.split("\t");
    
    Specimen specimen = new Specimen();
    
    specimen.setCode(fields[1]);           // Column 2: SpecimenCode
    specimen.setSubfamily(fields[2]);      // Column 3: Subfamily
    // ... existing fields ...
    
    specimen.setMedium(fields[7]);         // Column 8: Medium
    
    // ADD THIS LINE:
    if (fields.length > 8 && !fields[8].isEmpty()) {
        specimen.setPreservationMethod(fields[8]);
    }
    
    specimen.setSpecimenNotes(fields[9]); // NOW Column 10 (was 9)
    // ... rest of fields (increment indices by 1) ...
    
    return specimen;
}
```

**IMPORTANT:** Increment all subsequent field indices by 1!

### Step 4: Update Specimen Model

**File:** `src/org/calacademy/antweb/Specimen.java`

```java
private String preservationMethod;

public String getPreservationMethod() {
    return preservationMethod;
}

public void setPreservationMethod(String preservationMethod) {
    this.preservationMethod = preservationMethod;
}
```

### Step 5: Update Insert SQL

**File:** `src/org/calacademy/antweb/home/SpecimenDb.java`

Find `insertSpecimen()` method:

```java
String query = "INSERT INTO specimen (" +
    "code, taxon_name, subfamily, genus, species, subspecies, " +
    "life_stage, medium, preservation_method, specimen_notes, " +  // ADD preservation_method
    "... other fields ..." +
    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ...)";  // Add ? for new field

try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
    stmt.setString(1, specimen.getCode());
    stmt.setString(2, specimen.getTaxonName());
    // ... existing fields ...
    stmt.setString(8, specimen.getMedium());
    stmt.setString(9, specimen.getPreservationMethod());  // ADD THIS
    stmt.setString(10, specimen.getSpecimenNotes());     // Increment index
    // ... rest of fields (increment indices) ...
    
    stmt.executeUpdate();
}
```

### Step 6: Update Display

**File:** `web/specimen.jsp`

```jsp
<tr>
    <td><strong>Medium:</strong></td>
    <td><bean:write name="specimen" property="medium"/></td>
</tr>

<%-- ADD THIS: --%>
<logic:present name="specimen" property="preservationMethod">
<tr>
    <td><strong>Preservation:</strong></td>
    <td><bean:write name="specimen" property="preservationMethod"/></td>
</tr>
</logic:present>
```

### Step 7: Update API

**File:** `api/v3/home/specimen.py`

```python
class Specimen(Base):
    __tablename__ = 'specimen'
    
    code = Column(String(20), primary_key=True)
    # ... existing fields ...
    medium = Column(String(32))
    preservation_method = Column(String(64))  # ADD THIS
    specimen_notes = Column(Text)
```

**File:** `api/v3/api.py`

```python
# In specimen serialization, add:
'preservation_method': specimen.preservation_method,
```

### Step 8: Create Example Upload File

```bash
cat > example-specimens-with-preservation.txt << 'EOF'
Count	SpecimenCode	Subfamily	Genus	SpeciesGroup	Species	LifeStageSex	Medium	PreservationMethod	SpecimenNotes	DNANotes	LocatedAt	OwnedBy	TypeStatus	DeterminedBy	DateDetermined	CollectionCode	CollectedBy	DateCollectedStart	DateCollectedEnd	Method	Habitat	Microhabitat	CollectionNotes	LocalityName	Adm2	Adm1	Adm1check	ISO_3166	Country	Elevation	LatDeg	LatMin	NS	LonDeg	LonMin	EW	LocLatitude	LocLongitude	LatLonMaxError	BiogeographicRegion	LocalityNotes	ElevationMaxError
1	TEST0001	Formicinae	Camponotus		pennsylvanicus	1w	pin	dried	Test specimen		UCDC	UCDC		Smith, J.	2026-01-15	TEST001	Smith, J.	2025-12-01		hand collection	forest	ground		Test Site		California	1	US	United States	100 m							37.5	-122.0	10 m	Nearctic		
EOF
```

### Step 9: Deploy and Test

```bash
# Deploy
docker-compose exec antweb ant deploy
docker-compose restart api

# Test upload
# 1. Go to http://localhost/upload.do
# 2. Upload example file
# 3. Check uploadResults.jsp for success
# 4. View specimen: http://localhost/specimen/TEST0001
# 5. Verify "Preservation: dried" displays

# Test API
curl http://localhost:5000/api/v3/specimens/TEST0001
# Should show: "preservation_method": "dried"
```

### Step 10: Update Documentation

Update `DATABASE.md` with new column information.

---

## Example 3: Create a New Web Page

**Goal:** Create a "Recent Uploads" page showing the last 20 specimen uploads.

### Step 1: Create Action Class

**File:** `src/org/calacademy/antweb/RecentUploadsAction.java`

```java
package org.calacademy.antweb;

import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;
import org.calacademy.antweb.home.*;

public class RecentUploadsAction extends Action {
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, 
                                 HttpServletResponse response) {
        
        Connection conn = null;
        
        try {
            // Get database connection
            conn = DBUtil.getConnection();
            
            // Get recent uploads
            UploadDb uploadDb = new UploadDb(conn);
            List<Upload> uploads = uploadDb.getRecentUploads(20);
            
            // Put in request for JSP
            request.setAttribute("uploads", uploads);
            
            return mapping.findForward("success");
            
        } catch (SQLException e) {
            // Handle error
            request.setAttribute("error", e.getMessage());
            return mapping.findForward("error");
            
        } finally {
            DBUtil.close(conn);
        }
    }
}
```

### Step 2: Add Database Method

**File:** `src/org/calacademy/antweb/home/UploadDb.java`

```java
public List<Upload> getRecentUploads(int limit) throws SQLException {
    List<Upload> uploads = new ArrayList<>();
    
    String query = "SELECT * FROM upload " +
                   "ORDER BY upload_date DESC " +
                   "LIMIT ?";
    
    try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
        stmt.setInt(1, limit);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            Upload upload = new Upload();
            upload.setId(rs.getInt("id"));
            upload.setFileName(rs.getString("file_name"));
            upload.setUploadType(rs.getString("upload_type"));
            upload.setStatus(rs.getString("status"));
            upload.setRecordCount(rs.getInt("record_count"));
            upload.setUploadDate(rs.getTimestamp("upload_date"));
            upload.setUploadedBy(rs.getInt("uploaded_by"));
            
            uploads.add(upload);
        }
    }
    
    return uploads;
}
```

### Step 3: Register Action in Struts Config

**File:** `WEB-INF/struts-config.xml`

```xml
<action-mappings>
    <!-- ... existing actions ... -->
    
    <!-- ADD THIS: -->
    <action path="/recentUploads"
            type="org.calacademy.antweb.RecentUploadsAction">
        <forward name="success" path="/recentUploads.jsp"/>
        <forward name="error" path="/error.jsp"/>
    </action>
    
</action-mappings>
```

### Step 4: Create JSP Page

**File:** `web/recentUploads.jsp`

```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:include page="common/header.jsp"/>

<h1>Recent Uploads</h1>

<table class="uploads-table">
    <thead>
        <tr>
            <th>Date</th>
            <th>File Name</th>
            <th>Type</th>
            <th>Status</th>
            <th>Records</th>
            <th>Uploaded By</th>
        </tr>
    </thead>
    <tbody>
        <logic:iterate id="upload" name="uploads">
        <tr>
            <td><bean:write name="upload" property="uploadDate" format="yyyy-MM-dd HH:mm"/></td>
            <td><bean:write name="upload" property="fileName"/></td>
            <td><bean:write name="upload" property="uploadType"/></td>
            <td><bean:write name="upload" property="status"/></td>
            <td><bean:write name="upload" property="recordCount"/></td>
            <td><bean:write name="upload" property="uploadedBy"/></td>
        </tr>
        </logic:iterate>
    </tbody>
</table>

<logic:empty name="uploads">
    <p>No recent uploads found.</p>
</logic:empty>

<jsp:include page="common/footer.jsp"/>
```

### Step 5: Add CSS Styling

**File:** `web/css/antweb.css`

```css
.uploads-table {
    width: 100%;
    border-collapse: collapse;
}

.uploads-table th {
    background-color: #f0f0f0;
    padding: 10px;
    text-align: left;
    border-bottom: 2px solid #ddd;
}

.uploads-table td {
    padding: 8px;
    border-bottom: 1px solid #ddd;
}

.uploads-table tr:hover {
    background-color: #f9f9f9;
}
```

### Step 6: Add Navigation Link

**File:** `web/common/header.jsp`

```jsp
<div class="navigation">
    <a href="/browse.do">Browse</a> |
    <a href="/search.do">Search</a> |
    <a href="/recentUploads.do">Recent Uploads</a> |  <%-- ADD THIS --%>
    <a href="/about.do">About</a>
</div>
```

### Step 7: Deploy and Test

```bash
# Compile and deploy
docker-compose exec antweb ant deploy

# Test
# Visit: http://localhost/recentUploads.do
# Should show table of recent uploads
```

---

## Example 4: Add an API Endpoint

**Goal:** Add API endpoint to get specimens by collection date range.

### Step 1: Add Route to API

**File:** `api/v3/api.py`

```python
@application.route("/api/v3/specimens/by-date", methods=['GET'])
def specimens_by_date():
    """
    Get specimens collected within a date range.
    
    Query params:
        start_date: YYYY-MM-DD (required)
        end_date: YYYY-MM-DD (required)
        limit: max records (default 100)
        offset: pagination offset (default 0)
    """
    
    # Get parameters
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    limit = request.args.get('limit', 100, type=int)
    offset = request.args.get('offset', 0, type=int)
    
    # Validate
    if not start_date or not end_date:
        return jsonify({
            'error': 'start_date and end_date are required'
        }), 400
    
    try:
        # Parse dates
        from datetime import datetime
        start = datetime.strptime(start_date, '%Y-%m-%d')
        end = datetime.strptime(end_date, '%Y-%m-%d')
        
    except ValueError:
        return jsonify({
            'error': 'Invalid date format. Use YYYY-MM-DD'
        }), 400
    
    # Query database
    from home.specimen import Specimen
    
    specimens = session.query(Specimen)\
        .filter(Specimen.date_collected.between(start, end))\
        .limit(limit)\
        .offset(offset)\
        .all()
    
    # Serialize results
    results = []
    for specimen in specimens:
        results.append({
            'code': specimen.code,
            'taxon_name': specimen.taxon_name,
            'country': specimen.country,
            'date_collected': specimen.date_collected.isoformat() if specimen.date_collected else None,
            'collected_by': specimen.collected_by,
            'url': f'https://www.antweb.org/specimen/{specimen.code}'
        })
    
    return jsonify({
        'specimens': results,
        'count': len(results),
        'limit': limit,
        'offset': offset,
        'start_date': start_date,
        'end_date': end_date
    })
```

### Step 2: Update API Model (if needed)

**File:** `api/v3/home/specimen.py`

Ensure `date_collected` field exists:

```python
from sqlalchemy import Column, String, Date

class Specimen(Base):
    __tablename__ = 'specimen'
    
    code = Column(String(20), primary_key=True)
    # ... other fields ...
    date_collected = Column(Date)  # Ensure this exists
```

### Step 3: Test the Endpoint

```bash
# Restart API
docker-compose restart api

# Test
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01&end_date=2020-12-31&limit=5"

# Expected response:
{
  "specimens": [
    {
      "code": "CASENT0123456",
      "taxon_name": "camponotus pennsylvanicus",
      "country": "united states",
      "date_collected": "2020-06-15",
      "collected_by": "Smith, J.",
      "url": "https://www.antweb.org/specimen/CASENT0123456"
    }
  ],
  "count": 1,
  "limit": 5,
  "offset": 0,
  "start_date": "2020-01-01",
  "end_date": "2020-12-31"
}
```

### Step 4: Add to API Documentation

Update `API.md` with new endpoint documentation.

---

## Example 5: Modify Image Picker Logic

**Goal:** Change default image selection to prefer specimens with GPS coordinates.

### Step 1: Understand Current Logic

**File:** `src/org/calacademy/antweb/home/ImagePickDb.java`

Current method: `getDefaultSpecimenWithClause()`

```java
// Current query
String query = "select value from taxon_prop where "
    + taxonClause
    + " and prop = '" + Caste.getProp(caste) + "'"
    + " and value in (select code from specimen " + casteClause + ")"
    ;
```

### Step 2: Modify Query to Prefer Georeferenced

```java
private String getDefaultSpecimenWithClause(String caste, String taxonClause) throws SQLException {
    String defaultSpecimen = null;
    
    String casteClause = " where " + Caste.getSpecimenClause(caste);
    if (caste == null || Caste.DEFAULT.equals(caste)) 
        casteClause = " where is_worker = 1 or is_queen = 1";
    
    // MODIFIED QUERY: Prefer georeferenced specimens
    String query = "select value from taxon_prop where "
        + taxonClause
        + " and prop = '" + Caste.getProp(caste) + "'"
        + " and value in (select code from specimen " + casteClause 
        + " order by "
        + "   CASE WHEN decimal_latitude IS NOT NULL AND decimal_longitude IS NOT NULL THEN 0 ELSE 1 END, "
        + "   code "  // Secondary sort by code for consistency
        + " limit 1)"
        ;
    
    Statement stmt = null;
    ResultSet rset = null;
    try {
        stmt = DBUtil.getStatement(getConnection(), "getDefaultSpecimenWithClause()");
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            defaultSpecimen = rset.getString("value");
            break;
        }
    } catch (Exception e) {
        s_log.error("getDefaultSpecimenWithClause() e:" + e);
        throw e;
    } finally {
        DBUtil.close(stmt, rset, this, "getDefaultSpecimenWithClause()");
    }   
    return defaultSpecimen;
}
```

### Step 3: Test

```bash
# Deploy
docker-compose exec antweb ant deploy

# Test by visiting a taxon page
# Example: http://localhost/description.do?taxonName=camponotus

# Verify:
# 1. Default image shown
# 2. Check that specimen has GPS coordinates
# 3. Compare with previous default
```

### Step 4: Add Logging (Optional)

```java
if (defaultSpecimen != null) {
    s_log.info("Selected default specimen: " + defaultSpecimen + 
               " for taxon: " + taxonClause + ", caste: " + caste);
}
```

---

## Example 6: Add a New Search Filter

**Goal:** Add "Has DNA" filter to advanced search.

### Step 1: Add UI Element

**File:** `web/advancedSearch.jsp`

```jsp
<tr>
    <td><label>Has DNA Sample:</label></td>
    <td>
        <select name="hasDNA">
            <option value="">Any</option>
            <option value="yes">Yes</option>
            <option value="no">No</option>
        </select>
    </td>
</tr>
```

### Step 2: Modify Search Action

**File:** `src/org/calacademy/antweb/SearchAction.java`

```java
public ActionForward execute(ActionMapping mapping, ActionForm form,
                             HttpServletRequest request, HttpServletResponse response) {
    
    // ... existing code ...
    
    String genus = request.getParameter("genus");
    String country = request.getParameter("country");
    
    // ADD THIS:
    String hasDNA = request.getParameter("hasDNA");
    
    // Build search query
    SearchDb searchDb = new SearchDb(getConnection());
    List<Specimen> results = searchDb.advancedSearch(genus, country, hasDNA);
    
    // ... rest of code ...
}
```

### Step 3: Update Database Query

**File:** `src/org/calacademy/antweb/home/SearchDb.java`

```java
public List<Specimen> advancedSearch(String genus, String country, String hasDNA) 
        throws SQLException {
    
    StringBuilder query = new StringBuilder("SELECT * FROM specimen WHERE 1=1");
    List<Object> params = new ArrayList<>();
    
    if (genus != null && !genus.isEmpty()) {
        query.append(" AND genus = ?");
        params.add(genus);
    }
    
    if (country != null && !country.isEmpty()) {
        query.append(" AND country = ?");
        params.add(country);
    }
    
    // ADD THIS:
    if (hasDNA != null) {
        if ("yes".equals(hasDNA)) {
            query.append(" AND dna_notes IS NOT NULL AND dna_notes != ''");
        } else if ("no".equals(hasDNA)) {
            query.append(" AND (dna_notes IS NULL OR dna_notes = '')");
        }
    }
    
    query.append(" LIMIT 1000");
    
    // Execute query with prepared statement
    // ... existing code ...
}
```

### Step 4: Update Solr Query (if using Solr)

**File:** `src/org/calacademy/antweb/util/SolrUtil.java`

```java
public SolrQuery buildSearchQuery(Map<String, String> params) {
    SolrQuery query = new SolrQuery();
    
    // ... existing filters ...
    
    // ADD THIS:
    String hasDNA = params.get("hasDNA");
    if ("yes".equals(hasDNA)) {
        query.addFilterQuery("dna_notes:[* TO *]");  // Has content
    } else if ("no".equals(hasDNA)) {
        query.addFilterQuery("-dna_notes:[* TO *]"); // No content
    }
    
    return query;
}
```

### Step 5: Test

```bash
# Deploy
docker-compose exec antweb ant deploy

# Test
# 1. Go to http://localhost/advancedSearch.jsp
# 2. Select "Has DNA: Yes"
# 3. Submit search
# 4. Verify results all have DNA notes
```

---

## Example 7: Create a New Statistics Calculation

**Goal:** Calculate "average specimens per species" for each bioregion.

### Step 1: Add Column to Store Result

```sql
-- db/upgrade/8.106/2026-01-15-bioregion-avg-specimens.sql

ALTER TABLE bioregion 
ADD COLUMN avg_specimens_per_species DECIMAL(10, 2) NULL
AFTER specimen_count;
```

### Step 2: Create Calculation Method

**File:** `src/org/calacademy/antweb/home/BioregionDb.java`

```java
public void calculateAverageSpecimens() throws SQLException {
    String query = 
        "UPDATE bioregion b " +
        "SET avg_specimens_per_species = ( " +
        "    SELECT AVG(specimen_count) " +
        "    FROM ( " +
        "        SELECT COUNT(s.code) as specimen_count " +
        "        FROM specimen s " +
        "        JOIN taxon t ON s.taxon_name = t.taxon_name " +
        "        WHERE s.biogeographic_region = b.name " +
        "          AND t.rank = 'species' " +
        "        GROUP BY s.taxon_name " +
        "    ) species_counts " +
        ")";
    
    try (Statement stmt = getConnection().createStatement()) {
        int updated = stmt.executeUpdate(query);
        s_log.info("Updated avg_specimens_per_species for " + updated + " bioregions");
    }
}
```

### Step 3: Add to Statistics Recrawl

**File:** `src/org/calacademy/antweb/home/StatisticsDb.java`

```java
public void recrawl() throws SQLException {
    s_log.info("Starting statistics recrawl...");
    
    // ... existing recrawl methods ...
    
    // ADD THIS:
    BioregionDb bioregionDb = new BioregionDb(getConnection());
    bioregionDb.calculateAverageSpecimens();
    
    s_log.info("Statistics recrawl complete");
}
```

### Step 4: Display in UI

**File:** `web/bioregion.jsp`

```jsp
<tr>
    <td><strong>Avg Specimens/Species:</strong></td>
    <td><bean:write name="bioregion" property="avgSpecimensPerSpecies" format="0.00"/></td>
</tr>
```

### Step 5: Manual Trigger

```bash
# Access admin tools
# Visit: http://localhost/util.do?action=recrawlStatistics
```

---

## Example 8: Add a User Permission

**Goal:** Add "can_export_data" permission to control who can download CSV exports.

### Step 1: Add Database Column

```sql
-- db/upgrade/8.106/2026-01-15-export-permission.sql

ALTER TABLE login 
ADD COLUMN can_export_data TINYINT(1) DEFAULT 0
AFTER is_active;

-- Grant to admins
UPDATE login 
SET can_export_data = 1 
WHERE role = 'admin';
```

### Step 2: Update Login Model

**File:** `src/org/calacademy/antweb/Login.java`

```java
private boolean canExportData;

public boolean getCanExportData() {
    return canExportData;
}

public void setCanExportData(boolean canExportData) {
    this.canExportData = canExportData;
}
```

### Step 3: Update LoginDb

**File:** `src/org/calacademy/antweb/home/LoginDb.java`

```java
public Login getLogin(String email) throws SQLException {
    // ... existing code ...
    
    while (rs.next()) {
        login.setCanExportData(rs.getBoolean("can_export_data"));
        // ... other fields ...
    }
    
    return login;
}
```

### Step 4: Add Permission Check

**File:** `src/org/calacademy/antweb/ExportAction.java`

```java
public ActionForward execute(...) {
    // Get current user
    Login user = (Login) request.getSession().getAttribute("login");
    
    // Check permission
    if (user == null || !user.getCanExportData()) {
        request.setAttribute("error", "You do not have permission to export data");
        return mapping.findForward("unauthorized");
    }
    
    // Proceed with export
    // ...
}
```

### Step 5: Update UI

**File:** `web/search.jsp`

```jsp
<%-- Only show export button if user has permission --%>
<logic:present name="login">
    <logic:equal name="login" property="canExportData" value="true">
        <html:form action="/export">
            <html:submit value="Export to CSV"/>
        </html:form>
    </logic:equal>
</logic:present>
```

---

## Testing Your Changes

After each modification, follow this checklist:

1. **Database Changes:**
   - [ ] Migration applied successfully
   - [ ] New columns/tables exist
   - [ ] Indexes created
   - [ ] Test data inserted

2. **Java Code:**
   - [ ] Compiles without errors (`ant compile`)
   - [ ] No null pointer exceptions
   - [ ] Logging statements added
   - [ ] Error handling implemented

3. **Web Interface:**
   - [ ] Page loads without errors
   - [ ] Data displays correctly
   - [ ] Forms submit successfully
   - [ ] Validation works

4. **API:**
   - [ ] Endpoint responds
   - [ ] JSON valid
   - [ ] Errors handled gracefully
   - [ ] Documentation updated

5. **Integration:**
   - [ ] Related features still work
   - [ ] No broken links
   - [ ] No console errors
   - [ ] Performance acceptable

---

## Common Mistakes to Avoid

1. **Forgetting to increment field indices** when adding columns to specimen upload
2. **Not handling null values** in database queries and Java code
3. **Missing PreparedStatement parameters** (? placeholders)
4. **Not restarting containers** after code changes
5. **Inconsistent naming** between database, Java, and JSP
6. **No error handling** in try-catch blocks
7. **Forgetting to close database connections**
8. **Not testing with real data** - use production-like test data
9. **Breaking existing tests** - run full test suite
10. **Not updating documentation** - update API.md, DATABASE.md, etc.

---

## Next Steps

- **For testing:** See [TESTING.md](TESTING.md)
- **For feature locations:** See [FEATURES.md](FEATURES.md)
- **For database details:** See [DATABASE.md](DATABASE.md)
- **For development setup:** See [DEVELOPMENT.md](DEVELOPMENT.md)
