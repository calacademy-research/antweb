package org.calacademy.antweb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The MySQL login AntWeb uses, from etc/db.properties on the server
 * (~/antweb/etc/, which the container sees as /antweb/deploy/etc/; gitignored,
 * chmod 600, never in the repository):
 *   user=antweb
 *   password=...
 * When the file is missing, the old built-in values are used (until they are
 * removed from the repository). Read once at startup.
 */
public final class DbCredentials {

    private static final Log s_log = LogFactory.getLog(DbCredentials.class);
    static final String CONFIG = "/antweb/deploy/etc/db.properties";

    private static Properties s_props;

    private DbCredentials() {}

    private static synchronized Properties props() {
        if (s_props == null) {
            Properties p = new Properties();
            File f = new File(CONFIG);
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                    s_log.info("props() database login read from " + CONFIG);
                } catch (Exception e) {
                    s_log.error("props() could not read " + CONFIG + " e:" + e);
                }
            } else {
                s_log.warn("props() " + CONFIG + " not found; using the built-in database login");
            }
            s_props = p;
        }
        return s_props;
    }

    /** The user from db.properties, or the given default. */
    public static String user(String dflt) {
        String v = props().getProperty("user");
        return v == null || v.trim().isEmpty() ? dflt : v.trim();
    }

    /** The password from db.properties, or the given default. */
    public static String password(String dflt) {
        String v = props().getProperty("password");
        return v == null || v.isEmpty() ? dflt : v.trim();
    }
}
