package org.calacademy.antweb.util;

import java.util.*;
import java.util.Map;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class UserAgentTracker extends Action {

    private static final Log s_log = LogFactory.getLog(UserAgentTracker.class);

    private static Deque<Request> requestLinkList = new ConcurrentLinkedDeque<>();

    private static int MAX_QUEUE_SIZE    = 100;
    private static int PERIOD_IN_SECONDS = 100;
    private static int ALLOWED_PERCENT = 25;

    private static int totalCount = 0;
    private static int blockCount = 0;
    private static int notBlockCount = 0;

    public UserAgentTracker() {
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        String target = HttpUtil.getTarget(request);
        DynaActionForm df = (DynaActionForm) form;

        return mapping.findForward("userAgents");
    }

    private static String[] allowPages = new String[]{"login", "index", "basicLayout", "about.do", "documentation.do", "antShare.do", "press.do", "favs.do", "contact.do", "api.do"};
    private static final Set<String> allowPagesSet = new HashSet<>(Arrays.asList(allowPages));

    public static boolean isBlockUser(HttpServletRequest request) {
        addRequest(request);

        boolean isBlock = blockTest(request);

        if (isBlock) {
            ++blockCount;
        } else {
            ++notBlockCount;
        }
        ++totalCount;

        return isBlock;
    }

    private static boolean blockTest(HttpServletRequest request) {

        // Could change if bots wise up, but logged in users get full access always.
        if (LoginMgr.isLoggedIn(request)) return false;

        // If page target is unknown, might be home page?
        String reqPage = HttpUtil.getTarget(request);
        if (reqPage == null) return false;

        // Always allow all access to the static pages.
        if (allowPagesSet.stream().anyMatch(reqPage::contains)) return false;

        // https://www.antweb.org/serverDebug.do    This can be set by admin during runtime.
        if (ServerDebug.isDebug("isBlockUnLoggedIn")) return true;

        // If the queue isn't full, no need to continue. It will fill quickly.
        if (requestLinkList.size() < MAX_QUEUE_SIZE) return false;

        // If the list is empty, shouldn't happen, given the test above.
        Request first = requestLinkList.peekFirst();
        if (first == null) return false;

        // We won't block any unless the server is taxed. Around 1 request per second?
        long secsSinceFirst = AntwebUtil.secsSince(first.getDate());
        if (secsSinceFirst > PERIOD_IN_SECONDS) return false;

        // This is what should catch the bots!
        return getPercentage(request) >= ALLOWED_PERCENT;
    }

    private static void addRequest(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String loginName = getLoginName(request);
        requestLinkList.add(new Request(userAgent, loginName, new Date()));

        if (requestLinkList.size() > MAX_QUEUE_SIZE) {
            requestLinkList.pollFirst(); // Avoids NoSuchElementException
        }
    }

    public static double getPercentage(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return getPercentageByName(userAgent);
    }

    public static double getPercentageByName(String name) {
        if (requestLinkList.isEmpty() || name == null) {
            return 0.0; // Avoid division by zero or null comparisons
        }

        long count = requestLinkList.stream()
                .map(Request::getUserAgent)
                .filter(Objects::nonNull)
                .filter(userAgent -> userAgent.equals(name))
                .count();

        return (count * 100.0) / requestLinkList.size();
    }


    public static String getStatsAsHtml() {

        String returnStr = "<br>Block:<b>" + blockCount + "</b> Not Block:<b>" + notBlockCount + "</b> Total:<b>" + totalCount + "</b>";

        try {
            int size = requestLinkList.size();
            returnStr += "<br>Most Recent Requests Queue Size:<b>" + size + "</b>";

            if (size > 2) {
                Request firstRequest = requestLinkList.getFirst();
                Request lastRequest = requestLinkList.getLast();
                if (firstRequest != null && lastRequest != null) {
                    Date date1 = firstRequest.getDate();
                    Date date2 = lastRequest.getDate();
                    long seconds = AntwebUtil.secsBetween(date1, date2);
                    long minutes = AntwebUtil.minsBetween(date1, date2);
                    returnStr += "<br>Span in seconds:<b>" + seconds + "</b> (minutes:<b>" + minutes + "</b>)";
                }
            }

            returnStr += "<br><br>Constants:";
            returnStr += "<br>&nbsp;&nbsp; MAX_QUEUE_SIZE: <b>" + MAX_QUEUE_SIZE + "</b>.  Max Queue size";
            returnStr += "<br>&nbsp;&nbsp; PERIOD_IN_SECONDS: <b>" + PERIOD_IN_SECONDS + "</b>.  Minimum Full QUEUE timestamp spread for busy threshold ( < seconds)";
            returnStr += "<br>&nbsp;&nbsp; ALLOWED_PERCENT: <b>" + ALLOWED_PERCENT + "%</b>.  Allowed percentage of list before a bot is blocked";

        } catch (Exception e) {
            AntwebUtil.logStackTrace(e);
        }
        return returnStr;
    }

    public static String getRequestsAsHtml() {
        String returnStr = "";
        int i = 0;
        String requests = "";
        for (Request req : requestLinkList) {
            String loginName = req.getLoginName();
            String userAgent = req.getUserAgent();
            if (userAgent == null) {
                requests += "Null userAgent";
            } else {
                ++i;
                requests += "<br><b>" + i + ":</b> ";
                requests += loginName;
                requests += " " + userAgent + " - " + req.getDate();
            }
        }
        returnStr += requests;
        return returnStr;
    }

    public static String getAgentSummaryReport() {
        StringBuilder summaryReport = new StringBuilder();
        try {
            HashMap<String, Integer> agentHash = new HashMap<>();
            for (Request req : requestLinkList) {
                String loginName = req.getLoginName();
                String userAgent = req.getUserAgent();
                String key = loginName + " " + userAgent;

                agentHash.merge(key, 1, Integer::sum);
            }

            List<Map.Entry<String, Integer>> topAgents = agentHash.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(5)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Integer> entry : topAgents) {
                summaryReport.append("<br>").append("<b>count: ").append(entry.getValue()).append("</b> - ").append(entry.getKey());
            }
        } catch (Exception e) {
            AntwebUtil.logStackTrace(e);
        }

        return "" + summaryReport.toString();
    }

    private static String getLoginName(HttpServletRequest servletRequest) {
        String name = "";
        if (LoginMgr.isLoggedIn(servletRequest)) {
            Login login = LoginMgr.getLogin(servletRequest);
            name = "<b>*" + login.getName() + "</b>";
        }
        return name;
    }
}

class Request {
    private String userAgent;
    private String loginName;
    private Date date;

    public Request(String userAgent, String loginName, Date date) {
        this.userAgent = userAgent;
        this.loginName = loginName;
        this.date = date;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getLoginName() {
        return loginName;
    }

    public Date getDate() {
        return date;
    }
}
