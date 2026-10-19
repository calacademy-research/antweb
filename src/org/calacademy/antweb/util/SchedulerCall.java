package org.calacademy.antweb.util;

import java.util.Map;
import java.util.function.Predicate;

import jakarta.servlet.http.HttpServletRequest;

/**
 * AntWeb's calls to itself: the Scheduler's utilData.do?action=set1..set5 (nightly run) and
 * AntwebMgr's util.do?action=postInstantiate after startup. Both go to http://localhost:8080
 * inside the antweb container (AntwebProps.getThisDomainApp()) and carry a one-time code.
 *
 * The code travels in the request header HEADER, never in the URL (URLs end up in the Tomcat
 * access log). A call is accepted only when
 *   - the code is in the header and is the current code,
 *   - the URL has no secureCode parameter (a code in the query string is refused),
 *   - it comes from the container itself (TCP peer 127.0.0.1 or ::1; requests through Caddy
 *     come from Caddy's container address), and
 *   - it has no X-Forwarded-For or CF-Connecting-IP header (Caddy adds X-Forwarded-For to
 *     every request it passes on).
 * Otherwise the request is treated as any other, so an administrator login still works.
 */
public final class SchedulerCall {

    public static final String HEADER = "X-AntWeb-Scheduler-Code";
    public static final String QUERY_PARAM = "secureCode";

    private SchedulerCall() {
    }

    public static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null) return false;
        String a = remoteAddr.trim();
        return a.startsWith("127.") || a.equals("::1") || a.equals("0:0:0:0:0:0:0:1") || a.equals("[::1]");
    }

    /** The whole check, without a servlet request (unit-tested in SchedulerCallTest). */
    public static boolean accepts(String headerCode, String queryCode, String remoteAddr,
                                  String forwardedFor, String cfConnectingIp, Predicate<String> isCurrentCode) {
        if (queryCode != null) return false;
        if (headerCode == null || headerCode.isEmpty()) return false;
        if (!isLoopback(remoteAddr)) return false;
        if (forwardedFor != null || cfConnectingIp != null) return false;
        return isCurrentCode != null && isCurrentCode.test(headerCode);
    }

    static boolean accepts(HttpServletRequest request, Predicate<String> isCurrentCode) {
        if (request == null) return false;
        return accepts(request.getHeader(HEADER), request.getParameter(QUERY_PARAM), request.getRemoteAddr(),
            request.getHeader("X-Forwarded-For"), request.getHeader("CF-Connecting-IP"), isCurrentCode);
    }

    /** The Scheduler's call to utilData.do during a run (AntwebUtil.getSecureCode()). */
    public static boolean isScheduler(HttpServletRequest request) {
        return accepts(request, AntwebUtil::isSecureCode);
    }

    /** AntwebMgr's postInstantiate call to util.do after startup. */
    public static boolean isPostInitialize(HttpServletRequest request) {
        return accepts(request, AntwebMgr::isPostInitializeCode);
    }

    /** Request headers for a self-call with this code. */
    public static Map<String, String> headers(String code) {
        return Map.of(HEADER, code);
    }
}
