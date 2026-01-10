# GitHub Issue Labels for AntWeb

## Recommended Labels

### Type Labels
- `bug` - Something isn't working correctly
- `enhancement` - New feature or improvement request
- `security` - Security-related issues
- `documentation` - Documentation improvements
- `question` - General questions or discussions

### Priority Labels
- `priority: critical` - Critical issues requiring immediate attention
- `priority: high` - High priority issues
- `priority: medium` - Medium priority issues
- `priority: low` - Low priority, nice-to-have

### Phase Labels (from Roadmap)
- `phase-1` - Phase 1: Security & Foundation
- `phase-2` - Phase 2: API Development
- `phase-3` - Phase 3: Frontend Modernization
- `phase-4` - Phase 4: Backend Modernization

### Component Labels
- `component: api` - API-related issues
- `component: database` - Database-related issues
- `component: ui` - User interface issues
- `component: upload` - File upload functionality
- `component: search` - Search functionality
- `component: authentication` - Authentication/authorization
- `component: images` - Image processing/display

### Status Labels
- `status: investigating` - Under investigation
- `status: needs-reproduction` - Need to reproduce the issue
- `status: blocked` - Blocked by another issue
- `status: in-progress` - Currently being worked on
- `status: ready-for-review` - Ready for code review
- `status: ready-for-testing` - Ready for testing

### Special Labels
- `good first issue` - Good for newcomers
- `help wanted` - Looking for volunteers to help
- `duplicate` - Duplicate of another issue
- `wontfix` - Will not be fixed
- `needs-more-info` - Need more information from reporter

## Creating Labels in GitHub

### Option 1: Manual Creation
1. Go to https://github.com/calacademy-research/antweb/labels
2. Click "New label"
3. Add name, description, and color
4. Click "Create label"

### Option 2: Using GitHub CLI (if installed)
```bash
# Type labels
gh label create "bug" --description "Something isn't working" --color "d73a4a"
gh label create "enhancement" --description "New feature or request" --color "a2eeef"
gh label create "security" --description "Security issue" --color "d93f0b"
gh label create "documentation" --description "Documentation improvements" --color "0075ca"
gh label create "question" --description "Further information requested" --color "d876e3"

# Priority labels
gh label create "priority: critical" --description "Critical priority" --color "b60205"
gh label create "priority: high" --description "High priority" --color "d93f0b"
gh label create "priority: medium" --description "Medium priority" --color "fbca04"
gh label create "priority: low" --description "Low priority" --color "0e8a16"

# Phase labels
gh label create "phase-1" --description "Phase 1: Security & Foundation" --color "1d76db"
gh label create "phase-2" --description "Phase 2: API Development" --color "5319e7"
gh label create "phase-3" --description "Phase 3: Frontend Modernization" --color "0052cc"
gh label create "phase-4" --description "Phase 4: Backend Modernization" --color "006b75"

# Component labels  
gh label create "component: api" --description "API-related" --color "c5def5"
gh label create "component: database" --description "Database-related" --color "c5def5"
gh label create "component: ui" --description "UI-related" --color "c5def5"
gh label create "component: upload" --description "Upload functionality" --color "c5def5"
gh label create "component: search" --description "Search functionality" --color "c5def5"
gh label create "component: authentication" --description "Auth-related" --color "c5def5"
gh label create "component: images" --description "Image-related" --color "c5def5"

# Status labels
gh label create "status: investigating" --description "Under investigation" --color "ededed"
gh label create "status: needs-reproduction" --description "Needs reproduction" --color "ededed"
gh label create "status: blocked" --description "Blocked by another issue" --color "ededed"
gh label create "status: in-progress" --description "In progress" --color "ededed"

# Special labels
gh label create "good first issue" --description "Good for newcomers" --color "7057ff"
gh label create "help wanted" --description "Extra attention needed" --color "008672"
```

## Suggested Label Colors

- **Red tones** (#d73a4a, #b60205, #d93f0b) - Bugs, critical issues
- **Blue tones** (#0075ca, #1d76db, #0052cc) - Features, phases
- **Purple** (#a2eeef, #5319e7, #7057ff) - Enhancements, special
- **Yellow** (#fbca04) - Medium priority, warnings
- **Green** (#0e8a16, #008672) - Low priority, help wanted
- **Gray** (#ededed, #c5def5) - Status, components

## Using Labels

### Example Issue Label Combinations

**Critical bug in upload:**
- `bug`
- `priority: critical`
- `component: upload`
- `phase-1`

**Feature request for API:**
- `enhancement`
- `priority: medium`
- `component: api`
- `phase-2`

**Documentation improvement:**
- `documentation`
- `priority: low`
- `good first issue`

**Security vulnerability:**
- `security`
- `priority: critical`
- `phase-1`
- `component: authentication`

## Automatic Labeling

Issue templates automatically apply these labels:
- Bug Report → `bug`
- Feature Request → `enhancement`
- Security Vulnerability → `security`
- General Issue → `question`

Maintainers should add additional labels (priority, component, phase) as needed.
