<%-- The release number of AntWeb (semver, bumped by the release PR together with .version; CI checks they match).
     Statically included by common/academyHeader.jsp, which shows it in the header; AntwebUtil.getReleaseNum() reads
     the first quoted text of this file, so this comment holds no quote characters. Docs: docs/RELEASING.md. Tests:
     tests/e2e/test_header_version.py. --%><% antwebRelease = "9.3.0"; %>
