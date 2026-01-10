# Phase 1: Security & Foundation - Detailed Implementation Plan

## Overview

**Duration:** 12-16 weeks  
**Goal:** Establish secure, modern foundation for all future development  
**Prerequisites:** Development environment setup (see DEVELOPMENT.md)

This phase addresses critical security vulnerabilities and modernizes core infrastructure. All work in this phase can begin immediately and provides immediate value.

---

## Table of Contents

1. [Release Management & Versioning](#1-release-management--versioning)
2. [Authentication & Authorization System](#2-authentication--authorization-system)
3. [Dependency Updates & Security Patches](#3-dependency-updates--security-patches)
4. [Database Modernization](#4-database-modernization)

---

## 1. Release Management & Versioning

**Priority:** Start immediately (parallel with other work)  
**Duration:** 2 weeks  
**Why First:** Establishes process for tracking all subsequent changes

### Current State Problems

```
/doc/releaseNotes.txt - 65K of unstructured release notes
/doc/release.txt - 238K of release history
/doc/version.txt - Version tracking in text files
No GitHub Releases
No semantic versioning
No changelogs
Release process unclear
```

### Target State

```
✅ Semantic Versioning (SemVer 2.0)
✅ GitHub Releases with changelogs
✅ CHANGELOG.md in repository
✅ Automated version tagging
✅ Release notes visible on website
✅ Link to roadmap from website
```

---

### Week 1: Establish Versioning System

#### Task 1.1: Determine Current Version

**Action:**
```bash
# Review existing version files
cat doc/version.txt
cat doc/release.txt | head -50

# Determine last significant release
# Current version appears to be 8.x based on database migrations
```

**Decision:**
- Current version: `8.x.x` (based on db/upgrade/8.x structure)
- Starting point for new versioning: `8.105.0` or `9.0.0`
- Recommendation: Use `9.0.0` to mark new development era

#### Task 1.2: Adopt Semantic Versioning

**Create:** `.version` file in repository root
```
9.0.0
```

**Document:** Create `docs/VERSIONING.md`
```markdown
# AntWeb Versioning

AntWeb follows Semantic Versioning 2.0 (https://semver.org/)

## Version Format: MAJOR.MINOR.PATCH

- **MAJOR:** Breaking changes, major architecture updates
- **MINOR:** New features, non-breaking changes
- **PATCH:** Bug fixes, security patches

## Examples

- `9.0.0` → `9.1.0`: New authentication system (Phase 1)
- `9.1.0` → `9.1.1`: Security patch for authentication
- `9.1.1` → `10.0.0`: API breaking changes

## Release Schedule

- **Patch releases:** As needed (security/bugs)
- **Minor releases:** Monthly during active development
- **Major releases:** Per roadmap phases

## Version Locations

- Repository: `.version` file
- GitHub: Release tags (v9.0.0)
- Website: Footer and /about page
- API: /api/v3/version endpoint
```

#### Task 1.3: Create CHANGELOG.md

**Create:** `CHANGELOG.md` in repository root
```markdown
# Changelog

All notable changes to AntWeb will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Semantic versioning system
- GitHub Releases integration
- This CHANGELOG file

## [9.0.0] - 2026-01-XX

### Changed
- Established new release management process
- Migrated release notes from text files to GitHub

### Migration Note
This version marks the beginning of structured release management. 
Previous releases were documented in /doc/release.txt and /doc/releaseNotes.txt.

## Historical Releases

See `/doc/release.txt` for releases prior to v9.0.0.
```

---

### Week 2: GitHub Integration & Website Links

#### Task 1.4: Set Up GitHub Releases

**Action:**

1. **Create first GitHub Release:**
```bash
# Tag current state as baseline
git tag -a v9.0.0 -m "Release 9.0.0: Establish release management"
git push origin v9.0.0
```

2. **On GitHub:**
   - Go to https://github.com/calacademy-research/antweb/releases
   - Click "Draft a new release"
   - Tag: `v9.0.0`
   - Title: `Release 9.0.0: New Release Management`
   - Description: Copy from CHANGELOG.md

3. **Create release template:**

**Create:** `.github/RELEASE_TEMPLATE.md`
```markdown
## What's Changed

### Added
- 

### Changed
- 

### Fixed
- 

### Security
- 

## Migration Notes

[Any breaking changes or upgrade steps]

## Contributors

Thanks to everyone who contributed to this release!

**Full Changelog**: https://github.com/calacademy-research/antweb/compare/v[PREVIOUS]...v[CURRENT]
```

#### Task 1.5: Add API Version Endpoint

**Purpose:** Allow programmatic version checking

**Create:** `api/v3/version.py`
```python
from flask import jsonify, current_app
import os

@app.route('/api/v3/version')
def get_version():
    """Return current AntWeb version."""
    
    # Read from .version file
    version_file = os.path.join(current_app.root_path, '../../.version')
    try:
        with open(version_file, 'r') as f:
            version = f.read().strip()
    except FileNotFoundError:
        version = "unknown"
    
    return jsonify({
        'version': version,
        'api_version': '3.1',
        'release_notes': 'https://github.com/calacademy-research/antweb/releases',
        'roadmap': 'https://github.com/calacademy-research/antweb/blob/master/docs/ROADMAP.md'
    })
```

**Test:**
```bash
curl http://localhost:5000/api/v3/version
```

Expected response:
```json
{
  "version": "9.0.0",
  "api_version": "3.1",
  "release_notes": "https://github.com/calacademy-research/antweb/releases",
  "roadmap": "https://github.com/calacademy-research/antweb/blob/master/docs/ROADMAP.md"
}
```

#### Task 1.6: Add Links to Website

**Location 1: Footer** (all pages)

**Edit:** `web/common/footer.jsp`
```jsp
<div class="footer">
    <div class="footer-links">
        <a href="/about.do">About</a> |
        <a href="/contact.do">Contact</a> |
        <a href="https://github.com/calacademy-research/antweb/releases" target="_blank">Release Notes</a> |
        <a href="https://github.com/calacademy-research/antweb/blob/master/docs/ROADMAP.md" target="_blank">Development Roadmap</a>
    </div>
    <div class="footer-version">
        Version <%= application.getAttribute("version") %>
    </div>
    <div class="footer-copyright">
        © California Academy of Sciences
    </div>
</div>
```

**Location 2: About Page**

**Edit:** `web/about.jsp`
```jsp
<h2>Current Version</h2>
<p>
    AntWeb <%= application.getAttribute("version") %>
    <br>
    <a href="https://github.com/calacademy-research/antweb/releases" target="_blank">
        View Release Notes
    </a>
    <br>
    <a href="https://github.com/calacademy-research/antweb/blob/master/docs/ROADMAP.md" target="_blank">
        View Development Roadmap
    </a>
</p>
```

**Location 3: Admin/Curator Dashboard**

**Edit:** `web/curate.jsp`
```jsp
<div class="admin-info">
    <h3>System Information</h3>
    <ul>
        <li>Version: <%= application.getAttribute("version") %></li>
        <li><a href="https://github.com/calacademy-research/antweb/releases" target="_blank">Release Notes</a></li>
        <li><a href="https://github.com/calacademy-research/antweb/blob/master/docs/ROADMAP.md" target="_blank">Roadmap</a></li>
        <li><a href="https://github.com/calacademy-research/antweb/issues" target="_blank">Report Issue</a></li>
    </ul>
</div>
```

#### Task 1.7: Load Version at Startup

**Edit:** `src/org/calacademy/antweb/util/AntwebStartupListener.java` (create if doesn't exist)

```java
package org.calacademy.antweb.util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AntwebStartupListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(ServletContextEvent event) {
        // Load version from .version file
        String version = loadVersion();
        event.getServletContext().setAttribute("version", version);
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // Cleanup if needed
    }
    
    private String loadVersion() {
        String versionFile = System.getProperty("catalina.base") + "/.version";
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            return reader.readLine().trim();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
```

**Register listener in:** `WEB-INF/web.xml`
```xml
<listener>
    <listener-class>org.calacademy.antweb.util.AntwebStartupListener</listener-class>
</listener>
```

#### Task 1.8: Document Release Process

**Create:** `docs/RELEASING.md`
```markdown
# Release Process

## Pre-Release Checklist

- [ ] All tests passing
- [ ] CHANGELOG.md updated
- [ ] Version bumped in `.version`
- [ ] Database migrations documented
- [ ] Breaking changes documented
- [ ] Security issues addressed

## Creating a Release

### 1. Update Version

```bash
# For patch release: 9.0.0 → 9.0.1
# For minor release: 9.0.1 → 9.1.0
# For major release: 9.1.0 → 10.0.0

echo "9.1.0" > .version
```

### 2. Update CHANGELOG

Add new section to CHANGELOG.md:

```markdown
## [9.1.0] - 2026-02-15

### Added
- New authentication system
- Password reset functionality

### Changed
- Updated all dependencies

### Security
- Fixed authentication vulnerability CVE-XXXX
```

### 3. Commit and Tag

```bash
git add .version CHANGELOG.md
git commit -m "Release 9.1.0"
git tag -a v9.1.0 -m "Release 9.1.0: Authentication system"
git push origin master
git push origin v9.1.0
```

### 4. Create GitHub Release

1. Go to https://github.com/calacademy-research/antweb/releases/new
2. Choose tag: `v9.1.0`
3. Title: `Release 9.1.0: Authentication System`
4. Copy content from CHANGELOG.md
5. Attach any release artifacts (if applicable)
6. Publish release

### 5. Deploy to Production

```bash
# Follow deployment process
# Update version display on website
```

### 6. Announce

- Email to antweb-announce mailing list
- Post to social media (if applicable)
- Update documentation

## Hotfix Process

For urgent security fixes:

1. Create hotfix branch from latest release tag
2. Fix issue
3. Bump patch version
4. Create release
5. Deploy immediately
```

---

### Success Criteria for Release Management

**Week 2 Complete When:**
- [ ] `.version` file in repository
- [ ] CHANGELOG.md established
- [ ] First GitHub Release (v9.0.0) created
- [ ] Version displayed on website footer
- [ ] Links to GitHub releases and roadmap on website
- [ ] API endpoint returns version
- [ ] docs/VERSIONING.md documented
- [ ] docs/RELEASING.md documented

---

## 2. Authentication & Authorization System

**Priority:** Start Week 3 (after release management established)  
**Duration:** 8-10 weeks  
**Critical:** Highest security priority

### Current State Assessment

#### Week 3: Security Audit

**Task 2.1: Document Current Authentication**

**Create:** `docs/CURRENT_AUTH_ANALYSIS.md`

```markdown
# Current Authentication System Analysis

## Password Storage
- [ ] Method: (MD5/SHA1/bcrypt/plain text?)
- [ ] Salt used: (yes/no)
- [ ] Iterations: (if applicable)

## Session Management
- [ ] Session timeout: (minutes)
- [ ] Session token storage: (cookie/server)
- [ ] HTTPS enforcement: (yes/no)

## User Roles
- [ ] Admin
- [ ] Curator
- [ ] Contributor
- [ ] Public (no login)

## Vulnerabilities Identified
1. [List security issues found]

## Code Locations
- LoginDb.java - Database access
- LoginAction.java - Login handling
- login.jsp - Login form
- [Other files]
```

**Audit Commands:**
```sql
-- Check password storage
SELECT id, email, password, LENGTH(password) as pwd_len 
FROM login LIMIT 5;

-- Check user distribution
SELECT role, COUNT(*) FROM login GROUP BY role;

-- Check recent activity
SELECT COUNT(*) FROM login 
WHERE last_login > DATE_SUB(NOW(), INTERVAL 6 MONTH);

-- Check for inactive accounts
SELECT email, last_login FROM login 
WHERE last_login < DATE_SUB(NOW(), INTERVAL 1 YEAR) 
OR last_login IS NULL;
```

**Task 2.2: Security Vulnerability Scan**

```bash
# Run OWASP dependency check
./gradlew dependencyCheckAnalyze

# Run SQL injection scanner
sqlmap -u "http://localhost/login.do" --forms

# Check for XSS vulnerabilities
xsser -u "http://localhost/login.do" --auto

# SSL/TLS check
sslyze --regular localhost:443
```

**Document findings in:** `docs/SECURITY_AUDIT_2026.md`

---

### Week 4-5: Design New System

**Task 2.3: Choose Authentication Framework**

**Options Evaluation:**

| Framework | Pros | Cons | Recommendation |
|-----------|------|------|----------------|
| **Spring Security** | Industry standard, comprehensive, well-documented | Complex setup, Java only | ✅ RECOMMENDED |
| **Apache Shiro** | Simpler than Spring Security | Less active development | ⚠️ Alternative |
| **Custom JWT** | Full control, lightweight | More work, security risks | ❌ Not recommended |
| **Auth0/Okta** | Managed, modern | External dependency, cost | ⚠️ Future consideration |

**Decision:** Use Spring Security for:
- Industry-standard security
- Active development and support
- Comprehensive features (JWT, OAuth2, LDAP)
- Large community

**Task 2.4: Design Database Schema**

**Create new tables:**

```sql
-- New authentication tables
CREATE TABLE auth_user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- bcrypt hash
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    is_locked BOOLEAN DEFAULT FALSE,
    failed_login_attempts INT DEFAULT 0,
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_role (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL,  -- ADMIN, CURATOR, CONTRIBUTOR
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_user_role (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by INT,  -- admin who granted this role
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES auth_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_permission (
    id INT PRIMARY KEY AUTO_INCREMENT,
    role_id INT NOT NULL,
    resource VARCHAR(100) NOT NULL,  -- e.g., "specimens", "images", "users"
    action VARCHAR(50) NOT NULL,     -- e.g., "create", "read", "update", "delete"
    FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE,
    UNIQUE KEY unique_permission (role_id, resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    action VARCHAR(100) NOT NULL,  -- LOGIN, LOGOUT, PASSWORD_CHANGE, etc.
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_password_reset (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    token VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id INT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Migration Strategy:**

```sql
-- Migration script: Populate new auth tables from old login table
INSERT INTO auth_user (email, full_name, is_active, last_login, created_at)
SELECT 
    email,
    name,
    is_active,
    last_login,
    created
FROM login;

-- Map old roles to new roles
INSERT INTO auth_role (name, description) VALUES
('ADMIN', 'Full system access'),
('CURATOR', 'Can upload and edit specimens'),
('CONTRIBUTOR', 'Can submit specimens for review'),
('USER', 'Read-only access');

-- Assign roles based on old access_group
INSERT INTO auth_user_role (user_id, role_id)
SELECT 
    au.id,
    ar.id
FROM auth_user au
JOIN login l ON au.email = l.email
JOIN auth_role ar ON (
    CASE 
        WHEN l.role = 'admin' THEN ar.name = 'ADMIN'
        WHEN l.role = 'curator' THEN ar.name = 'CURATOR'
        ELSE ar.name = 'USER'
    END
);
```

---

### Week 6-8: Implementation

**Task 2.5: Set Up Spring Security**

**Add to:** `build.xml` (or create `pom.xml` if moving to Maven)

```xml
<!-- Spring Security dependencies -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-web</artifactId>
    <version>6.2.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-config</artifactId>
    <version>6.2.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>6.2.1</version>
</dependency>

<!-- JWT support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

**Task 2.6: Implement Core Authentication**

**Create:** `src/org/calacademy/antweb/security/SecurityConfig.java`

```java
package org.calacademy.antweb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // 12 rounds
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()  // API has own auth
                .requestMatchers("/public/**", "/browse.do", "/description.do").permitAll()
                .requestMatchers("/upload.do", "/curate.do").hasAnyRole("CURATOR", "ADMIN")
                .requestMatchers("/admin.do", "/util.do").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.do")
                .loginProcessingUrl("/performLogin")
                .defaultSuccessUrl("/curate.do")
                .failureUrl("/login.do?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/login.do?expired=true")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")  // API uses JWT
            );
        
        return http.build();
    }
}
```

**Create:** `src/org/calacademy/antweb/security/UserDetailsServiceImpl.java`

```java
package org.calacademy.antweb.security;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.calacademy.antweb.home.AuthUserDb;
import java.sql.SQLException;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try (Connection conn = DBUtil.getConnection()) {
            AuthUserDb userDb = new AuthUserDb(conn);
            AuthUser user = userDb.getUserByEmail(email);
            
            if (user == null || !user.isActive()) {
                throw new UsernameNotFoundException("User not found or inactive: " + email);
            }
            
            return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRoles().toArray(new String[0]))
                .accountLocked(user.isLocked())
                .build();
                
        } catch (SQLException e) {
            throw new UsernameNotFoundException("Database error", e);
        }
    }
}
```

**Task 2.7: Implement Password Reset**

**Create:** `src/org/calacademy/antweb/security/PasswordResetService.java`

```java
package org.calacademy.antweb.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;

public class PasswordResetService {
    
    private static final int TOKEN_LENGTH = 32;
    private static final int EXPIRY_HOURS = 24;
    
    public String createResetToken(int userId) throws SQLException {
        // Generate secure random token
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // Store in database
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "INSERT INTO auth_password_reset (user_id, token, expires_at) " +
                        "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setString(2, token);
                stmt.setInt(3, EXPIRY_HOURS);
                stmt.executeUpdate();
            }
        }
        
        return token;
    }
    
    public boolean validateResetToken(String token) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT id, expires_at, used FROM auth_password_reset " +
                        "WHERE token = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, token);
                ResultSet rs = stmt.executeQuery();
                
                if (!rs.next()) {
                    return false;  // Token not found
                }
                
                if (rs.getBoolean("used")) {
                    return false;  // Already used
                }
                
                Timestamp expires = rs.getTimestamp("expires_at");
                if (expires.before(new Timestamp(System.currentTimeMillis()))) {
                    return false;  // Expired
                }
                
                return true;
            }
        }
    }
    
    public void resetPassword(String token, String newPassword) throws SQLException {
        if (!validateResetToken(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        
        // Hash new password
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hashedPassword = encoder.encode(newPassword);
        
        try (Connection conn = DBUtil.getConnection()) {
            // Get user ID from token
            String getUserSql = "SELECT user_id FROM auth_password_reset WHERE token = ?";
            int userId;
            try (PreparedStatement stmt = conn.prepareStatement(getUserSql)) {
                stmt.setString(1, token);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Token not found");
                }
                userId = rs.getInt("user_id");
            }
            
            // Update password
            String updateSql = "UPDATE auth_user SET password_hash = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, hashedPassword);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
            
            // Mark token as used
            String markUsedSql = "UPDATE auth_password_reset SET used = TRUE WHERE token = ?";
            try (PreparedStatement stmt = conn.prepareStatement(markUsedSql)) {
                stmt.setString(1, token);
                stmt.executeUpdate();
            }
            
            // Log audit
            AuditLogService.log(userId, "PASSWORD_RESET", true, null);
        }
    }
}
```

---

### Week 9-10: Migration & Testing

**Task 2.8: User Migration Script**

**Create:** `scripts/migrate-users.sh`

```bash
#!/bin/bash
# Migrate users from old login table to new auth system

set -e

echo "Starting user migration..."

# Backup current login table
mysql -uantweb -p ant -e "CREATE TABLE login_backup AS SELECT * FROM login;"

# Create new auth tables
mysql -uantweb -p ant < db/migrations/auth-schema.sql

# Migrate users
mysql -uantweb -p ant < db/migrations/migrate-users.sql

# Verify migration
COUNT_OLD=$(mysql -uantweb -p ant -sN -e "SELECT COUNT(*) FROM login;")
COUNT_NEW=$(mysql -uantweb -p ant -sN -e "SELECT COUNT(*) FROM auth_user;")

if [ "$COUNT_OLD" -eq "$COUNT_NEW" ]; then
    echo "✅ Migration successful: $COUNT_NEW users migrated"
else
    echo "❌ Migration failed: $COUNT_OLD old users, $COUNT_NEW new users"
    exit 1
fi

echo "Sending password reset emails to all users..."
# Run Java program to send reset emails
java -cp ... org.calacademy.antweb.security.PasswordResetEmailer

echo "Migration complete!"
```

**Task 2.9: Testing**

**Create:** `test/org/calacademy/antweb/security/AuthenticationTest.java`

```java
package org.calacademy.antweb.security;

import org.junit.Test;
import static org.junit.Assert.*;

public class AuthenticationTest {
    
    @Test
    public void testPasswordHashing() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String password = "TestPassword123!";
        String hash = encoder.encode(password);
        
        assertTrue(encoder.matches(password, hash));
        assertFalse(encoder.matches("WrongPassword", hash));
    }
    
    @Test
    public void testPasswordResetToken() throws SQLException {
        PasswordResetService service = new PasswordResetService();
        int userId = 1;
        
        String token = service.createResetToken(userId);
        assertNotNull(token);
        assertTrue(service.validateResetToken(token));
        
        // Token should be invalid after use
        service.resetPassword(token, "NewPassword123!");
        assertFalse(service.validateResetToken(token));
    }
    
    @Test
    public void testAccountLocking() throws SQLException {
        // Test that account locks after 5 failed attempts
        // Test that account unlocks after timeout
        // Test admin can manually unlock
    }
}
```

**Manual Testing Checklist:**

```markdown
## Authentication Testing

- [ ] Login with valid credentials succeeds
- [ ] Login with invalid credentials fails
- [ ] Account locks after 5 failed attempts
- [ ] Password reset email sent
- [ ] Password reset link works
- [ ] Old password no longer works after reset
- [ ] Session expires after 30 minutes
- [ ] Logout clears session
- [ ] Cannot access admin pages without admin role
- [ ] Cannot access curator pages without curator/admin role
- [ ] Audit log records all login attempts
- [ ] XSS injection prevented
- [ ] SQL injection prevented
- [ ] CSRF protection works
```

---

### Success Criteria for Authentication

**Week 10 Complete When:**
- [ ] All tests passing (unit + integration)
- [ ] All users migrated successfully
- [ ] Password reset emails sent
- [ ] Security audit shows zero critical vulnerabilities
- [ ] Performance: Login takes < 1 second
- [ ] Audit logging functional
- [ ] Documentation complete
- [ ] Deployed to staging and tested
- [ ] Ready for production deployment

---

## 3. Dependency Updates & Security Patches

**Priority:** Week 11-13 (parallel with auth deployment)  
**Duration:** 3 weeks

[Continue with detailed dependency update plan...]

---

## 4. Database Modernization

**Priority:** Week 14-20  
**Duration:** 6-7 weeks

[Continue with detailed database migration plan...]

---

## Phase 1 Milestones

### Milestone 1: Release Management (Week 2)
- [ ] Semantic versioning established
- [ ] GitHub releases configured
- [ ] Website links added
- [ ] Release process documented

### Milestone 2: Authentication Complete (Week 10)
- [ ] Spring Security integrated
- [ ] All users migrated
- [ ] Password reset functional
- [ ] Audit logging operational

### Milestone 3: Dependencies Current (Week 13)
- [ ] Zero critical vulnerabilities
- [ ] All dependencies < 2 years old
- [ ] Automated scanning enabled

### Milestone 4: Database Modernized (Week 20)
- [ ] MySQL 8 in production
- [ ] All tables using InnoDB
- [ ] Performance improved
- [ ] Zero data loss

---

## Success Criteria for Phase 1

Phase 1 is complete when:

1. **Release Management:**
   - ✅ v9.x.x version in production
   - ✅ GitHub releases with changelogs
   - ✅ Links on website functional

2. **Security:**
   - ✅ Zero critical vulnerabilities
   - ✅ Modern authentication (bcrypt)
   - ✅ Comprehensive audit logging

3. **Dependencies:**
   - ✅ All packages current
   - ✅ Automated scanning in CI/CD

4. **Database:**
   - ✅ MySQL 8.0+ operational
   - ✅ UTF-8MB4 throughout
   - ✅ Measurable performance gains

5. **Documentation:**
   - ✅ All changes documented
   - ✅ Migration guides complete
   - ✅ Testing procedures documented

---

## Next Phase Preview

Once Phase 1 is complete, Phase 2 begins:
- **Complete REST API** (see docs/ROADMAP.md)
- **Background job system**
- **API documentation**

---

## Questions & Support

- **Issues:** https://github.com/calacademy-research/antweb/issues
- **Discussions:** Use `phase-1` label
- **Security:** Email security@antweb.org (do not post publicly)

---

**Last Updated:** January 2026  
**Next Review:** After Milestone 2 (Week 10)
