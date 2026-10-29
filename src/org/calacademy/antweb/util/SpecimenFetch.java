package org.calacademy.antweb.util;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Servlet for the short specimen link /specimen/<code> (web.xml): redirects (301) to specimen.do?name=<code>,
 * or answers 404 "Specimen not found." for an invalid code. Public. Reads nothing. Tests:
 * test/org/calacademy/antweb/security/SecurityBatch5Test.java.
 */
public class SpecimenFetch extends HttpServlet {

  private static final Log s_log = LogFactory.getLog(SpecimenFetch.class);
    
  // Accessible as: http://localhost/antweb/specimen/CASENT0106322X    
  //   http://data.antweb.org/specimen/CASENT0078328  
    
  // Specimen codes: letters, digits and . _ - : (e.g. CASENT0106322, jtl028522-01).
  static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,80}");

  /** The redirect target for a /specimen/<code> short link, or null if the code is not valid. */
  public static String target(String contextPath, String pathInfo) {
    if (pathInfo == null || pathInfo.length() < 2) return null;
    String specimenCode = pathInfo.substring(1).trim();
    if (!CODE.matcher(specimenCode).matches()) return null;
    return (contextPath == null ? "" : contextPath) + "/specimen.do?name=" + Html.url(specimenCode);
  }

  // A short link now redirects to the specimen page. It used to fetch the page from this server
  // and, on any error, echo the exception and URL unescaped (reflected XSS).
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    String target = target(request.getContextPath(), request.getPathInfo());
    if (target == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("text/plain;charset=UTF-8");
      Writer writer = response.getWriter();
      writer.write("Specimen not found.");
      return;
    }
    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    response.setHeader("Location", target);
  }
}
