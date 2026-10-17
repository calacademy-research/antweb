package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Browser cache lifetime for local *.js and *.css.
 *
 * Before 2026-10-17 Tomcat's ExpiresFilter (WEB-INF/web.xml) gave all css/js under
 * /common/, /search/ and /ckeditor/ "access plus 1 month" (Cache-Control: max-age=2592000),
 * so after a deploy visitors kept old scripts for up to 30 days (the header autocomplete
 * broke after the jQuery 3 upgrade because browsers kept the old autocomplete.js).
 *
 * Pages now reference every local script and stylesheet as file?v=hash (see Asset), so:
 *  - with a v= parameter: the URL changes whenever the file changes, so it can be cached
 *    for a year ("immutable": the browser does not even revalidate);
 *  - without one (old cached HTML, other sites, ckeditor's own ?t= files): 1 day, so a stale
 *    copy lives at most a day. Tomcat answers revalidations with 304 (ETag/Last-Modified).
 *
 * Mapped in web.xml AFTER ExpiresFilter: ExpiresFilter wraps the response and skips files
 * whose Cache-Control already has a max-age, so it then leaves these alone (it would add a
 * second Cache-Control header if this filter ran first).
 */
public class AssetCacheFilter implements Filter {

    static final String VERSIONED = "public, max-age=31536000, immutable";
    static final String UNVERSIONED = "public, max-age=86400";

    private static final Pattern V_PARAM = Pattern.compile("(^|&)v=[^&]+");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest request && res instanceof HttpServletResponse response) {
            String uri = request.getRequestURI();
            if (uri != null) {
                String lower = uri.toLowerCase();
                if (lower.endsWith(".js") || lower.endsWith(".css")) {
                    response.setHeader("Cache-Control", cacheControl(request.getQueryString()));
                }
            }
        }
        chain.doFilter(req, res);
    }

    static String cacheControl(String queryString) {
        return queryString != null && V_PARAM.matcher(queryString).find() ? VERSIONED : UNVERSIONED;
    }
}
