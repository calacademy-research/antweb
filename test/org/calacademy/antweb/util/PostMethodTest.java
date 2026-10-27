package test.org.calacademy.antweb.util;

import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.HttpUtil;

/**
 * 2026-10-27: HttpUtil.isPost and the form token check in SessionRequestFilter agree on what a post is:
 * a form post is sent as POST, and the token is checked for every method other than GET, HEAD and OPTIONS.
 */
public class PostMethodTest extends TestCase {

    private static HttpServletRequest request(String method, String contentType) {
        return (HttpServletRequest) Proxy.newProxyInstance(PostMethodTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getMethod": return method;
                    case "getContentType": return contentType;
                    default: return null;
                }
            });
    }

    public void testFormPostIsPost() {
        assertTrue(HttpUtil.isPost(request("POST", "application/x-www-form-urlencoded")));
        assertTrue(HttpUtil.isPost(request("post", "multipart/form-data; boundary=x")));
    }

    public void testGetWithFormContentTypeIsNotPost() {
        assertFalse(HttpUtil.isPost(request("GET", "application/x-www-form-urlencoded")));
        assertFalse(HttpUtil.isPost(request("GET", "text/plain")));
        assertFalse(HttpUtil.isPost(request("PUT", "multipart/form-data")));
        assertFalse(HttpUtil.isPost(request("POST", null)));
    }

    public void testMethodsCheckedForToken() {
        assertTrue(Csrf.methodNeedsCheck("POST"));
        assertTrue(Csrf.methodNeedsCheck("put"));
        assertTrue(Csrf.methodNeedsCheck("DELETE"));
        assertTrue(Csrf.methodNeedsCheck(null));
        assertFalse(Csrf.methodNeedsCheck("GET"));
        assertFalse(Csrf.methodNeedsCheck("head"));
        assertFalse(Csrf.methodNeedsCheck("OPTIONS"));
    }
}
