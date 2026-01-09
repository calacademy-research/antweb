# AntWeb Testing Guide

## Purpose

This document explains how to test your changes to ensure they work correctly and don't break existing functionality.

**Types of Testing Covered:**
- Manual testing in browser
- Database testing
- API testing
- Integration testing
- Performance testing

---

## Table of Contents

1. [Testing Checklist](#testing-checklist)
2. [Manual Testing](#manual-testing)
3. [Database Testing](#database-testing)
4. [API Testing](#api-testing)
5. [Integration Testing](#integration-testing)
6. [Performance Testing](#performance-testing)
7. [Common Test Scenarios](#common-test-scenarios)
8. [Debugging Failed Tests](#debugging-failed-tests)

---

## Testing Checklist

Use this checklist for every change you make:

### Before Starting
- [ ] Read relevant documentation (FEATURES.md, DATABASE.md)
- [ ] Understand what you're changing
- [ ] Know how to revert if something breaks

### During Development
- [ ] Test each small change incrementally
- [ ] Check logs for errors after each change
- [ ] Verify database queries return expected results

### After Completing Change
- [ ] Run through manual testing scenarios
- [ ] Test edge cases (null values, empty strings, etc.)
- [ ] Verify related features still work
- [ ] Check for console errors in browser
- [ ] Review application logs

### Before Committing
- [ ] All tests pass
- [ ] No console errors
- [ ] Documentation updated
- [ ] Ready for code review

---

## Manual Testing

### Testing a New Page

**Example:** Testing the "Recent Uploads" page from EXAMPLES.md

**Step 1: Access the Page**
```
URL: http://localhost/recentUploads.do
Expected: Page loads without errors
```

**Step 2: Visual Inspection**
- [ ] Page layout correct (no broken CSS)
- [ ] All elements visible
- [ ] No JavaScript errors in console (F12)
- [ ] Navigation works

**Step 3: Data Display**
- [ ] Table shows uploads
- [ ] Dates formatted correctly
- [ ] All columns populated
- [ ] No "null" or "undefined" text

**Step 4: Edge Cases**
- [ ] Empty results handled gracefully
- [ ] Large result sets don't break layout
- [ ] Special characters display correctly

**Step 5: Browser Compatibility**
Test in:
- [ ] Chrome
- [ ] Firefox
- [ ] Safari (if available)

### Testing a Modified Field

**Example:** Testing the new `preservation_method` field

**Step 1: Upload Test Data**
```bash
# Create test file
cat > test-specimen.txt << 'EOF'
Count	SpecimenCode	...	PreservationMethod	...
1	TEST0001	...	alcohol	...
EOF

# Upload via web interface
# http://localhost/upload.do
```

**Step 2: Verify Database**
```sql
SELECT code, preservation_method 
FROM specimen 
WHERE code = 'TEST0001';

-- Expected: preservation_method = 'alcohol'
```

**Step 3: Verify Display**
```
URL: http://localhost/specimen/TEST0001
Expected: "Preservation: alcohol" displays
```

**Step 4: Test Variations**
Upload specimens with:
- [ ] preservation_method = "dried"
- [ ] preservation_method = "" (empty)
- [ ] preservation_method = NULL (missing column)
- [ ] Very long preservation_method value

**Step 5: Verify No Regression**
- [ ] Old specimens still display correctly
- [ ] Specimens without preservation_method display fine

---

## Database Testing

### Testing Database Changes

**Example:** Testing a new column addition

**Step 1: Verify Migration Applied**
```bash
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant
```

```sql
-- Check table structure
DESCRIBE bioregion;

-- Expected: last_survey_date column exists
-- Type: date
-- Null: YES
-- Key: (none or MUL if indexed)
```

**Step 2: Test Insert**
```sql
INSERT INTO bioregion (name, title, last_survey_date)
VALUES ('test_region', 'Test Region', '2025-06-15');

-- Expected: Query OK, 1 row affected
```

**Step 3: Test Update**
```sql
UPDATE bioregion 
SET last_survey_date = '2025-07-01' 
WHERE name = 'test_region';

-- Expected: Query OK, 1 row affected
```

**Step 4: Test Query**
```sql
SELECT name, last_survey_date 
FROM bioregion 
WHERE last_survey_date >= '2025-01-01';

-- Expected: Returns rows with dates >= 2025-01-01
```

**Step 5: Test NULL Handling**
```sql
SELECT name, last_survey_date 
FROM bioregion 
WHERE last_survey_date IS NULL;

-- Expected: Returns rows with NULL dates (old records)
```

**Step 6: Clean Up**
```sql
DELETE FROM bioregion WHERE name = 'test_region';
```

### Testing Queries for Performance

**Step 1: Check Query Plan**
```sql
EXPLAIN SELECT * FROM specimen WHERE country = 'United States';

-- Look for:
-- type: ref (good) or ALL (bad - full table scan)
-- possible_keys: country index
-- key: country index being used
-- rows: estimated rows scanned
```

**Step 2: Test Query Speed**
```sql
-- Time a query
SET profiling = 1;
SELECT * FROM specimen WHERE country = 'United States' LIMIT 100;
SHOW PROFILES;

-- Expected: < 0.1 seconds for indexed queries
-- Warning: > 1 second indicates missing index or bad query
```

**Step 3: Test with Large Result Sets**
```sql
-- Count results
SELECT COUNT(*) FROM specimen WHERE country = 'United States';

-- If > 10,000 results, ensure pagination implemented
```

### Testing Data Integrity

**Referential Integrity:**
```sql
-- Check for orphaned records
SELECT s.code 
FROM specimen s 
LEFT JOIN taxon t ON s.taxon_name = t.taxon_name 
WHERE t.taxon_name IS NULL;

-- Expected: Empty result (no orphans)
```

**Required Fields:**
```sql
-- Check for NULL values in required fields
SELECT COUNT(*) 
FROM specimen 
WHERE code IS NULL OR taxon_name IS NULL;

-- Expected: 0
```

---

## API Testing

### Testing a New Endpoint

**Example:** Testing `/api/v3/specimens/by-date`

**Step 1: Basic Request**
```bash
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01&end_date=2020-12-31&limit=5"
```

**Expected Response:**
```json
{
  "specimens": [...],
  "count": 5,
  "limit": 5,
  "offset": 0,
  "start_date": "2020-01-01",
  "end_date": "2020-12-31"
}
```

**Check:**
- [ ] HTTP status 200
- [ ] Valid JSON
- [ ] All expected fields present
- [ ] Data types correct

**Step 2: Test Validation**

**Missing required parameter:**
```bash
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01"
# Expected: 400 Bad Request, error message
```

**Invalid date format:**
```bash
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-13-45&end_date=2020-12-31"
# Expected: 400 Bad Request, error message
```

**Step 3: Test Pagination**
```bash
# First page
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01&end_date=2020-12-31&limit=10&offset=0"

# Second page
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01&end_date=2020-12-31&limit=10&offset=10"

# Check:
# - Different results
# - Correct offset value in response
```

**Step 4: Test Edge Cases**

**No results:**
```bash
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=1900-01-01&end_date=1900-01-02"
# Expected: {"specimens": [], "count": 0, ...}
```

**Large result set:**
```bash
curl "http://localhost:5000/api/v3/specimens/by-date?start_date=1900-01-01&end_date=2030-12-31&limit=1000"
# Expected: Returns up to 1000 results, no timeout
```

**Step 5: Test Performance**
```bash
# Time the request
time curl "http://localhost:5000/api/v3/specimens/by-date?start_date=2020-01-01&end_date=2020-12-31"

# Expected: < 1 second for simple queries
```

### Testing Modified Endpoints

**Compare old vs new behavior:**
```bash
# Get specimen before change
curl "http://localhost:5000/api/v3/specimens/TEST0001" > before.json

# Make change, restart API
docker-compose restart api

# Get specimen after change
curl "http://localhost:5000/api/v3/specimens/TEST0001" > after.json

# Compare
diff before.json after.json

# Expected: Only new field differs
```

### API Testing Tools

**Using Postman:**
1. Import collection (if exists)
2. Set base URL: `http://localhost:5000`
3. Create test cases for each endpoint
4. Run collection tests

**Using Python:**
```python
import requests

def test_specimens_by_date():
    # Test successful request
    response = requests.get(
        'http://localhost:5000/api/v3/specimens/by-date',
        params={
            'start_date': '2020-01-01',
            'end_date': '2020-12-31',
            'limit': 5
        }
    )
    
    assert response.status_code == 200
    data = response.json()
    assert 'specimens' in data
    assert 'count' in data
    assert data['count'] <= 5
    
    # Test validation
    response = requests.get(
        'http://localhost:5000/api/v3/specimens/by-date',
        params={'start_date': '2020-01-01'}  # Missing end_date
    )
    
    assert response.status_code == 400
    assert 'error' in response.json()

if __name__ == '__main__':
    test_specimens_by_date()
    print("All tests passed!")
```

---

## Integration Testing

### Testing a Complete Workflow

**Example:** Specimen Upload → Display → API

**Step 1: Upload Specimen**
```bash
# Create test file
cat > test-integration.txt << 'EOF'
Count	SpecimenCode	Subfamily	Genus	Species	...
1	TEST_INT_001	Formicinae	Camponotus	pennsylvanicus	...
EOF

# Upload via web interface
# http://localhost/upload.do
```

**Step 2: Verify Database**
```sql
SELECT * FROM specimen WHERE code = 'TEST_INT_001';
-- Expected: Row exists with correct data
```

**Step 3: Test Web Display**
```
URL: http://localhost/specimen/TEST_INT_001
Expected: Page displays with all fields
```

**Step 4: Test API**
```bash
curl "http://localhost:5000/api/v3/specimens/TEST_INT_001"
# Expected: JSON with correct data
```

**Step 5: Test Search**
```
URL: http://localhost/search.do?q=TEST_INT_001
Expected: Specimen appears in results
```

**Step 6: Clean Up**
```sql
DELETE FROM specimen WHERE code = 'TEST_INT_001';
```

### Testing Related Features

**When modifying bioregion, test:**
- [ ] Bioregion page displays correctly
- [ ] Geographic search works
- [ ] Statistics updated
- [ ] API returns correct data
- [ ] Navigation links work

**When modifying specimen upload, test:**
- [ ] Upload succeeds
- [ ] Validation catches errors
- [ ] Database updated correctly
- [ ] Related tables updated (geolocale_taxon, etc.)
- [ ] Search index updated
- [ ] Statistics recalculated

---

## Performance Testing

### Database Performance

**Step 1: Test Query Speed**
```sql
-- Enable profiling
SET profiling = 1;

-- Run your query
SELECT * FROM specimen WHERE country = 'United States' LIMIT 100;

-- Check timing
SHOW PROFILES;

-- Expected: < 0.1 seconds for small queries, < 1 second for complex queries
```

**Step 2: Identify Slow Queries**
```bash
# Check slow query log
docker-compose exec mysql tail -f /var/log/mysql/slow-query.log
```

**Step 3: Optimize if Needed**
```sql
-- Add missing indexes
CREATE INDEX idx_country ON specimen(country);

-- Re-test query
```

### Application Performance

**Step 1: Measure Page Load Time**
```
Open browser DevTools (F12)
Go to Network tab
Load page: http://localhost/bioregion.do?name=nearctic
Check: Total load time < 2 seconds
```

**Step 2: Check for N+1 Queries**

Enable SQL logging and watch for repeated similar queries:
```
2025-01-15 10:00:01 SELECT * FROM taxon WHERE taxon_name = 'camponotus'
2025-01-15 10:00:01 SELECT * FROM taxon WHERE taxon_name = 'formica'
2025-01-15 10:00:01 SELECT * FROM taxon WHERE taxon_name = 'lasius'
...
```

**Fix:** Batch queries or use JOIN

### API Performance

**Step 1: Benchmark Endpoint**
```bash
# Install apache bench
# Ubuntu: sudo apt-get install apache2-utils
# macOS: (already installed)

# Test endpoint
ab -n 100 -c 10 "http://localhost:5000/api/v3/specimens?limit=10"

# Check:
# Requests per second: > 10
# Mean response time: < 100ms
# Failed requests: 0
```

**Step 2: Test Under Load**
```bash
# 1000 requests, 50 concurrent
ab -n 1000 -c 50 "http://localhost:5000/api/v3/taxa?rank=genus&limit=20"

# Expected: No timeouts, consistent response times
```

---

## Common Test Scenarios

### Scenario 1: Testing Specimen Upload

```bash
# 1. Create valid specimen file
cat > test-valid.txt << 'EOF'
Count	SpecimenCode	Subfamily	Genus	Species	LifeStageSex	Medium	...
1	TEST001	Formicinae	Camponotus	pennsylvanicus	1w	pin	...
EOF

# 2. Upload and verify success
# 3. Create invalid specimen file (missing required field)
cat > test-invalid.txt << 'EOF'
Count	SpecimenCode	Subfamily	...
1		Formicinae	...
EOF

# 4. Upload and verify error caught
```

**Check:**
- [ ] Valid upload succeeds
- [ ] Invalid upload rejected with helpful error
- [ ] Partial uploads don't corrupt database
- [ ] Upload log created

### Scenario 2: Testing Search

```bash
# 1. Upload test specimen with unique code
# 2. Wait 5 minutes (for Solr indexing) or manually trigger reindex
# 3. Search for unique code
# 4. Verify specimen appears in results
```

**Check:**
- [ ] Search finds new specimen
- [ ] Search result links work
- [ ] Filters work correctly
- [ ] Pagination works

### Scenario 3: Testing Image Upload

```bash
# 1. Prepare test image: TEST002_h_1.jpg
# 2. Upload via web interface
# 3. Check processing completes
# 4. Verify thumbnail, medium, high-res created
# 5. Check image displays on specimen page
```

**Check:**
- [ ] All image sizes created
- [ ] EXIF data extracted
- [ ] Image associated with specimen
- [ ] Image picker can select it

### Scenario 4: Testing Permission Change

```bash
# 1. Login as regular user
# 2. Try to access admin page
# 3. Expected: Access denied
# 4. Grant permission
# 5. Try again
# 6. Expected: Access granted
```

**Check:**
- [ ] Unauthorized users blocked
- [ ] Authorized users allowed
- [ ] Error messages helpful
- [ ] No security bypass

---

## Debugging Failed Tests

### Issue: Page Shows 404 Error

**Check:**
1. Action registered in `struts-config.xml`?
2. JSP file exists in correct location?
3. Correct URL path?
4. Tomcat restarted after config change?

**Debug:**
```bash
# Check Tomcat logs
docker-compose logs antweb | grep ERROR

# Check Struts routing
docker-compose logs antweb | grep "Request processing failed"
```

### Issue: Database Query Returns No Results

**Check:**
1. Data actually in database?
2. Query syntax correct?
3. Correct table/column names?
4. Case sensitivity issues?

**Debug:**
```sql
-- Test query step by step
SELECT COUNT(*) FROM specimen;  -- Any data?
SELECT COUNT(*) FROM specimen WHERE country = 'United States';  -- Filter works?
SELECT * FROM specimen WHERE country = 'United States' LIMIT 1;  -- Show data
```

### Issue: API Returns 500 Error

**Check:**
1. API container running?
2. Database connection works?
3. Python syntax error?

**Debug:**
```bash
# Check API logs
docker-compose logs api | tail -50

# Test database connection
docker-compose exec api python3 -c "
import pymysql
conn = pymysql.connect(host='mysql', user='antweb', password='f0rm1c6', db='ant')
print('Connected!')
conn.close()
"

# Check Python syntax
docker-compose exec api python3 -m py_compile /app/api.py
```

### Issue: Changes Not Appearing

**Check:**
1. Code deployed? (`ant deploy`)
2. Container restarted?
3. Browser cache cleared?
4. Correct file edited?

**Debug:**
```bash
# Verify file timestamp
docker-compose exec antweb ls -l /antweb/deploy/WEB-INF/classes/org/calacademy/antweb/MyClass.class

# Check compilation errors
docker-compose exec antweb ant compile 2>&1 | grep error

# Force browser refresh
# Ctrl+Shift+R (Chrome/Firefox)
# Cmd+Shift+R (macOS)
```

### Issue: Performance Degradation

**Check:**
1. Missing database indexes?
2. N+1 query problem?
3. Large result set?
4. Memory leak?

**Debug:**
```sql
-- Find slow queries
SHOW FULL PROCESSLIST;

-- Check table statistics
SHOW TABLE STATUS LIKE 'specimen';

-- Analyze query
EXPLAIN SELECT ...;
```

---

## Test Data Management

### Creating Test Data

**Minimal test specimen:**
```sql
INSERT INTO specimen (
    code, taxon_name, subfamily, genus, species,
    country, access_group
) VALUES (
    'TEST_MIN_001',
    'formicinae',
    'formicinae',
    'camponotus',
    'pennsylvanicus',
    'united states',
    1
);
```

**Complete test specimen:**
```sql
INSERT INTO specimen (
    code, taxon_name, subfamily, genus, species, subspecies,
    life_stage, caste, medium, type_status,
    determined_by, date_determined,
    collected_by, date_collected,
    country, adm1, decimal_latitude, decimal_longitude,
    biogeographic_region, access_group
) VALUES (
    'TEST_FULL_001',
    'camponotus pennsylvanicus',
    'formicinae',
    'camponotus',
    'pennsylvanicus',
    NULL,
    'adult',
    'worker',
    'pin',
    NULL,
    'Smith, J.',
    '2025-01-15',
    'Doe, J.',
    '2024-06-15',
    'united states',
    'california',
    37.7749,
    -122.4194,
    'nearctic',
    1
);
```

### Cleaning Up Test Data

**After each test:**
```sql
-- Delete test specimens
DELETE FROM specimen WHERE code LIKE 'TEST_%';

-- Delete test taxa (if created)
DELETE FROM taxon WHERE taxon_name LIKE 'test%';

-- Delete test uploads
DELETE FROM upload WHERE file_name LIKE 'test%';

-- Clean up related tables
DELETE FROM image WHERE specimen_code LIKE 'TEST_%';
DELETE FROM geolocale_taxon WHERE taxon_name LIKE 'test%';
```

**Full database reset (dev only!):**
```bash
# Stop containers
docker-compose down

# Remove database volume
docker volume rm antweb_database

# Restore from backup
# (see DEVELOPMENT.md for full process)
```

---

## Continuous Testing

### During Development

**After every code change:**
1. Compile: `docker-compose exec antweb ant compile`
2. Check logs: `docker-compose logs antweb | tail -20`
3. Test the specific feature you changed
4. Quick regression test (main pages load)

### Before Committing

**Full test run:**
1. All manual tests pass
2. All integration tests pass
3. No console errors
4. No database errors
5. Documentation updated
6. Ready for review

### Regular Maintenance

**Weekly:**
- Check slow query log
- Review error logs
- Test backup/restore process
- Performance benchmark

**Monthly:**
- Full database optimization
- Security audit
- Dependency updates
- Load testing

---

## Test Documentation

When you add a new feature, document how to test it:

```markdown
## Testing [Feature Name]

### Setup
1. [Prerequisites]
2. [Test data needed]

### Test Cases
1. **Happy path:** [Expected behavior]
   - Steps: ...
   - Expected: ...

2. **Error handling:** [Invalid input]
   - Steps: ...
   - Expected: Error message

3. **Edge cases:** [Boundary conditions]
   - Steps: ...
   - Expected: Graceful handling

### Cleanup
- [How to remove test data]
```

---

## Troubleshooting Guide

| Problem | Likely Cause | Solution |
|---------|--------------|----------|
| Page won't load | Struts config | Check `struts-config.xml`, restart Tomcat |
| Database error | Connection issue | Check credentials, MySQL running |
| API 500 error | Python exception | Check API logs, fix code |
| No search results | Index not updated | Rebuild Solr index |
| Slow queries | Missing index | Run EXPLAIN, add indexes |
| Upload fails | File format | Check encoding, column count |
| Images not displaying | Path incorrect | Check file system, permissions |
| Permission denied | Access control | Check user role, access_group |

---

## Quick Reference: Test Commands

```bash
# Database
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant

# View logs
docker-compose logs -f antweb
docker-compose logs -f api
docker-compose logs -f mysql

# Compile Java
docker-compose exec antweb ant compile

# Deploy
docker-compose exec antweb ant deploy

# Restart services
docker-compose restart antweb
docker-compose restart api

# Test API
curl http://localhost:5000/api/v3/specimens?limit=1

# Check running containers
docker-compose ps

# Enter container shell
docker-compose exec antweb bash
docker-compose exec api bash
```

---

## Summary

Testing is essential! Always:

1. **Test incrementally** - Don't make 10 changes then test
2. **Test edge cases** - NULL, empty, very long values
3. **Test integration** - Features interact with each other
4. **Check logs** - Errors often appear in logs first
5. **Clean up** - Remove test data after testing
6. **Document tests** - Help future developers

**Remember:** A bug found in testing is much cheaper than a bug found in production!

---

## Next Steps

- **For making changes:** See [EXAMPLES.md](EXAMPLES.md)
- **For feature locations:** See [FEATURES.md](FEATURES.md)
- **For database details:** See [DATABASE.md](DATABASE.md)
- **For development setup:** See [DEVELOPMENT.md](DEVELOPMENT.md)
