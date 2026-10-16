package org.calacademy.antweb.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Never let Tomcat compile and run a .jsp that the application generated at run time
 * (upload reports, logs, curator files, generated includes). Those directories hold text
 * that came from curators' uploads, so a .jsp there could carry injected JSP code.
 *
 * Mapped in web.xml to the run-time directories under /web/. Requests for anything
 * other than *.jsp / *.jspx / *.jspf pass through unchanged. Old .jsp reports under
 * /web/log/ are still viewable: the file is sent as plain bytes (never compiled) with a
 * Content-Security-Policy sandbox, so no script in it can run. Everything else is 404.
 */
public class GeneratedJspFilter implements Filter {

    private static final Log s_log = LogFactory.getLog(GeneratedJspFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath();
        if (request.getPathInfo() != null) path = (path == null ? "" : path) + request.getPathInfo();
        if (path == null) path = "";
        String lower = path.toLowerCase();
        if (!(lower.endsWith(".jsp") || lower.endsWith(".jspx") || lower.endsWith(".jspf"))) {
            chain.doFilter(req, res);
            return;
        }

        String method = request.getMethod();
        boolean isRead = "GET".equals(method) || "HEAD".equals(method);
        if (isRead && path.startsWith("/web/log/") && !path.contains("..") && !path.contains("\0")) {
            Path rel = Paths.get(path.substring(1)).normalize();
            if (rel.startsWith(Paths.get("web", "log"))) {
                Path file = Paths.get(AntwebProps.getDocRoot()).resolve(rel);
                if (Files.isRegularFile(file)) {
                    response.setContentType("text/html;charset=UTF-8");
                    response.setHeader("Content-Security-Policy", "sandbox; script-src 'none'; object-src 'none'");
                    response.setHeader("X-Content-Type-Options", "nosniff");
                    if ("GET".equals(method)) {
                        try (OutputStream out = response.getOutputStream()) {
                            Files.copy(file, out);
                        }
                    }
                    return;
                }
            }
        }
        s_log.debug("doFilter() refused generated jsp:" + path);
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
