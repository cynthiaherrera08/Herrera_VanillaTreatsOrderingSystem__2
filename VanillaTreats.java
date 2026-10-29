import java.awt.*;
import java.io.*;
import java.sql.*;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/** Vanilla Treats - cake ordering system (Java 8, Swing + SQLite). Needs the sqlite-jdbc jar on the classpath. */
public class VanillaTreats {
    static final Color PINK = new Color(0xE54278), BG = new Color(0xFFF5F8), SOFT = new Color(0xFDE6EE),
            INK = new Color(0x4A1F2E), LINE = new Color(0xF3CDD6), MUT = new Color(0x8A6572);
    static final String[] PIPE = {"Pending", "Payment Confirmed", "Preparing", "Ready", "Completed"};
    static final String[] SIZES = {"6\"", "8\"", "10\""}, FLAVORS = {"Vanilla", "Strawberry", "Chocolate", "Ube", "Mocha", "Red velvet", "Cookies and cream", "Mango"};
    static final double[] MULT = {1, 1.4, 1.9};
    static final String[] CATS = {"All Cakes", "Best Sellers", "Birthday Cakes", "Chocolate Cakes", "Cheesecakes", "Red Velvet", "Custom Cakes"};
    static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
    static final NumberFormat NF = new DecimalFormat("#,##0.00");
    static final File FILE = new File(System.getProperty("user.home"), "vanilla-treats.db");
    static Connection db;

    // ---------- data ----------
    static class Product implements Serializable {
        private static final long serialVersionUID = 1L;
        int id; String name, fl; double price; boolean on = true; int[] c; String[] cats;
        Product(int id, String n, double p, String fl, int[] c, String... cats) { this.id = id; name = n; price = p; this.fl = fl; this.c = c; this.cats = cats; }
        public String toString() { return name; }
    }
    static class Pay implements Serializable { private static final long serialVersionUID = 1L; String when, method, type; double amt; }
    static class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        String no, name, email, cake, flavor, size, msg = "", notes = "", img = "", method, addr = "", status = "Pending", pm = "";
        int qty, hour; LocalDate date; double sub, fee, disc, total; boolean refunded; List<Pay> pays = new ArrayList<>();
        double paid() { double t = 0; for (Pay p : pays) t += p.type.equals("refund") ? -p.amt : p.amt; return t; }
        String payLabel() { return refunded ? "Refunded" : paid() >= total - .5 ? "Paid" : paid() > 0 ? "Deposit paid" : "Unpaid"; }
    }
    static class Settings implements Serializable {
        private static final long serialVersionUID = 1L;
        int cap = 5, slotCap = 2, policy = 24, lead = 1; double fee = 150; String pay = "deposit";
        Set<LocalDate> blocked = new TreeSet<>();
        int[] open = {-1, 9, 9, 9, 9, 9, 10}, close = {0, 17, 17, 17, 17, 17, 15}; // index 0 = Sunday, open -1 = closed
    }
    static class Store implements Serializable {
        private static final long serialVersionUID = 1L;
        int seq = 125; Settings st = new Settings(); List<Product> products = new ArrayList<>(); List<Order> orders = new ArrayList<>(); List<User> users = new ArrayList<>();
    }
    static String hash(String x) { try { java.security.MessageDigest m = java.security.MessageDigest.getInstance("SHA-256"); StringBuilder b = new StringBuilder(); for (byte v : m.digest(x.getBytes(java.nio.charset.StandardCharsets.UTF_8))) b.append(String.format("%02x", v)); return b.toString(); } catch (Exception e) { throw new RuntimeException(e); } }
    static class User implements Serializable {
        private static final long serialVersionUID = 1L; String email, name, pass, answer;
        User() { }
        User(String e, String n, String p, String a) { email = e; name = n; pass = hash(e + ":" + p); answer = hash(e + ":" + a.trim().toLowerCase()); }
    }
    static User findUser(String em) { for (User u : S.users) if (u.email.equalsIgnoreCase(em.trim())) return u; return null; }
    static class Draft { Product p; String fl, size, msg, notes, img, method, addr, promo = ""; int qty, hour; LocalDate date; }

    static Store S; static boolean in, admin; static String user = "", email = ""; static Product pending;
    static JFrame frame; static JPanel content, nav; static Supplier<JComponent> cur;

    // ---------- SQLite storage ----------
    static String join(int[] a) { StringBuilder b = new StringBuilder(); for (int i = 0; i < a.length; i++) b.append(i > 0 ? "," : "").append(a[i]); return b.toString(); }
    static int[] ints(String s) { String[] p = s.split(","); int[] r = new int[p.length]; for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim()); return r; }
    static void openDb() throws Exception {
        Class.forName("org.sqlite.JDBC"); db = DriverManager.getConnection("jdbc:sqlite:" + FILE.getAbsolutePath());
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS settings(id INTEGER PRIMARY KEY, cap INT, slot_cap INT, policy INT, lead INT, fee REAL, pay TEXT, open_hours TEXT, close_hours TEXT, blocked TEXT, seq INT)");
            s.execute("CREATE TABLE IF NOT EXISTS products(id INTEGER PRIMARY KEY, name TEXT, flavor TEXT, price REAL, on_sale INT, colors TEXT, cats TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS orders(order_no TEXT PRIMARY KEY, name TEXT, email TEXT, cake TEXT, flavor TEXT, size TEXT, qty INT, msg TEXT, notes TEXT, img TEXT, method TEXT, addr TEXT, status TEXT, pm TEXT, hour_slot INT, order_date TEXT, sub REAL, fee REAL, disc REAL, total REAL, refunded INT)");
            s.execute("CREATE TABLE IF NOT EXISTS users(email TEXT PRIMARY KEY, name TEXT, pass_hash TEXT, answer_hash TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, order_no TEXT, paid_at TEXT, method TEXT, kind TEXT, amount REAL)");
        }
    }
    static void save() {
        try {
            db.setAutoCommit(false);
            try (Statement s = db.createStatement()) { s.executeUpdate("DELETE FROM payments"); s.executeUpdate("DELETE FROM users"); s.executeUpdate("DELETE FROM orders"); s.executeUpdate("DELETE FROM products"); s.executeUpdate("DELETE FROM settings"); }
            Settings t = S.st; StringBuilder bl = new StringBuilder(); for (LocalDate d : t.blocked) bl.append(bl.length() > 0 ? "," : "").append(d);
            try (PreparedStatement p = db.prepareStatement("INSERT INTO settings VALUES(1,?,?,?,?,?,?,?,?,?,?)")) {
                p.setInt(1, t.cap); p.setInt(2, t.slotCap); p.setInt(3, t.policy); p.setInt(4, t.lead); p.setDouble(5, t.fee); p.setString(6, t.pay); p.setString(7, join(t.open)); p.setString(8, join(t.close)); p.setString(9, bl.toString()); p.setInt(10, S.seq); p.executeUpdate(); }
            try (PreparedStatement p = db.prepareStatement("INSERT INTO products VALUES(?,?,?,?,?,?,?)")) {
                for (Product x : S.products) { p.setInt(1, x.id); p.setString(2, x.name); p.setString(3, x.fl); p.setDouble(4, x.price); p.setInt(5, x.on ? 1 : 0); p.setString(6, join(x.c)); p.setString(7, String.join("|", x.cats)); p.executeUpdate(); } }
            try (PreparedStatement p = db.prepareStatement("INSERT INTO orders VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"); PreparedStatement q = db.prepareStatement("INSERT INTO payments(order_no,paid_at,method,kind,amount) VALUES(?,?,?,?,?)")) {
                for (Order o : S.orders) {
                    String[] v = {o.no, o.name, o.email, o.cake, o.flavor, o.size}; for (int i = 0; i < 6; i++) p.setString(i + 1, v[i]);
                    p.setInt(7, o.qty); p.setString(8, o.msg); p.setString(9, o.notes); p.setString(10, o.img); p.setString(11, o.method); p.setString(12, o.addr); p.setString(13, o.status); p.setString(14, o.pm);
                    p.setInt(15, o.hour); p.setString(16, o.date.toString()); p.setDouble(17, o.sub); p.setDouble(18, o.fee); p.setDouble(19, o.disc); p.setDouble(20, o.total); p.setInt(21, o.refunded ? 1 : 0); p.executeUpdate();
                    for (Pay y : o.pays) { q.setString(1, o.no); q.setString(2, y.when); q.setString(3, y.method); q.setString(4, y.type); q.setDouble(5, y.amt); q.executeUpdate(); }
                } }
            try (PreparedStatement p = db.prepareStatement("INSERT INTO users VALUES(?,?,?,?)")) { for (User u : S.users) { p.setString(1, u.email); p.setString(2, u.name); p.setString(3, u.pass); p.setString(4, u.answer); p.executeUpdate(); } }
            db.commit();
        } catch (SQLException e) { try { db.rollback(); } catch (SQLException x) { } e.printStackTrace(); JOptionPane.showMessageDialog(null, "Could not save to the database: " + e.getMessage()); }
        finally { try { db.setAutoCommit(true); } catch (SQLException x) { } }
    }
    static Store load() {
        try {
            openDb();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM settings")) { r.next(); if (r.getInt(1) == 0) { Store n = seed(); S = n; save(); return n; } }
            Store st = new Store(); Settings t = st.st;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM settings")) { r.next();
                t.cap = r.getInt("cap"); t.slotCap = r.getInt("slot_cap"); t.policy = r.getInt("policy"); t.lead = r.getInt("lead"); t.fee = r.getDouble("fee"); t.pay = r.getString("pay"); t.open = ints(r.getString("open_hours")); t.close = ints(r.getString("close_hours")); st.seq = r.getInt("seq");
                for (String b : r.getString("blocked").split(",")) if (!b.trim().isEmpty()) t.blocked.add(LocalDate.parse(b.trim())); }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM products ORDER BY id")) { while (r.next()) { Product p = new Product(r.getInt("id"), r.getString("name"), r.getDouble("price"), r.getString("flavor"), ints(r.getString("colors")), r.getString("cats").isEmpty() ? new String[0] : r.getString("cats").split("\\|")); p.on = r.getInt("on_sale") == 1; st.products.add(p); } }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM users")) { while (r.next()) { User u = new User(); u.email = r.getString("email"); u.name = r.getString("name"); u.pass = r.getString("pass_hash"); u.answer = r.getString("answer_hash"); st.users.add(u); } }
            Map<String, Order> by = new HashMap<>();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM orders ORDER BY rowid")) { while (r.next()) {
                Order o = new Order(); o.no = r.getString("order_no"); o.name = r.getString("name"); o.email = r.getString("email"); o.cake = r.getString("cake"); o.flavor = r.getString("flavor"); o.size = r.getString("size"); o.qty = r.getInt("qty");
                o.msg = r.getString("msg"); o.notes = r.getString("notes"); o.img = r.getString("img"); o.method = r.getString("method"); o.addr = r.getString("addr"); o.status = r.getString("status"); o.pm = r.getString("pm");
                o.hour = r.getInt("hour_slot"); o.date = LocalDate.parse(r.getString("order_date")); o.sub = r.getDouble("sub"); o.fee = r.getDouble("fee"); o.disc = r.getDouble("disc"); o.total = r.getDouble("total"); o.refunded = r.getInt("refunded") == 1; st.orders.add(o); by.put(o.no, o); } }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM payments ORDER BY id")) { while (r.next()) { Order o = by.get(r.getString("order_no")); if (o == null) continue; Pay y = new Pay(); y.when = r.getString("paid_at"); y.method = r.getString("method"); y.type = r.getString("kind"); y.amt = r.getDouble("amount"); o.pays.add(y); } }
            if (st.users.isEmpty()) { st.users.add(new User("maria@example.com", "Maria Santos", "cake123", "vanilla")); S = st; save(); }
            return st;
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, "SQLite driver not found.\nAdd the sqlite-jdbc .jar file to your project libraries (see the setup steps)."); System.exit(1);
        } catch (Exception e) { e.printStackTrace(); JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage()); System.exit(1); }
        return null;
    }
    static Store seed() {
        Store s = new Store(); S = s;
        s.products.add(new Product(1, "Red Velvet", 1050, "Red velvet", new int[]{0xFFF4F4, 0xC2352D, 0xFFFFFF, 0xA3222A}, "Best Sellers", "Red Velvet", "Birthday Cakes"));
        s.products.add(new Product(2, "Ube Dream", 950, "Ube", new int[]{0x8E63C4, 0x5B3A94, 0xD9C6EE, 0xB797DE}, "Best Sellers", "Birthday Cakes"));
        s.products.add(new Product(3, "Chocolate Dream", 950, "Chocolate", new int[]{0x5A3427, 0xA0673C, 0x8A5A3A, 0x5A3427}, "Best Sellers", "Chocolate Cakes"));
        s.products.add(new Product(4, "Strawberry Bliss", 950, "Strawberry", new int[]{0xF6A0B8, 0xE54278, 0xFBC7D5, 0xFBDFE7}, "Best Sellers", "Birthday Cakes"));
        s.products.add(new Product(5, "Cookies and Cream", 950, "Cookies and cream", new int[]{0xEFE6DF, 0x3B2A26, 0x3B2A26, 0xEFE6DF}, "Cheesecakes", "Custom Cakes"));
        s.products.add(new Product(6, "Mango Delight", 1000, "Mango", new int[]{0xF6B93B, 0xE58E26, 0xFBE3A0, 0xFDF0C8}, "Cheesecakes"));
        s.products.add(new Product(7, "Black Forest", 1050, "Chocolate", new int[]{0xFFFFFF, 0xB1273A, 0x4A2A22, 0x4A2A22}, "Chocolate Cakes", "Birthday Cakes"));
        s.products.add(new Product(8, "Classic Vanilla", 850, "Vanilla", new int[]{0xF6E6C0, 0xE2C98A, 0xF6E6C0, 0xFAF0D8}, "Birthday Cakes", "Custom Cakes"));
        seedOrder(123, "Maria Santos", "maria@example.com", 4, "8\"", 1, false, 5, 10, "Preparing", true);
        seedOrder(124, "Maria Santos", "maria@example.com", 8, "6\"", 2, false, -10, 13, "Completed", false);
        seedOrder(122, "Jon Reyes", "jon@example.com", 2, "10\"", 1, true, 3, 14, "Pending", false);
        s.users.add(new User("maria@example.com", "Maria Santos", "cake123", "vanilla"));
        return s;
    }
    static void seedOrder(int n, String nm, String em, int pid, String size, int q, boolean del, int days, int h, String st, boolean deposit) {
        Product p = S.products.get(pid - 1); Order o = build(p, p.fl, size, q, del, "", nm, em);
        o.no = "VT-2026-00" + n; o.date = LocalDate.now().plusDays(days); o.hour = h; o.status = st; o.pm = "GCash"; if (del) o.addr = "Sample address";
        Pay y = new Pay(); y.when = LocalDateTime.now().minusDays(1).toString(); y.method = "GCash"; y.type = "payment";
        if (deposit) { y.amt = o.total / 2; o.pays.add(y); } else if (st.equals("Completed")) { y.amt = o.total; o.pays.add(y); }
        S.orders.add(o);
    }
    static double[] calc(Product p, String size, int qty, boolean del, String promo) {
        double sub = p.price * MULT[Arrays.asList(SIZES).indexOf(size)] * qty, fee = del ? S.st.fee : 0, disc = promo.equalsIgnoreCase("VANILLA10") ? sub * .1 : 0;
        return new double[]{sub, fee, disc, sub + fee - disc};
    }
    static Order build(Product p, String fl, String size, int qty, boolean del, String promo, String nm, String em) {
        double[] c = calc(p, size, qty, del, promo); Order o = new Order();
        o.name = nm; o.email = em; o.cake = p.name; o.flavor = fl; o.size = size; o.qty = qty; o.method = del ? "Delivery" : "Pickup";
        o.sub = c[0]; o.fee = c[1]; o.disc = c[2]; o.total = c[3]; return o;
    }

    // ---------- availability ----------
    static class Avail { boolean ok; String why = ""; List<int[]> slots = new ArrayList<>(); }
    static Avail avail(LocalDate d, Order ex, boolean adm) {
        Avail a = new Avail(); Settings st = S.st; int dw = d.getDayOfWeek().getValue() % 7;
        LocalDate min = LocalDate.now().plusDays(adm ? 0 : st.lead); List<Order> act = new ArrayList<>();
        for (Order o : S.orders) if (o.date.equals(d) && !o.status.equals("Cancelled") && o != ex) act.add(o);
        if (d.isBefore(min)) a.why = "Too soon"; else if (st.blocked.contains(d)) a.why = "Unavailable";
        else if (st.open[dw] < 0) a.why = "Closed"; else if (act.size() >= st.cap) a.why = "Fully booked";
        boolean any = false;
        if (st.open[dw] >= 0) for (int h = st.open[dw]; h < st.close[dw]; h++) {
            int n = 0; for (Order o : act) if (o.hour == h) n++;
            boolean free = n < st.slotCap; any |= free; a.slots.add(new int[]{h, free ? 1 : 0});
        }
        if (a.why.isEmpty() && !any) a.why = "Fully booked";
        a.ok = a.why.isEmpty(); return a;
    }
    static String fh(int h) { return (h % 12 == 0 ? 12 : h % 12) + ":00 " + (h < 12 ? "AM" : "PM"); }
    static String money(double d) { return "\u20B1" + NF.format(d); }

    // ---------- UI helpers ----------
    static void aa(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
    static class RB extends JButton {
        boolean primary;
        RB(String t, boolean p) {
            super(t); primary = p; setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setForeground(p ? Color.WHITE : PINK); setFont(getFont().deriveFont(Font.BOLD)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setMargin(new Insets(9, 22, 9, 22));
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g);
            g.setColor(!isEnabled() ? LINE : primary ? (getModel().isRollover() ? PINK.darker() : PINK) : (getModel().isRollover() ? SOFT : Color.WHITE));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            if (!primary) { g.setColor(PINK); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()); }
            g.dispose(); super.paintComponent(g0);
        }
    }
    static JButton btn(String t, boolean primary) { return new RB(t, primary); }
    static JLabel lbl(String t, int size, boolean bold, Color c) { JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size)); l.setForeground(c); return l; }
    static JPanel card() {
        JPanel p = new JPanel() { protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(Color.WHITE); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.setColor(LINE); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24); g.dispose(); } };
        p.setOpaque(false); p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16)); return p;
    }
    static JPanel plain(LayoutManager l) { JPanel p = new JPanel(l); p.setBackground(BG); return p; }
    static javax.swing.border.Border pad(int n) { return BorderFactory.createEmptyBorder(n, n, n, n); }
    static GridBagConstraints gc(int x, int y, int w) { GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; c.gridwidth = w; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(4, 4, 4, 4); return c; }
    static void row(JPanel p, int y, String l, JComponent c) { p.add(lbl(l, 13, true, INK), gc(0, y * 2, 1)); p.add(c, gc(0, y * 2 + 1, 1)); }
    static DocumentListener dl(Runnable r) { return new DocumentListener() { public void insertUpdate(DocumentEvent e) { r.run(); } public void removeUpdate(DocumentEvent e) { r.run(); } public void changedUpdate(DocumentEvent e) { r.run(); } }; }
    static void info(String m) { JOptionPane.showMessageDialog(frame, m, "Vanilla Treats", JOptionPane.INFORMATION_MESSAGE); }

    static class CakeIcon extends JComponent {
        int[] c; CakeIcon(int[] c, int w, int h) { this.c = c; setPreferredSize(new Dimension(w, h)); setMaximumSize(new Dimension(w, h)); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); double s = getWidth() / 120.0; g.scale(s, s);
            g.setColor(new Color(0xF6DFE6)); g.fillOval(8, 63, 104, 14);
            g.setColor(new Color(c[2])); g.fillRect(26, 38, 68, 14); g.setColor(new Color(c[3])); g.fillRoundRect(26, 50, 68, 18, 8, 8);
            g.setColor(new Color(c[0])); g.fillRoundRect(26, 34, 68, 10, 8, 8);
            g.setColor(new Color(c[1])); for (int x : new int[]{34, 47, 60, 73, 86}) g.fillOval(x - 4, 26, 9, 9);
            g.dispose();
        }
    }
    static class Steps extends JComponent {
        String st; Steps(String s) { st = s; setPreferredSize(new Dimension(520, 34)); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setFont(getFont().deriveFont(11f));
            if (st.equals("Cancelled")) { g.setColor(new Color(0xB3364F)); g.drawString("Cancelled", 2, 16); return; }
            int n = PIPE.length, w = getWidth() / n, idx = Arrays.asList(PIPE).indexOf(st);
            for (int i = 0; i < n; i++) { g.setColor(i <= idx ? PINK : LINE); g.fillRoundRect(i * w + 2, 4, w - 4, 6, 6, 6); g.setColor(i <= idx ? PINK : MUT); g.drawString(PIPE[i], i * w + 2, 26); }
            g.dispose();
        }
    }

    // ---------- calendar ----------
    static class CalendarPanel extends JPanel {
        YearMonth ym = YearMonth.now(); LocalDate date; Integer hour; Order ex; boolean adm, view; Runnable ch; Consumer<LocalDate> onDay;
        CalendarPanel(Order ex, boolean adm, boolean view, Runnable ch) { this.ex = ex; this.adm = adm; this.view = view; this.ch = ch; setOpaque(false); setLayout(new BorderLayout(0, 8)); build(); }
        void build() {
            removeAll(); JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
            JButton pv = btn("<", false), nx = btn(">", false); pv.setEnabled(ym.isAfter(YearMonth.now()));
            pv.addActionListener(e -> { ym = ym.minusMonths(1); build(); }); nx.addActionListener(e -> { ym = ym.plusMonths(1); build(); });
            JLabel t = lbl(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear(), 15, true, INK); t.setHorizontalAlignment(SwingConstants.CENTER);
            top.add(pv, BorderLayout.WEST); top.add(t, BorderLayout.CENTER); top.add(nx, BorderLayout.EAST);
            JPanel g = new JPanel(new GridLayout(0, 7, 4, 4)); g.setOpaque(false);
            for (String s : "S M T W T F S".split(" ")) { JLabel l = lbl(s, 12, true, MUT); l.setHorizontalAlignment(SwingConstants.CENTER); g.add(l); }
            for (int i = 0; i < ym.atDay(1).getDayOfWeek().getValue() % 7; i++) g.add(new JLabel());
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                LocalDate day = ym.atDay(d); Avail a = avail(day, ex, adm); int cnt = 0;
                for (Order o : S.orders) if (o.date.equals(day) && !o.status.equals("Cancelled")) cnt++;
                JButton b = new JButton(view && cnt > 0 ? "<html><center>" + d + "<br><small>" + cnt + " cake" + (cnt > 1 ? "s" : "") + "</small></center></html>" : "" + d);
                b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true); b.setMargin(new Insets(4, 0, 4, 0));
                b.setBackground(day.equals(date) ? PINK : S.st.blocked.contains(day) && view ? LINE : SOFT); b.setForeground(day.equals(date) ? Color.WHITE : INK);
                if (!view && !a.ok) { b.setEnabled(false); b.setToolTipText(a.why); b.setBackground(new Color(0xF4F0F1)); }
                b.addActionListener(e -> { if (view) { if (onDay != null) onDay.accept(day); } else { date = day; hour = null; build(); } });
                g.add(b);
            }
            JPanel sl = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); sl.setOpaque(false);
            if (!view) {
                if (date == null) sl.add(lbl("Pick a date to see available times. Greyed-out days are closed, blocked or full.", 12, false, MUT));
                else { sl.add(lbl("Times:", 13, true, INK)); ButtonGroup bg = new ButtonGroup();
                    for (int[] s : avail(date, ex, adm).slots) { JToggleButton tb = new JToggleButton(fh(s[0])); tb.setEnabled(s[1] == 1); tb.setSelected(hour != null && hour == s[0]); bg.add(tb);
                        final int h = s[0]; tb.addActionListener(e -> { hour = h; ch.run(); }); sl.add(tb); } }
            }
            add(top, BorderLayout.NORTH); add(g, BorderLayout.CENTER); add(sl, BorderLayout.SOUTH); revalidate(); repaint(); if (ch != null) ch.run();
        }
    }

    // ---------- app shell ----------
    public static void main(String[] a) throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        for (Object k : Collections.list(UIManager.getDefaults().keys())) { Object v = UIManager.get(k); if (v instanceof Font) UIManager.put(k, new javax.swing.plaf.FontUIResource(((Font) v).deriveFont(13.5f))); }
        S = load(); SwingUtilities.invokeLater(VanillaTreats::start);
    }
    static String page = "home", nextPage = "", catSel = CATS[0], searchTxt = "";
    static void start() {
        frame = new JFrame("Vanilla Treats"); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setSize(1120, 780); frame.setMinimumSize(new Dimension(920, 640)); frame.setLocationRelativeTo(null);
        nav = new JPanel(new BorderLayout()); nav.setBackground(Color.WHITE); nav.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        content = plain(new BorderLayout()); frame.add(nav, BorderLayout.NORTH); frame.add(content); go("home"); frame.setVisible(true);
    }
    static void show(Supplier<JComponent> s) { page = nextPage; nextPage = ""; cur = s; content.removeAll(); content.add(s.get()); content.revalidate(); content.repaint(); refreshNav(); }
    /** Go to one of the public pages: home, cakes, about, contact. */
    static void go(String p) {
        nextPage = p;
        if (p.equals("home")) show(VanillaTreats::home); else if (p.equals("cakes")) show(VanillaTreats::catalog); else if (p.equals("about")) show(VanillaTreats::about); else show(VanillaTreats::contact);
    }
    static void view(Product p) { nextPage = "cakes"; show(() -> cakeScreen(p)); }
    static JButton navBtn(String t, String key) {
        boolean on = page.equals(key); JButton b = new JButton(t); b.setContentAreaFilled(false); b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(on ? PINK : INK); b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, on ? PINK : Color.WHITE), BorderFactory.createEmptyBorder(6, 8, 2, 8)));
        b.addActionListener(e -> go(key)); return b;
    }
    static void refreshNav() {
        nav.removeAll(); JLabel b = lbl("Vanilla Treats", 22, true, PINK); b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { go("home"); } }); nav.add(b, BorderLayout.WEST);
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14)); mid.setOpaque(false); mid.add(navBtn("Home", "home")); mid.add(navBtn("Cakes", "cakes")); mid.add(navBtn("About us", "about")); mid.add(navBtn("Contact", "contact")); nav.add(mid, BorderLayout.CENTER);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10)); r.setOpaque(false);
        JTextField sf = new JTextField(searchTxt, 12); sf.setToolTipText("Search cakes, then press Enter");
        sf.addActionListener(e -> { searchTxt = sf.getText().trim(); catSel = CATS[0]; go("cakes"); }); r.add(lbl("Search", 12, false, MUT)); r.add(sf);
        if (in && !admin) { JButton d = btn("My dashboard", false); d.addActionListener(e -> show(VanillaTreats::dash)); r.add(d); }
        if (in && admin) { JButton d = btn("Admin", false); d.addActionListener(e -> show(VanillaTreats::adminScreen)); r.add(d); }
        JButton s = btn(in ? "Sign out" : "Sign in", true); s.addActionListener(e -> { if (in) { in = false; admin = false; go("home"); } else show(VanillaTreats::login); }); r.add(s);
        nav.add(r, BorderLayout.EAST); nav.revalidate(); nav.repaint();
    }

    // ---------- public pages ----------
    static JPanel rounded(Color fill) {
        JPanel p = new JPanel() { protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28); g.dispose(); } };
        p.setOpaque(false); return p;
    }
    static <T extends JComponent> T left(T c) { c.setAlignmentX(0f); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height)); return c; }
    static JScrollPane scroller(JComponent inner) {
        JPanel wrap = plain(new BorderLayout()); wrap.setBorder(pad(16)); wrap.add(inner, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.getViewport().setBackground(BG); sp.getVerticalScrollBar().setUnitIncrement(16); return sp;
    }
    static JComponent home() {
        JPanel box = plain(null); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JPanel hero = rounded(SOFT); hero.setLayout(new BorderLayout()); hero.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        JPanel l = new JPanel(); l.setOpaque(false); l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
        JLabel h = new JLabel("<html><div style='font-size:34px;color:#4A1F2E;font-weight:bold'>Sweet moments<br>start with<br><span style='color:#E54278'>Vanilla Treats</span></div></html>"); h.setAlignmentX(0f);
        JLabel d = new JLabel("<html><div style='width:300px;color:#8A6572'>Order your favourite cakes online and we deliver the happiness to your door.</div></html>"); d.setAlignmentX(0f);
        JButton shop = btn("Shop cakes", true); shop.setAlignmentX(0f); shop.addActionListener(e -> go("cakes"));
        l.add(h); l.add(Box.createVerticalStrut(10)); l.add(d); l.add(Box.createVerticalStrut(18)); l.add(shop);
        hero.add(l, BorderLayout.WEST); hero.add(new CakeIcon(new int[]{0xF6A0B8, 0xE54278, 0xFBC7D5, 0xFBDFE7}, 300, 200), BorderLayout.EAST); box.add(left(hero)); box.add(Box.createVerticalStrut(16));
        String[][] f = {{"Freshly baked every day", "Nothing sits overnight."}, {"Premium ingredients", "Real butter, real fruit."}, {"Custom cakes available", "Tell us the occasion."}, {"Delivered with care", "Chilled, boxed, on time."}};
        JPanel fp = plain(new GridLayout(1, 4, 12, 12)); for (String[] x : f) { JPanel c = card(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS)); c.add(lbl(x[0], 13, true, INK)); c.add(lbl(x[1], 12, false, MUT)); fp.add(c); }
        box.add(left(fp)); box.add(Box.createVerticalStrut(22));
        JPanel hd = plain(new BorderLayout()); hd.add(lbl("Loved by our customers", 20, true, INK), BorderLayout.WEST); JButton all = link("See the full menu"); all.addActionListener(e -> { searchTxt = ""; catSel = CATS[0]; go("cakes"); }); hd.add(all, BorderLayout.EAST);
        box.add(left(hd)); box.add(Box.createVerticalStrut(10));
        List<Product> pick = new ArrayList<>(); for (Product p : S.products) if (p.on && Arrays.asList(p.cats).contains("Best Sellers")) pick.add(p);
        for (Product p : S.products) if (p.on && !pick.contains(p)) pick.add(p);
        JPanel g = plain(new GridLayout(1, 4, 14, 14)); for (int i = 0; i < 4; i++) { if (i < pick.size()) g.add(pcard(pick.get(i))); else g.add(plain(null)); }
        box.add(left(g)); return scroller(box);
    }
    static JComponent catalog() {
        JPanel root = plain(new BorderLayout(14, 0)); root.setBorder(pad(16));
        JPanel side = card(); side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS)); JLabel ct = lbl("Categories", 15, true, INK); ct.setAlignmentX(0f); side.add(ct); side.add(Box.createVerticalStrut(8)); JComponent sut = (JComponent) Box.createHorizontalStrut(170); sut.setAlignmentX(0f); side.add(sut);
        JLabel title = lbl(catSel, 24, true, INK), count = lbl("", 13, false, MUT); JComboBox<String> so = new JComboBox<>(new String[]{"Most popular", "Price: low to high", "Price: high to low", "Name A-Z"});
        JPanel grid = new JPanel(new GridLayout(0, 4, 14, 14)); grid.setOpaque(false); JLabel srch = lbl("", 12, false, MUT); JButton clr = link("Clear search"); clr.setVisible(!searchTxt.isEmpty());
        List<JToggleButton> tbs = new ArrayList<>();
        Runnable style = () -> { for (JToggleButton t : tbs) { boolean on = t.getText().equals(catSel); t.setBackground(on ? SOFT : Color.WHITE); t.setForeground(on ? PINK : INK); t.setFont(t.getFont().deriveFont(on ? Font.BOLD : Font.PLAIN)); } };
        Runnable fill = () -> {
            grid.removeAll(); List<Product> l = new ArrayList<>();
            for (Product p : S.products) if (p.on && (catSel.equals(CATS[0]) || Arrays.asList(p.cats).contains(catSel)) && p.name.toLowerCase().contains(searchTxt.toLowerCase())) l.add(p);
            int k = so.getSelectedIndex(); if (k == 1) l.sort(Comparator.comparingDouble((Product p) -> p.price)); if (k == 2) l.sort(Comparator.comparingDouble((Product p) -> -p.price)); if (k == 3) l.sort(Comparator.comparing((Product p) -> p.name));
            for (Product p : l) grid.add(pcard(p)); if (l.isEmpty()) grid.add(lbl("No cakes match. Try another category or search.", 14, false, MUT));
            title.setText(catSel); count.setText(l.size() + " cake" + (l.size() == 1 ? "" : "s")); srch.setText(searchTxt.isEmpty() ? "" : "Search: \"" + searchTxt + "\""); style.run(); grid.revalidate(); grid.repaint();
        };
        ButtonGroup bg = new ButtonGroup();
        for (String c : CATS) { JToggleButton t = new JToggleButton(c); t.setHorizontalAlignment(SwingConstants.LEFT); t.setBorderPainted(false); t.setFocusPainted(false); t.setOpaque(true); t.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34)); t.setAlignmentX(0f);
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); t.addActionListener(e -> { catSel = c; fill.run(); }); bg.add(t); tbs.add(t); side.add(t); side.add(Box.createVerticalStrut(2)); }
        so.addActionListener(e -> fill.run()); clr.addActionListener(e -> { searchTxt = ""; clr.setVisible(false); refreshNav(); fill.run(); });
        JPanel hd = plain(new BorderLayout()); JPanel hl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); hl.setOpaque(false); hl.add(title); hl.add(count); hl.add(srch); hl.add(clr); hd.add(hl, BorderLayout.WEST); hd.add(so, BorderLayout.EAST);
        fill.run(); JPanel wrap = plain(new BorderLayout()); wrap.add(grid, BorderLayout.NORTH); JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.getViewport().setBackground(BG); sp.getVerticalScrollBar().setUnitIncrement(16);
        JPanel sw = plain(new BorderLayout()); sw.add(side, BorderLayout.NORTH); JPanel main = plain(new BorderLayout(0, 12)); main.add(hd, BorderLayout.NORTH); main.add(sp);
        root.add(sw, BorderLayout.WEST); root.add(main); return root;
    }
    static JPanel pcard(Product p) {
        JPanel c = card(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS)); CakeIcon ic = new CakeIcon(p.c, 120, 80); ic.setAlignmentX(.5f);
        JLabel n = lbl(p.name, 15, true, INK), pr = lbl(money(p.price), 14, true, PINK); n.setAlignmentX(.5f); pr.setAlignmentX(.5f);
        JButton b = btn("View cake", true); b.setAlignmentX(.5f); b.addActionListener(e -> view(p));
        c.add(ic); c.add(Box.createVerticalStrut(8)); c.add(n); c.add(pr); c.add(Box.createVerticalStrut(8)); c.add(b); return c;
    }
    static JComponent cakeScreen(Product p) {
        JPanel root = plain(new BorderLayout(0, 12)); root.setBorder(pad(16)); JButton back = link("< All cakes"); back.setHorizontalAlignment(SwingConstants.LEFT); back.addActionListener(e -> go("cakes"));
        JPanel c = card(); c.setLayout(new BorderLayout(24, 0)); c.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30)); c.add(new CakeIcon(p.c, 300, 200), BorderLayout.WEST);
        JPanel i = new JPanel(); i.setOpaque(false); i.setLayout(new BoxLayout(i, BoxLayout.Y_AXIS));
        JLabel nm = lbl(p.name, 28, true, INK); nm.setAlignmentX(0f); i.add(nm);
        JLabel ds = new JLabel("<html><div style='width:320px;color:#8A6572'>" + p.fl + " flavor, baked fresh for your date. Add a message, a reference photo and special instructions when you order.</div></html>"); ds.setAlignmentX(0f); i.add(Box.createVerticalStrut(6)); i.add(ds); i.add(Box.createVerticalStrut(12));
        for (int k = 0; k < SIZES.length; k++) { JLabel z = lbl(SIZES[k] + " cake:   " + money(p.price * MULT[k]), 15, true, PINK); z.setAlignmentX(0f); i.add(z); i.add(Box.createVerticalStrut(4)); }
        JButton ob = btn("Order this cake", true); ob.setAlignmentX(0f); ob.addActionListener(e -> startOrder(p)); i.add(Box.createVerticalStrut(12)); i.add(ob); c.add(i);
        JPanel w = plain(new BorderLayout()); w.add(c, BorderLayout.NORTH); root.add(back, BorderLayout.NORTH); root.add(w); return root;
    }
    static JComponent textPage(String title, String... paras) {
        JPanel c = card(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS)); c.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        JLabel t = lbl(title, 26, true, INK); t.setAlignmentX(0f); c.add(t); c.add(Box.createVerticalStrut(10));
        for (String x : paras) { JLabel l = new JLabel("<html><div style='width:560px'>" + x + "</div></html>"); l.setAlignmentX(0f); c.add(l); c.add(Box.createVerticalStrut(8)); }
        JPanel w = plain(new BorderLayout()); w.setBorder(pad(16)); w.add(c, BorderLayout.NORTH); return w;
    }
    static JComponent about() { return textPage("About Vanilla Treats", "We bake small batches every morning with real butter and fresh fruit, then box and chill each cake so it arrives just right.", "Have a special occasion? Choose a custom cake and tell us the details when you order."); }
    static JComponent contact() { return textPage("Contact us", "Questions about an order or a custom design? Message us and we will reply within a day.", "<b>Email:</b> hello@vanillatreats.example<br><b>Phone:</b> 0900 000 0000", "<span style='color:#8A6572'>Replace these with your real contact details.</span>"); }
    static void startOrder(Product p) { if (!in) { pending = p; show(VanillaTreats::login); info("Please sign in to place your order."); } else if (admin) info("Owner accounts manage orders. Sign in as a customer to order."); else show(() -> orderScreen(p)); }

    static JButton link(String t) { JButton b = new JButton(t); b.setBorderPainted(false); b.setContentAreaFilled(false); b.setFocusPainted(false); b.setForeground(PINK); b.setFont(b.getFont().deriveFont(Font.BOLD)); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setHorizontalAlignment(SwingConstants.RIGHT); return b; }
    static JComponent login() {
        JPanel root = plain(new GridBagLayout()); JPanel lp = plain(null); lp.setLayout(new BoxLayout(lp, BoxLayout.Y_AXIS));
        CakeIcon ic = new CakeIcon(new int[]{0xF6A0B8, 0xE54278, 0xFBC7D5, 0xFBDFE7}, 300, 200); ic.setAlignmentX(.5f);
        JLabel bn = lbl("Vanilla Treats", 36, true, PINK), tg = lbl("Delicious cakes, made with love.", 15, false, MUT); bn.setAlignmentX(.5f); tg.setAlignmentX(.5f); lp.add(ic); lp.add(bn); lp.add(tg);
        JPanel c = card(); c.setLayout(new GridBagLayout()); c.setPreferredSize(new Dimension(410, 610));
        JTextField ef = new JTextField("maria@example.com"); JPasswordField pf = new JPasswordField(), ow = new JPasswordField();
        c.add(lbl("Welcome back", 24, true, INK), gc(0, 0, 1)); c.add(lbl("Sign in to continue your sweet journey.", 13, false, MUT), gc(0, 1, 1));
        row(c, 1, "Email", ef); row(c, 2, "Password", pf);
        JButton fp = link("Forgot password?"); fp.addActionListener(e -> forgot()); c.add(fp, gc(0, 6, 1));
        JButton go = btn("Sign in", true); go.addActionListener(e -> {
            User u = findUser(ef.getText()); if (u == null || !u.pass.equals(hash(u.email + ":" + new String(pf.getPassword())))) { info("That email or password doesn't match. Try again, or use Forgot password."); return; }
            in = true; admin = false; user = u.name; email = u.email.toLowerCase();
            if (pending != null) { Product p = pending; pending = null; show(() -> orderScreen(p)); } else show(VanillaTreats::dash); });
        pf.addActionListener(e -> go.doClick()); c.add(go, gc(0, 7, 1));
        JButton ca = btn("Create an account", false); ca.addActionListener(e -> register(ef)); c.add(ca, gc(0, 8, 1));
        JLabel demo = lbl("Demo account: maria@example.com / cake123", 12, false, MUT); demo.setHorizontalAlignment(SwingConstants.CENTER); c.add(demo, gc(0, 9, 1));
        row(c, 5, "Shop owner passcode", ow);
        JButton ad = btn("Open admin dashboard", false); ad.addActionListener(e -> {
            if (new String(ow.getPassword()).equals("vanilla2026")) { in = true; admin = true; user = "Owner"; show(VanillaTreats::adminScreen); } else info("Wrong passcode. Demo passcode: vanilla2026"); });
        c.add(ad, gc(0, 12, 1)); GridBagConstraints g = new GridBagConstraints(); g.insets = new Insets(0, 20, 0, 20); root.add(lp, g); root.add(c, g); return root;
    }
    static void register(JTextField target) {
        JDialog d = new JDialog(frame, "Create an account", true); JPanel p = plain(new GridBagLayout()); p.setBorder(pad(18));
        JTextField nm = new JTextField(), em = new JTextField(), an = new JTextField(); JPasswordField p1 = new JPasswordField(), p2 = new JPasswordField();
        row(p, 0, "Full name", nm); row(p, 1, "Email", em); row(p, 2, "Password (at least 6 characters)", p1); row(p, 3, "Confirm password", p2); row(p, 4, "Security question: What is your favorite cake flavor?", an);
        JButton ok = btn("Create account", true); ok.addActionListener(e -> {
            String e1 = em.getText().trim().toLowerCase(), pw = new String(p1.getPassword());
            if (nm.getText().trim().isEmpty() || !e1.contains("@")) { info("Enter your name and a valid email."); return; }
            if (pw.length() < 6) { info("Your password needs at least 6 characters."); return; } if (!pw.equals(new String(p2.getPassword()))) { info("The two passwords don't match."); return; }
            if (an.getText().trim().isEmpty()) { info("Enter an answer to the security question. You will need it if you forget your password."); return; }
            if (findUser(e1) != null) { info("That email already has an account. Try signing in."); return; }
            S.users.add(new User(e1, nm.getText().trim(), pw, an.getText())); save(); target.setText(e1); d.dispose(); info("Account created! You can sign in now."); });
        JButton cn = btn("Cancel", false); cn.addActionListener(e -> d.dispose()); JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bs.setOpaque(false); bs.add(cn); bs.add(ok); p.add(bs, gc(0, 10, 1));
        d.setContentPane(p); d.pack(); d.setMinimumSize(new Dimension(420, 0)); d.setLocationRelativeTo(frame); d.setVisible(true);
    }
    static void forgot() {
        JDialog d = new JDialog(frame, "Reset your password", true); JPanel p = plain(new GridBagLayout()); p.setBorder(pad(18));
        JTextField em = new JTextField(), an = new JTextField(); JPasswordField p1 = new JPasswordField(), p2 = new JPasswordField();
        p.add(lbl("Answer your security question to set a new password.", 12, false, MUT), gc(0, 0, 1));
        row(p, 1, "Email", em); row(p, 2, "What is your favorite cake flavor?", an); row(p, 3, "New password (at least 6 characters)", p1); row(p, 4, "Confirm new password", p2);
        JButton ok = btn("Reset password", true); ok.addActionListener(e -> {
            User u = findUser(em.getText()); String pw = new String(p1.getPassword());
            if (u == null || !u.answer.equals(hash(u.email + ":" + an.getText().trim().toLowerCase()))) { info("We couldn't verify those details. Check your email and answer."); return; }
            if (pw.length() < 6) { info("Your new password needs at least 6 characters."); return; } if (!pw.equals(new String(p2.getPassword()))) { info("The two passwords don't match."); return; }
            u.pass = hash(u.email + ":" + pw); save(); d.dispose(); info("Password updated! You can sign in now."); });
        JButton cn = btn("Cancel", false); cn.addActionListener(e -> d.dispose()); JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bs.setOpaque(false); bs.add(cn); bs.add(ok); p.add(bs, gc(0, 12, 1));
        d.setContentPane(p); d.pack(); d.setMinimumSize(new Dimension(420, 0)); d.setLocationRelativeTo(frame); d.setVisible(true);
    }

    static JComponent dash() {
        JPanel box = plain(null); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS)); box.setBorder(pad(20));
        JLabel h = lbl("Hello, " + user.split(" ")[0] + "!", 28, true, INK); h.setAlignmentX(0); box.add(h);
        JLabel s = lbl("Your cakes, all in one place.", 14, false, MUT); s.setAlignmentX(0); box.add(s); box.add(Box.createVerticalStrut(8));
        JButton nb = btn("Place a new cake order", true); nb.setAlignmentX(0); nb.addActionListener(e -> { for (Product p : S.products) if (p.on) { startOrder(p); return; } info("No cakes are available right now."); }); box.add(nb);
        List<Order> mine = new ArrayList<>(); for (Order o : S.orders) if (o.email.equals(email)) mine.add(o); mine.sort(Comparator.comparing((Order o) -> o.date));
        List<Order> up = new ArrayList<>(), past = new ArrayList<>(); for (Order o : mine) (o.status.equals("Completed") || o.status.equals("Cancelled") ? past : up).add(o);
        section(box, "Upcoming orders", up, true); section(box, "Past orders", past, false);
        JPanel wrap = plain(new BorderLayout()); wrap.add(box, BorderLayout.NORTH); JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.getViewport().setBackground(BG); return sp;
    }
    static void section(JPanel box, String t, List<Order> l, boolean up) {
        box.add(Box.createVerticalStrut(16)); JLabel h = lbl(t, 20, true, INK); h.setAlignmentX(0); box.add(h); box.add(Box.createVerticalStrut(6));
        if (l.isEmpty()) { JLabel e = lbl(up ? "No upcoming orders. Pick a cake and a date to get started." : "Your completed orders will show up here.", 14, false, MUT); e.setAlignmentX(0); box.add(e); }
        for (Order o : l) { JPanel c = orderCard(o, up); c.setAlignmentX(0); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height)); box.add(c); box.add(Box.createVerticalStrut(10)); }
    }
    static JPanel orderCard(Order o, boolean up) {
        JPanel c = card(); c.setLayout(new BorderLayout(8, 6));
        c.add(new JLabel("<html><b style='font-size:14px'>" + o.cake + "</b> &nbsp; <span style='color:#8A6572'>" + o.no + "</span><br>" + o.flavor + " \u00B7 " + o.size + " \u00B7 Qty " + o.qty
                + "<br>" + o.method + ": <b>" + o.date.format(DF) + ", " + fh(o.hour) + "</b><br>Total " + money(o.total) + " \u00B7 " + o.payLabel() + " \u00B7 Status: <b>" + o.status + "</b></html>"), BorderLayout.NORTH);
        c.add(new Steps(o.status), BorderLayout.CENTER);
        JPanel s = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); s.setOpaque(false);
        if (up) { JButton r = btn("Reschedule", true); r.addActionListener(e -> { reschedule(o, false); show(cur); }); s.add(r); }
        JButton rc = btn("Order receipt", false); rc.addActionListener(e -> showReceipt(o)); s.add(rc); JButton pc = btn("Payment receipt", false); pc.addActionListener(e -> payMenu(o)); s.add(pc);
        if (up) s.add(lbl("Orders can be rescheduled up to " + S.st.policy + " hours before pickup.", 12, false, MUT));
        c.add(s, BorderLayout.SOUTH);
        return c;
    }

    static void reschedule(Order o, boolean adm) {
        if (!adm && Duration.between(LocalDateTime.now(), o.date.atTime(o.hour, 0)).toHours() < S.st.policy) {
            info("This order can't be rescheduled online because it's within " + S.st.policy + " hours of " + o.method.toLowerCase() + ". Please contact Vanilla Treats."); return; }
        JDialog d = new JDialog(frame, "Reschedule " + o.no, true); JButton rev = btn("Review change", true); rev.setEnabled(false);
        CalendarPanel cal = new CalendarPanel(o, adm, false, null); cal.ch = () -> rev.setEnabled(cal.date != null && cal.hour != null);
        JPanel p = plain(new BorderLayout(0, 10)); p.setBorder(pad(16)); p.add(lbl("Orders can be rescheduled up to " + S.st.policy + " hours before pickup.", 12, false, MUT), BorderLayout.NORTH); p.add(cal);
        JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bs.setOpaque(false); JButton cn = btn("Cancel", false); cn.addActionListener(e -> d.dispose()); bs.add(cn); bs.add(rev); p.add(bs, BorderLayout.SOUTH);
        rev.addActionListener(e -> {
            String msg = "<html>Current: <b>" + o.date.format(DF) + ", " + fh(o.hour) + "</b><br>New: <b>" + cal.date.format(DF) + ", " + fh(cal.hour) + "</b><br><br>Are you sure you want to reschedule this order?</html>";
            if (JOptionPane.showConfirmDialog(d, msg, "Confirm reschedule", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                o.date = cal.date; o.hour = cal.hour; save(); d.dispose(); info("Your order has been rescheduled successfully!"); } });
        d.setContentPane(p); d.setSize(460, 560); d.setLocationRelativeTo(frame); d.setVisible(true);
    }

    static JComponent orderScreen(Product start) {
        JPanel root = plain(new GridLayout(1, 2, 16, 0)); root.setBorder(pad(16)); JPanel left = card(); left.setLayout(new GridBagLayout());
        JComboBox<Product> cake = new JComboBox<>(); for (Product p : S.products) if (p.on) cake.addItem(p); cake.setSelectedItem(start);
        JComboBox<String> fl = new JComboBox<>(FLAVORS), sz = new JComboBox<>(SIZES); fl.setSelectedItem(start.fl);
        JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1)); JTextField msg = new JTextField(), addr = new JTextField(); JTextArea notes = new JTextArea(3, 20); notes.setLineWrap(true);
        JRadioButton pk = new JRadioButton("Pickup", true), dv = new JRadioButton("Delivery (+" + money(S.st.fee) + ")"); ButtonGroup bg = new ButtonGroup(); bg.add(pk); bg.add(dv); pk.setOpaque(false); dv.setOpaque(false);
        JPanel mp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); mp.setOpaque(false); mp.add(pk); mp.add(dv);
        String[] img = {""}; JLabel il = lbl("No image chosen", 12, false, MUT); JButton ib = btn("Upload reference image", false);
        ib.addActionListener(e -> { JFileChooser fc = new JFileChooser(); if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) { img[0] = fc.getSelectedFile().getAbsolutePath(); il.setText(fc.getSelectedFile().getName()); } });
        JPanel ip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); ip.setOpaque(false); ip.add(ib); ip.add(il);
        row(left, 0, "Cake type", cake); row(left, 1, "Flavor", fl); row(left, 2, "Size", sz); row(left, 3, "Quantity", qty); row(left, 4, "Message / design", msg);
        row(left, 5, "Special instructions", new JScrollPane(notes)); row(left, 6, "Reference image (optional)", ip); row(left, 7, "Pickup or delivery", mp); row(left, 8, "Delivery address", addr);
        JPanel right = card(); right.setLayout(new BorderLayout(0, 10)); JLabel tot = lbl("", 20, true, PINK); JButton go = btn("Continue to checkout", true);
        Runnable upd = () -> { double[] c = calc((Product) cake.getSelectedItem(), (String) sz.getSelectedItem(), (Integer) qty.getValue(), dv.isSelected(), ""); tot.setText("Total: " + money(c[3])); addr.setEnabled(dv.isSelected()); };
        CalendarPanel cal = new CalendarPanel(null, false, false, null);
        cake.addActionListener(e -> { fl.setSelectedItem(((Product) cake.getSelectedItem()).fl); upd.run(); }); sz.addActionListener(e -> upd.run()); dv.addActionListener(e -> upd.run()); pk.addActionListener(e -> upd.run()); qty.addChangeListener(e -> upd.run()); upd.run();
        go.addActionListener(e -> {
            if (dv.isSelected() && addr.getText().trim().isEmpty()) { info("Enter a delivery address."); return; }
            if (cal.date == null || cal.hour == null) { info("Select an available date and time first."); return; }
            Draft d = new Draft(); d.p = (Product) cake.getSelectedItem(); d.fl = (String) fl.getSelectedItem(); d.size = (String) sz.getSelectedItem(); d.qty = (Integer) qty.getValue(); d.msg = msg.getText(); d.notes = notes.getText();
            d.img = img[0]; d.method = dv.isSelected() ? "Delivery" : "Pickup"; d.addr = addr.getText().trim(); d.date = cal.date; d.hour = cal.hour; checkout(d); });
        JPanel bot = new JPanel(new BorderLayout(0, 8)); bot.setOpaque(false); bot.add(tot, BorderLayout.NORTH); bot.add(go, BorderLayout.SOUTH);
        right.add(lbl("Choose a date and time", 18, true, INK), BorderLayout.NORTH); right.add(cal); right.add(bot, BorderLayout.SOUTH); root.add(left); root.add(right); return root;
    }

    static double due(double total, String pm) { return pm.equals("Cash") ? 0 : S.st.pay.equals("deposit") ? Math.round(total / 2.0) : total; }
    static void checkout(Draft d) {
        JDialog dlg = new JDialog(frame, "Checkout", true); JPanel p = plain(new GridBagLayout()); p.setBorder(pad(18)); int y = 0;
        String[][] rows = {{"Cake", d.p.name}, {"Size", d.size}, {"Quantity", "" + d.qty}, {d.method + " date", d.date.format(DF)}, {d.method + " time", fh(d.hour)}};
        for (String[] r : rows) { p.add(lbl(r[0], 13, false, MUT), gc(0, y, 1)); p.add(lbl(r[1], 13, true, INK), gc(1, y++, 1)); }
        JLabel[] v = new JLabel[4]; String[] names = {"Subtotal", "Delivery fee", "Discount", "Total"};
        for (int i = 0; i < 4; i++) { v[i] = lbl("", i == 3 ? 17 : 13, i == 3, i == 3 ? PINK : INK); p.add(lbl(names[i], i == 3 ? 15 : 13, i == 3, i == 3 ? PINK : MUT), gc(0, y, 1)); p.add(v[i], gc(1, y++, 1)); }
        JTextField promo = new JTextField(); JButton ap = btn("Apply", false); p.add(promo, gc(0, y, 1)); p.add(ap, gc(1, y++, 1));
        String[] pms = new String[]{"GCash", "Maya", "Credit/debit card", "Cash"}; ButtonGroup bg = new ButtonGroup(); JRadioButton[] rb = new JRadioButton[pms.length];
        p.add(lbl("Payment method  (" + (S.st.pay.equals("full") ? "full payment" : S.st.pay.equals("deposit") ? "50% deposit" : "pay later allowed") + ")", 13, true, INK), gc(0, y++, 2));
        for (int i = 0; i < pms.length; i++) { rb[i] = new JRadioButton(pms[i], i == (S.st.pay.equals("later") ? pms.length - 1 : 0)); rb[i].setOpaque(false); bg.add(rb[i]); p.add(rb[i], gc(0, y++, 2)); }
        JTextField card = new JTextField(); card.setToolTipText("Card number"); p.add(card, gc(0, y++, 2)); JLabel dueL = lbl("", 16, true, INK); p.add(dueL, gc(0, y++, 2));
        JButton pay = btn("", true); p.add(pay, gc(0, y++, 2)); p.add(lbl("Demo checkout: no real money moves. Connect PayMongo for live payments.", 11, false, MUT), gc(0, y, 2));
        Runnable ref = () -> { double[] c = calc(d.p, d.size, d.qty, d.method.equals("Delivery"), d.promo); v[0].setText(money(c[0])); v[1].setText(money(c[1])); v[2].setText("-" + money(c[2])); v[3].setText(money(c[3]));
            String pm = sel(rb); double du = due(c[3], pm); card.setEnabled(pm.startsWith("Credit")); dueL.setText("Due now: " + money(du) + (pm.equals("Cash") ? "  (pay cash on pickup/delivery)" : "")); pay.setText(du > 0 ? "Pay " + money(du) : "Place order"); };
        ap.addActionListener(e -> { d.promo = promo.getText().trim(); if (!d.promo.equalsIgnoreCase("VANILLA10")) info("That promo code isn't valid."); ref.run(); });
        for (JRadioButton r : rb) r.addActionListener(e -> ref.run()); ref.run();
        pay.addActionListener(e -> {
            String pm = sel(rb); if (pm.startsWith("Credit") && card.getText().replaceAll("\\D", "").length() < 13) { info("Enter a valid card number."); return; }
            Avail a = avail(d.date, null, false); boolean free = false; for (int[] s : a.slots) if (s[0] == d.hour && s[1] == 1) free = true;
            if (!free) { dlg.dispose(); info("That slot was just taken. Please pick another time."); return; }
            pay.setEnabled(false); pay.setText("Processing...");
            javax.swing.Timer t = new javax.swing.Timer(1100, ev -> { Order o = place(d, pm); dlg.dispose(); info((o.paid() > 0 ? "Payment successful!" : "Order placed!") + "\nOrder number: " + o.no + "\nPaid " + money(o.paid()) + " of " + money(o.total)); if (o.pays.isEmpty()) showReceipt(o); else showPayment(o, o.pays.size() - 1); show(VanillaTreats::dash); });
            t.setRepeats(false); t.start(); });
        dlg.setContentPane(p); dlg.pack(); dlg.setMinimumSize(new Dimension(420, 0)); dlg.setLocationRelativeTo(frame); dlg.setVisible(true);
    }
    static String sel(JRadioButton[] rb) { for (JRadioButton r : rb) if (r.isSelected()) return r.getText(); return ""; }
    static Order place(Draft d, String pm) {
        Order o = build(d.p, d.fl, d.size, d.qty, d.method.equals("Delivery"), d.promo, user, email);
        o.no = String.format("VT-%d-%05d", LocalDate.now().getYear(), ++S.seq); o.msg = d.msg; o.notes = d.notes; o.img = d.img; o.addr = d.addr; o.date = d.date; o.hour = d.hour; o.pm = pm;
        double du = due(o.total, pm); if (du > 0) { Pay y = new Pay(); y.when = LocalDateTime.now().toString(); y.method = pm; y.type = "payment"; y.amt = du; o.pays.add(y); o.status = "Payment Confirmed"; }
        S.orders.add(o); save(); return o;
    }

    // ---------- reports ----------
    static LocalDate parseD(String x, LocalDate def) { try { return LocalDate.parse(x.trim()); } catch (Exception e) { return def; } }
    static boolean inCat(Order o, String cat) {
        if (cat == null || cat.equals(CATS[0])) return true;
        for (Product p : S.products) if (p.name.equals(o.cake)) return Arrays.asList(p.cats).contains(cat); return false;
    }
    static String catOf(Order o) { for (Product p : S.products) if (p.name.equals(o.cake)) return p.cats.length > 0 ? p.cats[0] : "Other"; return "Other"; }
    /** {valid orders, cancelled, items sold, gross sales, collected, refunded, outstanding}. Cancelled and refunded orders are not counted as sales. */
    static double[] totals(List<Order> l) {
        double[] r = new double[7];
        for (Order o : l) {
            for (Pay y : o.pays) { if (y.type.equals("refund")) { r[5] += y.amt; r[4] -= y.amt; } else r[4] += y.amt; }
            if (o.status.equals("Cancelled")) { r[1]++; continue; } if (o.refunded) continue;
            r[0]++; r[2] += o.qty; r[3] += o.total; r[6] += Math.max(0, o.total - o.paid());
        }
        return r;
    }
    static String q(Object o) { return "\"" + String.valueOf(o).replace("\"", "\"\"") + "\""; }
    static void exportCsv(String name, JTable... ts) {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File(name)); if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), "UTF-8")) {
            w.write('\uFEFF');
            for (JTable t : ts) { TableModel m = t.getModel();
                for (int c = 0; c < m.getColumnCount(); c++) w.write(q(m.getColumnName(c)) + (c < m.getColumnCount() - 1 ? "," : "\n"));
                for (int r = 0; r < m.getRowCount(); r++) for (int c = 0; c < m.getColumnCount(); c++) w.write(q(m.getValueAt(r, c)) + (c < m.getColumnCount() - 1 ? "," : "\n"));
                w.write("\n"); }
            info("Report saved to " + fc.getSelectedFile().getName());
        } catch (IOException ex) { info("Could not save: " + ex.getMessage()); }
    }
    static JComponent reportTab(boolean sales) {
        JPanel p = plain(new BorderLayout(0, 8)); p.setBorder(pad(14));
        JTextField from = new JTextField(LocalDate.now().minusDays(30).toString(), 9), to = new JTextField(LocalDate.now().plusDays(30).toString(), 9);
        JComboBox<String> cat = new JComboBox<>(CATS), st = new JComboBox<>(); st.addItem("All statuses"); for (String x : PIPE) st.addItem(x); st.addItem("Cancelled");
        JButton ap = btn("Apply", true), ex = btn("Export CSV", false), pr = btn("Print", false);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); top.setOpaque(false);
        top.add(lbl("From (yyyy-mm-dd)", 13, true, INK)); top.add(from); top.add(lbl("To", 13, true, INK)); top.add(to); top.add(lbl("Category", 13, true, INK)); top.add(cat);
        if (!sales) { top.add(lbl("Status", 13, true, INK)); top.add(st); } top.add(ap); top.add(ex); top.add(pr);
        JLabel note = lbl(sales ? "Sales are counted by pickup/delivery date. Cancelled and refunded orders are not counted as sales." : "Orders are listed by pickup/delivery date.", 12, false, MUT);
        JPanel head = plain(new BorderLayout(0, 4)); head.add(top, BorderLayout.NORTH); head.add(note, BorderLayout.SOUTH);
        String[] names = sales ? new String[]{"Orders", "Cancelled", "Items sold", "Gross sales", "Collected", "Refunded", "Still to collect"} : new String[]{"Orders", "Cancelled", "Items", "Order value", "Collected", "Refunded", "Still to collect"};
        JPanel cards = new JPanel(new GridLayout(1, 0, 8, 8)); cards.setOpaque(false); JLabel[] vals = new JLabel[7];
        for (int i = 0; i < 7; i++) { JPanel c = card(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS)); vals[i] = lbl("", 16, true, PINK); c.add(lbl(names[i], 11, false, MUT)); c.add(vals[i]); cards.add(c); }
        DefaultTableModel m1 = new DefaultTableModel(sales ? new String[]{"Cake", "Orders", "Items sold", "Sales"} : new String[]{"Order #", "Customer", "Cake", "Category", "Date", "Time", "Type", "Qty", "Total", "Paid", "Status"}, 0) { public boolean isCellEditable(int r, int c) { return false; } };
        DefaultTableModel m2 = new DefaultTableModel(new String[]{"Payment method", "Net collected"}, 0) { public boolean isCellEditable(int r, int c) { return false; } };
        JTable t1 = new JTable(m1), t2 = new JTable(m2); t1.setRowHeight(26); t2.setRowHeight(26);
        JLabel byStatus = lbl("", 13, false, INK);
        Runnable fill = () -> {
            LocalDate f = parseD(from.getText(), LocalDate.now().minusDays(30)), t = parseD(to.getText(), LocalDate.now().plusDays(30)); List<Order> l = new ArrayList<>();
            for (Order o : S.orders) if (!o.date.isBefore(f) && !o.date.isAfter(t) && inCat(o, (String) cat.getSelectedItem()) && (sales || st.getSelectedIndex() == 0 || o.status.equals(st.getSelectedItem()))) l.add(o);
            l.sort(Comparator.comparing((Order o) -> o.date).thenComparingInt(o -> o.hour));
            double[] r = totals(l); for (int i = 0; i < 7; i++) vals[i].setText(i == 0 || i == 1 || i == 2 ? "" + (int) r[i] : money(r[i]));
            m1.setRowCount(0); m2.setRowCount(0);
            if (sales) {
                Map<String, double[]> byCake = new TreeMap<>(); Map<String, Double> byPay = new TreeMap<>();
                for (Order o : l) { for (Pay y : o.pays) byPay.merge(y.method, y.type.equals("refund") ? -y.amt : y.amt, Double::sum);
                    if (o.status.equals("Cancelled") || o.refunded) continue; double[] a = byCake.computeIfAbsent(o.cake, k -> new double[3]); a[0]++; a[1] += o.qty; a[2] += o.total; }
                for (Map.Entry<String, double[]> e : byCake.entrySet()) m1.addRow(new Object[]{e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1], money(e.getValue()[2])});
                m1.addRow(new Object[]{"TOTAL", (int) r[0], (int) r[2], money(r[3])});
                for (Map.Entry<String, Double> e : byPay.entrySet()) m2.addRow(new Object[]{e.getKey(), money(e.getValue())}); m2.addRow(new Object[]{"TOTAL", money(r[4])});
            } else {
                Map<String, Integer> sc = new LinkedHashMap<>(); for (String x : PIPE) sc.put(x, 0); sc.put("Cancelled", 0);
                for (Order o : l) { sc.merge(o.status, 1, Integer::sum); m1.addRow(new Object[]{o.no, o.name, o.cake, catOf(o), o.date.toString(), fh(o.hour), o.method, o.qty, money(o.total), money(o.paid()), o.status}); }
                StringBuilder b = new StringBuilder("By status:  "); for (Map.Entry<String, Integer> e : sc.entrySet()) b.append(e.getKey()).append(": ").append(e.getValue()).append("     "); byStatus.setText(b.toString());
            }
        };
        ap.addActionListener(e -> fill.run()); cat.addActionListener(e -> fill.run()); st.addActionListener(e -> fill.run());
        ex.addActionListener(e -> { if (sales) exportCsv("sales-report.csv", t1, t2); else exportCsv("order-report.csv", t1); });
        pr.addActionListener(e -> { try { t1.print(JTable.PrintMode.FIT_WIDTH, new java.text.MessageFormat(sales ? "Vanilla Treats - Sales report" : "Vanilla Treats - Order report"), new java.text.MessageFormat("Page {0}")); } catch (java.awt.print.PrinterException x) { info("Could not print: " + x.getMessage()); } });
        fill.run();
        JPanel north = plain(new BorderLayout(0, 8)); north.add(head, BorderLayout.NORTH); north.add(cards, BorderLayout.CENTER); p.add(north, BorderLayout.NORTH);
        if (sales) { JPanel two = plain(new GridLayout(1, 2, 12, 0)); two.add(new JScrollPane(t1)); two.add(new JScrollPane(t2)); p.add(two); }
        else { p.add(new JScrollPane(t1)); p.add(byStatus, BorderLayout.SOUTH); }
        return p;
    }

    // ---------- receipt ----------
    static String php(double d) { return "PHP " + NF.format(d); }
    static void line(StringBuilder b, String l, double v) { b.append(String.format("%-18s %22s\n", l, (v < 0 ? "-" : "") + php(Math.abs(v)))); }
    static String receipt(Order o) {
        String L = "----------------------------------------\n"; StringBuilder b = new StringBuilder();
        b.append("            VANILLA TREATS\n     Delicious cakes, made with love\n").append(L);
        b.append("Receipt:  ").append(o.no).append("\nIssued:   ").append(LocalDate.now().format(DF)).append("\nCustomer: ").append(o.name).append("\nEmail:    ").append(o.email).append("\n").append(L);
        b.append(o.qty).append(" x ").append(o.cake).append(" (").append(o.flavor).append(", ").append(o.size).append(")\n");
        if (!o.msg.isEmpty()) b.append("Message:  ").append(o.msg).append("\n");
        b.append(o.method).append(": ").append(o.date.format(DF)).append(", ").append(fh(o.hour)).append("\n");
        if (!o.addr.isEmpty()) b.append("Address:  ").append(o.addr).append("\n");
        b.append(L); line(b, "Subtotal", o.sub); line(b, "Delivery fee", o.fee); line(b, "Discount", -o.disc); line(b, "TOTAL", o.total); b.append(L);
        for (Pay y : o.pays) line(b, (y.type.equals("refund") ? "Refund - " : "Paid - ") + y.method, y.type.equals("refund") ? -y.amt : y.amt);
        line(b, "Balance due", o.refunded ? 0 : Math.max(0, o.total - o.paid())); b.append(L);
        if (o.paid() <= 0 && "Cash".equals(o.pm)) b.append("Payment: Cash on pickup/delivery\n");
        b.append("Status:   ").append(o.status).append("  (").append(o.payLabel()).append(")\n\n         Thank you for your order!\n"); return b.toString();
    }
    static void showReceipt(Order o) {
        boolean paidAll = o.paid() >= o.total - .5 && !o.refunded;
        receiptDialog("Receipt " + o.no, receipt(o), o.refunded ? "REFUNDED" : paidAll ? "PAID" : o.paid() > 0 ? "DEPOSIT PAID" : "UNPAID", o.no + "-receipt.txt");
    }
    // ---------- payment receipts ----------
    static String fmtWhen(String w) { try { return LocalDateTime.parse(w).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")); } catch (Exception e) { return "-"; } }
    static String payNo(Order o, int i) { return "PR-" + o.no + "-" + (i + 1); }
    static String paymentReceipt(Order o, int i) {
        Pay y = o.pays.get(i); boolean refund = y.type.equals("refund"); String L = "----------------------------------------\n"; double cum = 0;
        for (int k = 0; k <= i; k++) cum += o.pays.get(k).type.equals("refund") ? -o.pays.get(k).amt : o.pays.get(k).amt;
        StringBuilder b = new StringBuilder("            VANILLA TREATS\n          " + (refund ? "REFUND RECEIPT" : "PAYMENT RECEIPT") + "\n").append(L);
        b.append("Receipt no:   ").append(payNo(o, i)).append("\nDate:         ").append(fmtWhen(y.when)).append("\n").append("Customer:     ").append(o.name).append("\nOrder no:     ").append(o.no).append("\n").append(L);
        b.append(String.format("%-18s %22s\n", "Payment method", y.method)); line(b, refund ? "Amount refunded" : "Amount paid", y.amt); b.append(L);
        line(b, "Order total", o.total); line(b, "Paid to date", cum); line(b, "Balance remaining", refund ? 0 : Math.max(0, o.total - cum)); b.append(L);
        b.append(refund ? "This refund has been returned to you.\n" : cum >= o.total - .5 ? "Paid in full. Thank you!\n" : "Deposit received. Balance is due on pickup/delivery.\n"); return b.toString();
    }
    static void showPayment(Order o, int i) { boolean r = o.pays.get(i).type.equals("refund"); receiptDialog((r ? "Refund receipt " : "Payment receipt ") + payNo(o, i), paymentReceipt(o, i), r ? "REFUNDED" : "PAID", payNo(o, i) + ".txt"); }
    /** Opens the payment receipt for an order; if there are several payments, asks which one. */
    static void payMenu(Order o) {
        if (o.pays.isEmpty()) { info("No payment has been recorded for " + o.no + " yet." + ("Cash".equals(o.pm) ? "\nThis order is paid in cash on pickup/delivery." : "")); return; }
        if (o.pays.size() == 1) { showPayment(o, 0); return; }
        String[] opts = new String[o.pays.size()]; for (int i = 0; i < opts.length; i++) { Pay y = o.pays.get(i); opts[i] = payNo(o, i) + "  -  " + (y.type.equals("refund") ? "Refund " : "") + y.method + "  " + php(y.amt); }
        Object c = JOptionPane.showInputDialog(frame, "Which payment?", "Payment receipts for " + o.no, JOptionPane.PLAIN_MESSAGE, null, opts, opts[opts.length - 1]);
        if (c != null) showPayment(o, Arrays.asList(opts).indexOf(c));
    }
    static void receiptDialog(String title, String body, String badgeText, String fileName) {
        JDialog d = new JDialog(frame, title, true); JTextArea t = new JTextArea(body); t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); t.setEditable(false); t.setMargin(new Insets(10, 16, 10, 16));
        JPanel banner = new JPanel(new BorderLayout()); banner.setBackground(PINK); banner.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        banner.add(lbl("Vanilla Treats", 20, true, Color.WHITE), BorderLayout.WEST);
        JLabel badge = lbl(badgeText, 13, true, Color.WHITE); badge.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 2, true), BorderFactory.createEmptyBorder(2, 10, 2, 10))); banner.add(badge, BorderLayout.EAST);
        JButton pr = btn("Print", true), sv = btn("Save as text", false), cl = btn("Close", false);
        pr.addActionListener(e -> { try { t.print(); } catch (java.awt.print.PrinterException ex) { info("Could not print: " + ex.getMessage()); } });
        sv.addActionListener(e -> { JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File(fileName));
            if (fc.showSaveDialog(d) == JFileChooser.APPROVE_OPTION) { try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), "UTF-8")) { w.write(t.getText()); info("Receipt saved."); } catch (IOException ex) { info("Could not save: " + ex.getMessage()); } } });
        cl.addActionListener(e -> d.dispose()); JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bs.setBackground(BG); bs.add(pr); bs.add(sv); bs.add(cl);
        d.add(banner, BorderLayout.NORTH); d.add(new JScrollPane(t)); d.add(bs, BorderLayout.SOUTH); d.setSize(480, 600); d.setLocationRelativeTo(frame); d.setVisible(true);
    }

    // ---------- admin ----------
    static JComponent adminScreen() {
        JTabbedPane t = new JTabbedPane(); t.addTab("Orders", ordersTab()); t.addTab("Sales report", reportTab(true)); t.addTab("Order report", reportTab(false)); t.addTab("Calendar", calTab()); t.addTab("Menu", menuTab()); t.addTab("Settings", settingsTab()); t.addTab("Payments", paymentsTab()); return t;
    }
    static JButton ab(String t, Supplier<Order> sel, Runnable fill, Consumer<Order> a) {
        JButton b = btn(t, false); b.addActionListener(e -> { Order o = sel.get(); if (o == null) { info("Select an order first."); return; } a.accept(o); save(); fill.run(); }); return b;
    }
    static JComponent ordersTab() {
        JPanel p = plain(new BorderLayout(0, 8)); p.setBorder(pad(14)); JTextField q = new JTextField(16); JComboBox<String> cs = new JComboBox<>(CATS); JComboBox<String> fs = new JComboBox<>(); fs.addItem("All statuses"); for (String s : PIPE) fs.addItem(s); fs.addItem("Cancelled");
        String[] cols = {"Order #", "Customer", "Cake", "Date", "Time", "Type", "Total", "Payment", "Status"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; } }; JTable t = new JTable(m); t.setRowHeight(26); t.setSelectionBackground(SOFT); t.setSelectionForeground(INK); t.setAutoCreateRowSorter(false);
        List<Order> shown = new ArrayList<>();
        Runnable fill = () -> {
            m.setRowCount(0); shown.clear(); List<Order> all = new ArrayList<>(S.orders); all.sort(Comparator.comparing((Order o) -> o.date));
            for (Order o : all) { String hay = (o.no + o.name + o.email + o.cake).toLowerCase(); if (!hay.contains(q.getText().trim().toLowerCase()) || !inCat(o, (String) cs.getSelectedItem()) || (fs.getSelectedIndex() > 0 && !o.status.equals(fs.getSelectedItem()))) continue;
                shown.add(o); m.addRow(new Object[]{o.no, o.name, o.cake, o.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), fh(o.hour), o.method, money(o.total), o.payLabel(), o.status}); } };
        q.getDocument().addDocumentListener(dl(fill)); fs.addActionListener(e -> fill.run()); cs.addActionListener(e -> fill.run()); fill.run();
        Supplier<Order> sel = () -> t.getSelectedRow() < 0 ? null : shown.get(t.getSelectedRow());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); top.setOpaque(false); top.add(lbl("Search", 13, true, INK)); top.add(q); top.add(lbl("Category", 13, true, INK)); top.add(cs); top.add(lbl("Status", 13, true, INK)); top.add(fs);
        JPanel bs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); bs.setOpaque(false);
        bs.add(ab("Confirm", sel, fill, o -> { if (o.status.equals("Pending")) o.status = "Payment Confirmed"; }));
        bs.add(ab("Set status", sel, fill, o -> { Object s = JOptionPane.showInputDialog(frame, "New status for " + o.no, "Change status", JOptionPane.PLAIN_MESSAGE, null, concat(PIPE, "Cancelled"), o.status); if (s != null) o.status = (String) s; }));
        bs.add(ab("Cancel order", sel, fill, o -> { if (JOptionPane.showConfirmDialog(frame, "Cancel " + o.no + "?", "Cancel order", JOptionPane.YES_NO_OPTION) == 0) o.status = "Cancelled"; }));
        bs.add(ab("Reschedule", sel, fill, o -> reschedule(o, true)));
        bs.add(ab("Approve payment", sel, fill, o -> { if (o.paid() < o.total - .5) { Pay y = new Pay(); y.when = LocalDateTime.now().toString(); y.method = o.pm.isEmpty() ? "Cash" : o.pm; y.type = "payment"; y.amt = o.total - o.paid(); o.pays.add(y); if (o.status.equals("Pending")) o.status = "Payment Confirmed"; showPayment(o, o.pays.size() - 1); } }));
        bs.add(ab("Refund", sel, fill, o -> { if (o.paid() > 0 && !o.refunded && JOptionPane.showConfirmDialog(frame, "Refund " + money(o.paid()) + "?", "Refund", JOptionPane.YES_NO_OPTION) == 0) { Pay y = new Pay(); y.when = LocalDateTime.now().toString(); y.method = o.pm; y.type = "refund"; y.amt = o.paid(); o.pays.add(y); o.refunded = true; } }));
        bs.add(ab("Order receipt", sel, fill, o -> showReceipt(o))); bs.add(ab("Payment receipt", sel, fill, o -> payMenu(o)));
        JPanel n = plain(new BorderLayout()); n.add(top, BorderLayout.NORTH); n.add(bs, BorderLayout.SOUTH); p.add(n, BorderLayout.NORTH); p.add(new JScrollPane(t)); return p;
    }
    static String[] concat(String[] a, String b) { String[] r = Arrays.copyOf(a, a.length + 1); r[a.length] = b; return r; }
    static JComponent calTab() {
        JPanel p = plain(new BorderLayout()); p.setBorder(pad(14)); CalendarPanel c = new CalendarPanel(null, true, true, null);
        c.onDay = d -> { StringBuilder sb = new StringBuilder(d.format(DF) + "\n\n"); int n = 0;
            for (Order o : S.orders) if (o.date.equals(d) && !o.status.equals("Cancelled")) { sb.append(fh(o.hour)).append("  ").append(o.no).append("  ").append(o.name).append("  ").append(o.cake).append("\n"); n++; }
            if (n == 0) sb.append("No orders on this day.\n"); boolean bl = S.st.blocked.contains(d);
            if (JOptionPane.showConfirmDialog(frame, sb + "\n" + (bl ? "Unblock this date?" : "Block this date?"), "Day details", JOptionPane.YES_NO_OPTION) == 0) { if (bl) S.st.blocked.remove(d); else S.st.blocked.add(d); save(); c.build(); } };
        p.add(lbl("Click a day to see its orders or block/unblock it (blocked days are shaded).", 13, false, MUT), BorderLayout.NORTH); p.add(c); return p;
    }
    static JComponent menuTab() {
        JPanel p = plain(new BorderLayout(0, 8)); p.setBorder(pad(14));
        DefaultTableModel m = new DefaultTableModel(new String[]{"Cake", "Price (6\" size)", "Available"}, 0) { public Class<?> getColumnClass(int c) { return c == 2 ? Boolean.class : c == 1 ? Double.class : String.class; } };
        for (Product pr : S.products) m.addRow(new Object[]{pr.name, pr.price, pr.on});
        m.addTableModelListener(e -> { if (e.getType() != TableModelEvent.UPDATE) return; int r = e.getFirstRow(); if (r < 0 || r >= S.products.size()) return; Product pr = S.products.get(r); pr.name = "" + m.getValueAt(r, 0); pr.price = ((Number) m.getValueAt(r, 1)).doubleValue(); pr.on = (Boolean) m.getValueAt(r, 2); save(); });
        JTable t = new JTable(m); t.setRowHeight(26); JButton add = btn("Add a cake", true), del = btn("Delete selected cake", false);
        del.addActionListener(e -> { int r = t.getSelectedRow(); if (r < 0) { info("Select a cake in the table first."); return; } Product pr = S.products.get(r);
            if (JOptionPane.showConfirmDialog(frame, "Delete " + pr.name + " from the menu?\nExisting orders keep their details.", "Delete cake", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { S.products.remove(r); m.removeRow(r); save(); } });
        add.addActionListener(e -> { Product n = new Product(S.products.size() + 1, "New cake", 900, "Vanilla", new int[]{0xFBC7D5, 0xE54278, 0xFFFFFF, 0xFBDFE7}); S.products.add(n); m.addRow(new Object[]{n.name, n.price, n.on}); save(); });
        p.add(new JScrollPane(t)); JPanel s = new JPanel(new FlowLayout(FlowLayout.LEFT)); s.setOpaque(false); s.add(add); s.add(del); p.add(s, BorderLayout.SOUTH); return p;
    }
    static JComponent settingsTab() {
        Settings st = S.st; JPanel p = card(); p.setLayout(new GridBagLayout());
        JSpinner cap = new JSpinner(new SpinnerNumberModel(st.cap, 1, 99, 1)), sc = new JSpinner(new SpinnerNumberModel(st.slotCap, 1, 99, 1)), pol = new JSpinner(new SpinnerNumberModel(st.policy, 0, 720, 1)),
                lead = new JSpinner(new SpinnerNumberModel(st.lead, 0, 60, 1)), fee = new JSpinner(new SpinnerNumberModel(st.fee, 0.0, 5000.0, 10.0));
        JComboBox<String> pay = new JComboBox<>(new String[]{"Full amount", "50% deposit", "Pay later"}); pay.setSelectedIndex(st.pay.equals("full") ? 0 : st.pay.equals("deposit") ? 1 : 2);
        row(p, 0, "Daily cake-order capacity", cap); row(p, 1, "Orders per time slot", sc); row(p, 2, "Reschedule allowed up to (hours before)", pol); row(p, 3, "Minimum days notice", lead); row(p, 4, "Delivery fee", fee); row(p, 5, "Customers must pay", pay);
        String[] dn = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"}; JCheckBox[] on = new JCheckBox[7]; JSpinner[] o = new JSpinner[7], c = new JSpinner[7]; JPanel hp = new JPanel(new GridLayout(0, 3, 6, 2)); hp.setOpaque(false);
        for (int i = 0; i < 7; i++) { on[i] = new JCheckBox(dn[i], st.open[i] >= 0); on[i].setOpaque(false); o[i] = new JSpinner(new SpinnerNumberModel(Math.max(st.open[i], 9), 0, 23, 1)); c[i] = new JSpinner(new SpinnerNumberModel(Math.max(st.close[i], 17), 1, 24, 1)); hp.add(on[i]); hp.add(o[i]); hp.add(c[i]); }
        p.add(lbl("Business hours (open hour / close hour, 24h)", 13, true, INK), gc(0, 12, 1)); p.add(hp, gc(0, 13, 1));
        JButton sv = btn("Save settings", true); sv.addActionListener(e -> {
            st.cap = (Integer) cap.getValue(); st.slotCap = (Integer) sc.getValue(); st.policy = (Integer) pol.getValue(); st.lead = (Integer) lead.getValue(); st.fee = ((Number) fee.getValue()).doubleValue(); st.pay = new String[]{"full", "deposit", "later"}[pay.getSelectedIndex()];
            for (int i = 0; i < 7; i++) { int op = (Integer) o[i].getValue(), cl = (Integer) c[i].getValue(); st.open[i] = on[i].isSelected() ? op : -1; st.close[i] = Math.max(cl, op + 1); }
            save(); info("Settings saved."); });
        p.add(sv, gc(0, 14, 1)); JPanel w = plain(new BorderLayout()); w.setBorder(pad(14)); w.add(p, BorderLayout.NORTH); JScrollPane sp = new JScrollPane(w); sp.setBorder(null); return sp;
    }
    static JComponent paymentsTab() {
        DefaultTableModel m = new DefaultTableModel(new String[]{"Receipt no.", "Order #", "Customer", "Method", "Type", "Amount", "When"}, 0) { public boolean isCellEditable(int r, int c) { return false; } };
        List<Object[]> refs = new ArrayList<>();
        for (Order o : S.orders) for (int i = 0; i < o.pays.size(); i++) { Pay y = o.pays.get(i); refs.add(new Object[]{o, i}); m.addRow(new Object[]{payNo(o, i), o.no, o.name, y.method, y.type.equals("refund") ? "Refunded" : "Approved", money(y.amt), fmtWhen(y.when)}); }
        JTable t = new JTable(m); t.setRowHeight(26); t.setSelectionBackground(SOFT); t.setSelectionForeground(INK);
        JButton vr = btn("View payment receipt", true); vr.addActionListener(e -> { int r = t.getSelectedRow(); if (r < 0) { info("Select a payment first."); return; } showPayment((Order) refs.get(r)[0], (Integer) refs.get(r)[1]); });
        JPanel bs = new JPanel(new FlowLayout(FlowLayout.LEFT)); bs.setOpaque(false); bs.add(vr);
        JPanel p = plain(new BorderLayout(0, 8)); p.setBorder(pad(14)); p.add(new JScrollPane(t)); p.add(bs, BorderLayout.SOUTH); return p;
    }
}