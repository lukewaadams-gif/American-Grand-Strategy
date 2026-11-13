package game.mandate.web.kit;

import java.util.List;

/** A real table: weighted columns, eyebrow headers, striped rows; a cell is text or a control. */
public class Table extends El {
    public record Col(String header, double weight, boolean right) {
        public static Col left(String h, double w) { return new Col(h, w, false); }
        public static Col right(String h, double w) { return new Col(h, w, true); }
    }

    private final List<Col> cols;
    private final El body;
    private int rows;

    public Table(List<Col> cols) {
        super("table");
        cls("table");
        this.cols = cols;
        double total = cols.stream().mapToDouble(Col::weight).sum();
        El colgroup = new El("colgroup");
        for (Col c : cols) colgroup.add(new El("col").css("width", (Math.round(c.weight / total * 1000) / 10.0) + "%"));
        El head = new El("thead"), tr = new El("tr");
        for (Col c : cols) tr.add(new El("th").attr("scope", "col").cls(c.right ? "right" : "").text(c.header));
        add(colgroup, head.add(tr));
        body = new El("tbody");
        add(body);
    }

    public Table row(Object... cells) {
        El tr = new El("tr");
        for (int i = 0; i < cols.size(); i++) {
            Object v = i < cells.length ? cells[i] : "";
            El td = new El("td").cls(cols.get(i).right ? "right" : "");
            if (v instanceof El e) td.add(e); else td.text(v == null ? "" : String.valueOf(v));
            tr.add(td);
        }
        body.add(tr);
        rows++;
        return this;
    }

    public int rowCount() { return rows; }
}
