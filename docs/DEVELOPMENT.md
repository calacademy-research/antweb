# AntWeb Development Guide

## Getting Started

This guide covers setting up a development environment, understanding the codebase structure, making changes, and contributing to AntWeb.

## Prerequisites

### Required Software

- **Git** - Version control
- **Docker** - Containerization (Docker Engine 20+)
- **Docker Compose** - Multi-container orchestration (v2.0+)
- **Text Editor/IDE** - VS Code, IntelliJ IDEA, or similar

### Recommended Software

- **MySQL Workbench** - Database management
- **Postman/Insomnia** - API testing
- **Java JDK 11+** - For local Java development (optional)
- **Python 3.6+** - For API development (optional)

### System Requirements

- **OS:** Linux, macOS, or Windows 10+ (WSL2)
- **RAM:** 8 GB minimum, 16 GB recommended
- **Disk:** 50 GB free space (for database + images)
- **Network:** VPN access required for production data access

## Initial Setup

### 1. Clone the Repository

```bash
git clone git@github.com:calacademy-research/antweb.git
cd antweb
```

### 2. Set Up Docker BuildKit

Add to `~/.bashrc` or `~/.zshrc`:

```bash
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
```

Then reload:
```bash
source ~/.bashrc  # or source ~/.zshrc
```

### 3. Choose Development Data Source

You have two options for getting development data:

#### Option A: Download to Local Machine (Recommended for Development)

**Advantages:**
- Faster access
- Can work offline
- Full control over data

**Setup:**

1. Install rclone:
```bash
# macOS
brew install rclone

# Linux
curl https://rclone.org/install.sh | sudo bash
```

2. Configure rclone (get credentials from team):
```bash
cat >> ~/.config/rclone/rclone.conf << EOF
[digitalocean]
type = s3
provider = DigitalOcean
access_key_id = PIM6E5G3RZ6ANNAAJGKB
secret_access_key = <GET_FROM_TEAM>
endpoint = sfo3.digitaloceanspaces.com
acl = public-read
EOF
```

3. Download project files (~10 GB, required):
```bash
mkdir -p data/web
rclone sync --size-only -P --exclude "upload/**" digitalocean:antweb/web/ data/web/
```

4. Optionally download images (~1.6 TB, takes hours):
```bash
mkdir -p data/images
rclone sync --size-only -P --checkers 32 --fast-list digitalocean:antweb/images data/images
```

5. Set environment variable:
```bash
export ANTWEB_BUCKET_PATH=$(pwd)/data
echo "ANTWEB_BUCKET_PATH=$ANTWEB_BUCKET_PATH" >> .env
```

**Note:** If you skip images, set `site.imgDomain=www.antweb.org` in `WEB-INF/classes/AntwebResources.properties` to serve images from production.

#### Option B: Mount via VPN (For Staging/Production-like Setup)

**Advantages:**
- No large downloads
- Always up-to-date data
- Less disk space required

**Setup:**

1. Connect to California Academy of Sciences VPN

2. Install s3fs:
```bash
# macOS
brew install s3fs

# Ubuntu
sudo apt-get install s3fs
```

3. Create mount points:
```bash
mkdir -p ~/volumes/antweb ~/volumes/antweb_backup
```

4. Create credentials file:
```bash
echo "antweb:<SECRET_KEY>" > ~/.s3fs_antweb_key
chmod 600 ~/.s3fs_antweb_key
```

5. Mount buckets:
```bash
s3fs antweb ~/volumes/antweb \
  -o passwd_file=~/.s3fs_antweb_key \
  -o url=https://sfo3.digitaloceanspaces.com \
  -o allow_other \
  -o use_path_request_style

s3fs antweb-dbarchive ~/volumes/antweb_backup \
  -o passwd_file=~/.s3fs_antweb_key \
  -o url=https://sfo3.digitaloceanspaces.com \
  -o allow_other \
  -o use_path_request_style
```

6. Set environment variables:
```bash
export ANTWEB_BUCKET_PATH=$HOME/volumes/antweb
export ANTWEB_BACKUP_PATH=$HOME/volumes/antweb_backup
echo "ANTWEB_BUCKET_PATH=$ANTWEB_BUCKET_PATH" >> .env
echo "ANTWEB_BACKUP_PATH=$ANTWEB_BACKUP_PATH" >> .env
```

### 4. Load Database

1. Download latest database snapshot:
```bash
# From production server (requires SSH access)
scp root@antweb.org:/root/ant-currentDump.sql.gz ./

# Or from backup bucket (if mounted)
cp ~/volumes/antweb_backup/ant-currentDump.sql.gz ./
```

2. Create and load database:
```bash
# Create volume
docker volume create antweb_database

# Start temporary MySQL container
CID=$(docker run -d --rm \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=1 \
  -e MYSQL_DATABASE=ant \
  --mount source=antweb_database,target=/var/lib/mysql \
  mysql:5-debian \
  --innodb-buffer-pool-size=4G \
  --innodb-log-file-size=512M \
  --innodb-buffer-pool-instances=4)

# Wait for MySQL to start
sleep 15

# Create user
docker exec -it $CID mysql -uroot ant -e \
  "CREATE USER antweb@'%' IDENTIFIED BY 'f0rm1c6'; \
   GRANT ALL ON *.* TO antweb@'%' WITH GRANT OPTION"

# Load database
gunzip -c ant-currentDump.sql.gz | docker exec -i $CID sh -c "exec mysql -uroot ant"

# Optimize tables
docker exec -it $CID sh -c "exec mysqlcheck --all-databases --analyze -uroot"

# Stop container (volume persists)
docker stop $CID

# Clean up
rm ant-currentDump.sql.gz
```

### 5. Create Caddy Volume

```bash
docker volume create antweb_caddy_data
```

### 6. Set Up Development Override

```bash
ln -sf docker-compose.dev.yml docker-compose.override.yml
```

### 7. Start Services

```bash
docker-compose up -d
```

**Services Started:**
- `antweb` - Main application on http://localhost
- `mysql` - Database on localhost:3306
- `caddy` - Web server/proxy

### 8. Verify Installation

Visit http://localhost in your browser. You should see the AntWeb homepage.

**Check logs:**
```bash
docker-compose logs -f antweb
```

## Project Structure

```
antweb/
├── src/                          # Java source code
│   └── org/calacademy/antweb/
│       ├── home/                 # Database access layer (*Db.java)
│       ├── util/                 # Utilities
│       ├── upload/               # Upload processing
│       ├── geolocale/            # Geographic data
│       └── ...
├── web/                          # Frontend (JSP, JS, CSS)
│   ├── common/                   # Shared templates
│   ├── specimen.jsp              # Specimen page
│   ├── description.jsp           # Taxon page
│   └── ...
├── WEB-INF/                      # Web app configuration
│   ├── web.xml                   # Servlet config
│   ├── struts-config.xml         # Struts routing
│   └── classes/
│       └── AntwebResources.properties  # Configuration
├── api/                          # Python API
│   └── v3/
│       ├── api.py                # Flask application
│       └── home/                 # SQLAlchemy models
├── db/                           # Database files
│   ├── upgrade/                  # Migration scripts
│   └── lookup/                   # Reference data
├── doc/                          # Documentation
├── test/                         # Test files
├── docker/                       # Docker configurations
├── build.xml                     # Ant build script
└── docker-compose*.yml           # Docker compose files
```

## Development Workflow

### Making Code Changes

#### Java/Struts Changes

1. Edit Java files in `src/` or JSP files in `web/`

2. Rebuild and deploy:
```bash
docker-compose exec antweb ant deploy
```

3. View changes at http://localhost

4. Check logs:
```bash
docker-compose logs -f antweb
```

#### Configuration Changes

1. Edit configuration files:
   - `WEB-INF/classes/AntwebResources.properties` - Application config
   - `WEB-INF/struts-config.xml` - URL routing
   - `WEB-INF/web.xml` - Servlet configuration

2. Rebuild:
```bash
docker-compose exec antweb ant deploy
docker-compose restart antweb
```

#### Database Changes

1. Create SQL migration file:
```bash
# Create directory for your version
mkdir -p db/upgrade/8.106

# Create dated SQL file
cat > db/upgrade/8.106/2026-01-15.sql << EOF
-- Add new column
ALTER TABLE specimen ADD COLUMN new_field VARCHAR(128);

-- Add index
CREATE INDEX idx_new_field ON specimen(new_field);
EOF
```

2. Apply migration:
```bash
# Connect to database
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant

# Run migration
mysql> SOURCE /path/to/migration.sql;
```

3. Update Java models if needed

#### Python API Changes

1. Edit files in `api/v3/`

2. Restart API container:
```bash
docker-compose restart api
```

3. Test endpoint:
```bash
curl http://localhost:5000/api/v3/specimens?limit=1
```

### Testing Changes

#### Manual Testing

1. **Browse the site:** http://localhost
2. **Test specimen page:** http://localhost/specimen/CASENT0106322
3. **Test taxon page:** http://localhost/description.do?taxonName=formicinae
4. **Test search:** http://localhost/search.do?searchMethod=advancedSearch&genus=camponotus

#### Database Queries

```bash
# Connect to MySQL
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant

# Run queries
mysql> SELECT COUNT(*) FROM specimen;
mysql> SELECT * FROM taxon WHERE taxon_name = 'formicinae';
```

#### API Testing

```bash
# Test specimens endpoint
curl http://localhost:5000/api/v3/specimens?genus=camponotus&limit=5

# Test specific specimen
curl http://localhost:5000/api/v3/specimens/CASENT0106322

# Test taxa endpoint
curl http://localhost:5000/api/v3/taxa?rank=genus&limit=10
```

### Debugging

#### Java Application

1. **Enable debug logging:**

Edit `WEB-INF/classes/log4j.properties`:
```properties
log4j.rootLogger=DEBUG, stdout, file
```

2. **View logs:**
```bash
docker-compose logs -f antweb | grep DEBUG
```

3. **Interactive debugging:**
   - Use IntelliJ IDEA remote debugging
   - Connect to port 8000 (configured in docker-compose.dev.yml)

#### Database

1. **Check slow queries:**
```bash
docker-compose exec mysql cat /var/log/mysql/slow-query.log
```

2. **Explain query plan:**
```sql
EXPLAIN SELECT * FROM specimen WHERE taxon_name = 'formicinae';
```

3. **Check indexes:**
```sql
SHOW INDEXES FROM specimen;
```

#### Python API

1. **Enable debug mode:**

Edit `api/v3/api.py`:
```python
application.debug = True
```

2. **View logs:**
```bash
docker-compose logs -f api
```

## Common Development Tasks

### Add a New Page

1. **Create JSP template:**
```bash
cat > web/mypage.jsp << EOF
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>

<jsp:include page="common/header.jsp"/>

<h1>My New Page</h1>
<p>Content goes here</p>

<jsp:include page="common/footer.jsp"/>
EOF
```

2. **Add Struts action:**

Create `src/org/calacademy/antweb/MyAction.java`:
```java
package org.calacademy.antweb;

import javax.servlet.http.*;
import org.apache.struts.action.*;

public class MyAction extends Action {
    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response) {
        // Your logic here
        return mapping.findForward("success");
    }
}
```

3. **Register in Struts config:**

Edit `WEB-INF/struts-config.xml`:
```xml
<action path="/myPage"
        type="org.calacademy.antweb.MyAction">
    <forward name="success" path="/mypage.jsp"/>
</action>
```

4. **Deploy and test:**
```bash
docker-compose exec antweb ant deploy
# Visit http://localhost/myPage.do
```

### Add a Database Table

1. **Create migration:**
```sql
-- db/upgrade/8.106/2026-01-15-new-table.sql
CREATE TABLE my_table (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    value TEXT,
    created DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

2. **Create Java Db class:**
```java
// src/org/calacademy/antweb/home/MyTableDb.java
package org.calacademy.antweb.home;

import java.sql.*;
import org.calacademy.antweb.util.*;

public class MyTableDb extends AntwebDb {
    
    public void insert(String name, String value) throws SQLException {
        String query = "INSERT INTO my_table (name, value) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }
    
    public String get(int id) throws SQLException {
        String query = "SELECT * FROM my_table WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        }
        return null;
    }
}
```

3. **Apply migration and deploy:**
```bash
docker-compose exec mysql mysql -uantweb -pf0rm1c6 ant < db/upgrade/8.106/2026-01-15-new-table.sql
docker-compose exec antweb ant deploy
```

### Add an API Endpoint

1. **Create SQLAlchemy model:**
```python
# api/v3/home/mytable.py
from sqlalchemy import Column, Integer, String, Text, DateTime
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class MyTable(Base):
    __tablename__ = 'my_table'
    
    id = Column(Integer, primary_key=True)
    name = Column(String(128))
    value = Column(Text)
    created = Column(DateTime)
```

2. **Add endpoint to API:**
```python
# api/v3/api.py
from home.mytable import MyTable

@application.route("/api/v3/mytable", methods=['GET'])
def get_mytable():
    limit = request.args.get('limit', 100, type=int)
    
    results = session.query(MyTable).limit(limit).all()
    
    return jsonify({
        'records': [
            {
                'id': r.id,
                'name': r.name,
                'value': r.value,
                'created': r.created.isoformat() if r.created else None
            }
            for r in results
        ],
        'count': len(results)
    })
```

3. **Test:**
```bash
docker-compose restart api
curl http://localhost:5000/api/v3/mytable?limit=5
```

## Code Style Guidelines

### Java

- **Naming:** CamelCase for classes, camelCase for methods/variables
- **Indentation:** 4 spaces
- **Line length:** 120 characters max
- **Imports:** Organized, no wildcards
- **Comments:** Javadoc for public methods

**Example:**
```java
/**
 * Retrieves specimens for a given taxon.
 * 
 * @param taxonName the scientific name
 * @return list of specimens
 * @throws SQLException if database error occurs
 */
public List<Specimen> getSpecimens(String taxonName) throws SQLException {
    String query = "SELECT * FROM specimen WHERE taxon_name = ?";
    List<Specimen> specimens = new ArrayList<>();
    
    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, taxonName);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            specimens.add(createSpecimen(rs));
        }
    }
    
    return specimens;
}
```

### Python

- **Style:** PEP 8
- **Naming:** snake_case for functions/variables
- **Indentation:** 4 spaces
- **Line length:** 100 characters
- **Type hints:** Use where beneficial

**Example:**
```python
def get_specimens(taxon_name: str, limit: int = 100) -> List[Dict]:
    """
    Retrieve specimens for a given taxon.
    
    Args:
        taxon_name: Scientific name of the taxon
        limit: Maximum number of records to return
        
    Returns:
        List of specimen dictionaries
    """
    results = session.query(Specimen)\
        .filter(Specimen.taxon_name == taxon_name)\
        .limit(limit)\
        .all()
    
    return [specimen.to_dict() for specimen in results]
```

### SQL

- **Keywords:** UPPERCASE
- **Identifiers:** lowercase_with_underscores
- **Indentation:** Align keywords

**Example:**
```sql
SELECT 
    s.code,
    s.taxon_name,
    s.country,
    COUNT(i.image_id) AS image_count
FROM specimen s
LEFT JOIN image i ON s.code = i.specimen_code
WHERE s.subfamily = 'formicinae'
  AND s.country = 'United States'
GROUP BY s.code, s.taxon_name, s.country
HAVING image_count > 0
ORDER BY s.code;
```

## Git Workflow

### Branching Strategy

- `master` - Production code (protected)
- `develop` - Development integration (protected)
- `feature/*` - New features
- `bugfix/*` - Bug fixes
- `hotfix/*` - Emergency production fixes

### Making Changes

1. **Create feature branch:**
```bash
git checkout develop
git pull origin develop
git checkout -b feature/add-new-endpoint
```

2. **Make changes and commit:**
```bash
git add .
git commit -m "Add new API endpoint for specimen images"
```

3. **Push and create PR:**
```bash
git push origin feature/add-new-endpoint
# Create Pull Request on GitHub
```

4. **After review and merge:**
```bash
git checkout develop
git pull origin develop
git branch -d feature/add-new-endpoint
```

### Commit Messages

Follow conventional commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `style:` - Formatting
- `refactor:` - Code restructuring
- `test:` - Tests
- `chore:` - Build/tooling

**Examples:**
```
feat(api): add endpoint for specimen images

Add new /api/v3/specimens/{code}/images endpoint
that returns all images for a specimen.

Closes #123

---

fix(upload): handle UTF-8 BOM in specimen files

Some Excel exports include UTF-8 BOM which was
causing parsing errors.

Fixes #456

---

docs(readme): update installation instructions

Add section on mounting S3 buckets via rclone.
```

## Contributing

### Before Starting

1. **Check existing issues:** https://github.com/calacademy-research/antweb/issues
2. **Discuss large changes:** Create an issue first to discuss approach
3. **Read documentation:** Familiarize yourself with architecture

### Contribution Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Write/update tests
5. Update documentation
6. Submit pull request

### Pull Request Checklist

- [ ] Code follows style guidelines
- [ ] All tests pass
- [ ] Documentation updated
- [ ] Commit messages are clear
- [ ] No merge conflicts
- [ ] Reviewed own changes

## Troubleshooting

### Common Issues

**Issue:** Docker containers won't start
```bash
# Check logs
docker-compose logs

# Rebuild containers
docker-compose down
docker-compose up -d --build
```

**Issue:** Database connection errors
```bash
# Verify MySQL is running
docker-compose ps mysql

# Check MySQL logs
docker-compose logs mysql

# Restart MySQL
docker-compose restart mysql
```

**Issue:** "Ant deploy" fails
```bash
# Check Java compilation errors
docker-compose exec antweb ant compile

# Clean and rebuild
docker-compose exec antweb ant clean deploy
```

**Issue:** Images not loading
```bash
# Check ANTWEB_BUCKET_PATH
echo $ANTWEB_BUCKET_PATH

# Verify mount
ls $ANTWEB_BUCKET_PATH/images

# Check configuration
docker-compose exec antweb cat WEB-INF/classes/AntwebResources.properties | grep imgDomain
```

## Additional Resources

- **Repository:** https://github.com/calacademy-research/antweb
- **Issues:** https://github.com/calacademy-research/antweb/issues
- **Live Site:** https://www.antweb.org
- **API Docs:** https://api.antweb.org/v3/docs
- **Docker Docs:** https://docs.docker.com
- **Struts Docs:** https://struts.apache.org
- **Flask Docs:** https://flask.palletsprojects.com
