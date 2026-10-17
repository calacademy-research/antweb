package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.DBUtil;

/** Values built into SQL text by the specimen upload are quoted with backslashes escaped. */
public class SqlLiteralTest extends TestCase {

    public void testQuotesAndBackslashes() {
        assertEquals("'abc'", DBUtil.sqlLiteral("abc"));
        assertEquals("'O''Brien'", DBUtil.sqlLiteral("O'Brien"));
        // x\' must not let the quote end the literal: the backslash is doubled, the quote doubled.
        assertEquals("'x\\\\'''", DBUtil.sqlLiteral("x\\'"));
        assertEquals("'end\\\\'", DBUtil.sqlLiteral("end\\"));
        assertEquals("null", DBUtil.sqlLiteral(null));
    }
}
