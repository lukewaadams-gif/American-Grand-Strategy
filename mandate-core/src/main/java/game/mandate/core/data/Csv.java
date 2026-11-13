package game.mandate.core.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV reader for bundled content files. Lines starting with '#' are comments.
 * Fields may not contain commas; content files are authored to respect that.
 * (A JSON/schema-validated content pipeline replaces this in milestone M2.)
 */
public final class Csv {
    private Csv() {}

    /**
     * A tab-separated table exported from a source game ({@code data/rd/*.tsv}): '#' lines are comments and the first
     * other line is the header, which is skipped. Fields may contain commas.
     */
    public static List<String[]> readTsv(String path) {
        InputStream in = Csv.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("Missing content resource " + path);
        List<String[]> rows = new ArrayList<>();
        boolean header = true;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                if (header) { header = false; continue; }
                rows.add(line.split("\t", -1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    public static List<String[]> readResource(String path) {
        InputStream in = Csv.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("Missing content resource " + path);
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                String t = line.strip();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String[] f = t.split(",", -1);
                for (int i = 0; i < f.length; i++) f[i] = f[i].strip();
                rows.add(f);
                if (f.length < 2) throw new IllegalStateException(path + ":" + lineNo + " has too few fields");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
