import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.calacademy.antweb.util.HtmlSanitizer;

/**
 * Command-line access to the server's HtmlSanitizer for test_editor_roundtrip.py (compiled
 * there together with src/org/calacademy/antweb/util/HtmlSanitizer.java, which uses only the JDK).
 *
 *   java EditorRules valid            prints editorValidElements(false), a newline, editorValidElements(true)
 *   java EditorRules sanitize < in    in: records "0|html" or "1|html" (1 = videos field) separated by
 *                                     U+0000; prints the sanitized records separated by U+0000
 */
public class EditorRules {
    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length == 1 && "valid".equals(args[0])) {
            out.print(HtmlSanitizer.editorValidElements(false) + "\n" + HtmlSanitizer.editorValidElements(true));
            return;
        }
        if (args.length == 1 && "sanitize".equals(args[0])) {
            String all = new String(readAll(System.in), StandardCharsets.UTF_8);
            String[] records = all.split("\u0000", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < records.length; i++) {
                String r = records[i];
                boolean videos = r.startsWith("1|");
                String html = r.length() >= 2 ? r.substring(2) : "";
                if (i > 0) sb.append('\u0000');
                sb.append(HtmlSanitizer.sanitize(html, videos));
            }
            out.print(sb);
            return;
        }
        System.err.println("usage: EditorRules valid | sanitize");
        System.exit(2);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
