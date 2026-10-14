package org.calacademy.antweb.util;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The application's database connection pools (conPool, mediumConPool, longConPool).
 *
 * Struts 1.1 built these from the &lt;data-sources&gt; section of
 * WEB-INF/struts-configDb.xml and handed them out through Action.getDataSource().
 * Later Struts 1 versions dropped data-source support, so AntWeb builds the
 * pools itself, from the same file and with the same properties.
 */
public final class AntwebDataSources {

    private static final Log s_log = LogFactory.getLog(AntwebDataSources.class);
    static final String CONFIG = "/WEB-INF/struts-configDb.xml";

    private static Map<String, DataSource> sources;

    private AntwebDataSources() {}

    public static DataSource get(ServletContext context, String key) {
        Map<String, DataSource> all = getAll(context);
        DataSource ds = all.get(key);
        if (ds == null) s_log.error("get() no data source named " + key + " in " + CONFIG);
        return ds;
    }

    private static synchronized Map<String, DataSource> getAll(ServletContext context) {
        if (sources == null) {
            sources = load(context.getRealPath(CONFIG));
        }
        return sources;
    }

    static Map<String, DataSource> load(String path) {
        Map<String, DataSource> out = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setValidating(false);
            f.setNamespaceAware(false);
            // The file declares the old Struts DTD; don't fetch it.
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new File(path));
            NodeList list = doc.getElementsByTagName("data-source");
            for (int i = 0; i < list.getLength(); i++) {
                Element el = (Element) list.item(i);
                String key = el.getAttribute("key");
                String type = el.getAttribute("type");
                Object ds = Class.forName(type).getDeclaredConstructor().newInstance();
                NodeList props = el.getElementsByTagName("set-property");
                for (int j = 0; j < props.getLength(); j++) {
                    Element p = (Element) props.item(j);
                    String name = p.getAttribute("property");
                    String value = p.getAttribute("value");
                    if (PropertyUtils.isWriteable(ds, name)) {
                        BeanUtils.setProperty(ds, name, value);
                    } else {
                        // Struts ignored properties the class does not have (factory, auth, name).
                        s_log.debug("load() " + key + ": ignoring property " + name);
                    }
                }
                out.put(key, (DataSource) ds);
                s_log.info("load() data source " + key + " (" + type + ")");
            }
        } catch (Exception e) {
            s_log.error("load() could not read data sources from " + path + " e:" + e, e);
        }
        return out;
    }

    /** Close the pools when the application stops (called from AppContextListener). */
    public static synchronized void closeAll() {
        if (sources == null) return;
        for (Map.Entry<String, DataSource> e : sources.entrySet()) {
            try {
                Method close = e.getValue().getClass().getMethod("close");
                close.invoke(e.getValue());
            } catch (Exception ex) {
                s_log.warn("closeAll() " + e.getKey() + " e:" + ex);
            }
        }
        sources = null;
    }
}
