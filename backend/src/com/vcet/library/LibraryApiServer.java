package com.vcet.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * VCET ILMS - Scenario 4: Library Book Lending and Usage Analysis System
 * Register No: 34 | Java REST API Backend (Port 8080)
 */
public class LibraryApiServer {

    private static final int PORT = 8080;
    private static double FINE_PER_DAY = 5.0;
    private static String DB_URL = "jdbc:mysql://localhost:3306/vcet_ilms_reg34?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String DB_USER = "root";
    private static String DB_PASS = "";
    private static boolean mysqlConnected = false;

    private static final String CSV_DIR = "../database/csv_storage";

    static class Book {
        int id;
        String isbn, title, author, category, bookType, addedDate;
        int totalCopies, availableCopies, borrowCount;

        Book(int id, String isbn, String title, String author, String category,
             int totalCopies, int availableCopies, String bookType, int borrowCount, String addedDate) {
            this.id = id;
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.category = category;
            this.totalCopies = totalCopies;
            this.availableCopies = availableCopies;
            this.bookType = bookType;
            this.borrowCount = borrowCount;
            this.addedDate = addedDate;
        }
    }

    static class Member {
        int id;
        String regNo, name, department, email, joinedDate;

        Member(int id, String regNo, String name, String department, String email, String joinedDate) {
            this.id = id;
            this.regNo = regNo;
            this.name = name;
            this.department = department;
            this.email = email;
            this.joinedDate = joinedDate;
        }
    }

    static class Transaction {
        int id, bookId, memberId;
        String issueDate, dueDate, returnDate, status;
        double fine;

        Transaction(int id, int bookId, int memberId, String issueDate, String dueDate,
                    String returnDate, double fine, String status) {
            this.id = id;
            this.bookId = bookId;
            this.memberId = memberId;
            this.issueDate = issueDate;
            this.dueDate = dueDate;
            this.returnDate = returnDate;
            this.fine = fine;
            this.status = status;
        }
    }

    private static final List<Book> books = Collections.synchronizedList(new ArrayList<>());
    private static final List<Member> members = Collections.synchronizedList(new ArrayList<>());
    private static final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger bookIdSeq = new AtomicInteger(100);
    private static final AtomicInteger memberIdSeq = new AtomicInteger(10);
    private static final AtomicInteger txnIdSeq = new AtomicInteger(1000);

    public static void main(String[] args) throws IOException {
        loadDbProperties();
        initStorage();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/health", ex -> {
            if (handleCors(ex)) return;
            sendJson(ex, 200, "{\"status\":\"OK\",\"storageMode\":\"" + (mysqlConnected ? "MySQL Database" : "CSV Synced Storage") + "\",\"finePerDay\":" + FINE_PER_DAY + "}");
        });

        server.createContext("/api/dashboard", ex -> {
            if (handleCors(ex)) return;
            sendJson(ex, 200, buildDashboardJson());
        });

        server.createContext("/api/books", ex -> {
            if (handleCors(ex)) return;
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                sendJson(ex, 200, booksToJson());
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> data = parseJson(readBody(ex));
                String isbn = data.getOrDefault("isbn", "").trim();
                if (isbn.isEmpty()) isbn = "978-93-" + (1000 + new Random().nextInt(8999));
                String title = data.getOrDefault("title", "").trim();
                String author = data.getOrDefault("author", "").trim();
                String category = data.getOrDefault("category", "Software & Java").trim();
                String bookType = data.getOrDefault("bookType", "Physical").trim();
                int copies = 1;
                try { copies = Math.max(1, Integer.parseInt(data.getOrDefault("totalCopies", "3"))); } catch (Exception ignored) {}

                if (title.isEmpty() || author.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Title and Author are required\"}");
                    return;
                }

                Book created = addBook(isbn, title, author, category, copies, bookType);
                sendJson(ex, 201, "{\"message\":\"Book saved to database\",\"book\":" + bookToJson(created) + "}");
            } else if ("DELETE".equalsIgnoreCase(method)) {
                int id = extractIdFromQuery(ex.getRequestURI().getQuery());
                if (deleteBookById(id)) {
                    sendJson(ex, 200, "{\"message\":\"Book removed\"}");
                } else {
                    sendJson(ex, 404, "{\"error\":\"Book not found\"}");
                }
            } else {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/members", ex -> {
            if (handleCors(ex)) return;
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                sendJson(ex, 200, membersToJson());
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> data = parseJson(readBody(ex));
                String regNo = data.getOrDefault("regNo", "").trim();
                String name = data.getOrDefault("name", "").trim();
                String dept = data.getOrDefault("department", "IT").trim();
                String email = data.getOrDefault("email", "").trim();
                if (regNo.isEmpty() || name.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Register No and Full Name are required\"}");
                    return;
                }
                if (email.isEmpty()) email = regNo.toLowerCase() + "@vcet.ac.in";
                Member m = addMember(regNo, name, dept, email);
                sendJson(ex, 201, "{\"message\":\"Member saved\",\"member\":" + memberToJson(m) + "}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/transactions", ex -> {
            if (handleCors(ex)) return;
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 200, transactionsToJson());
            } else {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/issue", ex -> {
            if (handleCors(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> data = parseJson(readBody(ex));
            int bookId = Integer.parseInt(data.getOrDefault("bookId", "0"));
            int memberId = Integer.parseInt(data.getOrDefault("memberId", "0"));
            String issueDate = data.getOrDefault("issueDate", LocalDate.now().toString()).trim();
            String dueDate = data.getOrDefault("dueDate", "").trim();

            if (dueDate.isEmpty()) {
                dueDate = LocalDate.parse(issueDate).plusDays(14).toString();
            }

            Book target = getAllBooks().stream().filter(b -> b.id == bookId).findFirst().orElse(null);
            Member mem = getAllMembers().stream().filter(m -> m.id == memberId).findFirst().orElse(null);

            if (target == null || mem == null) {
                sendJson(ex, 404, "{\"error\":\"Invalid Book or Member\"}");
                return;
            }
            if (target.availableCopies <= 0) {
                sendJson(ex, 409, "{\"error\":\"Cannot issue: 0 available copies in catalog!\"}");
                return;
            }

            Transaction txn = issueBookTxn(bookId, memberId, issueDate, dueDate);
            sendJson(ex, 201, "{\"message\":\"Book issued successfully\",\"txnId\":" + txn.id + "}");
        });

        server.createContext("/api/return", ex -> {
            if (handleCors(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> data = parseJson(readBody(ex));
            int txnId = Integer.parseInt(data.getOrDefault("txnId", "0"));
            String returnDateStr = data.getOrDefault("returnDate", LocalDate.now().toString()).trim();

            Transaction txn = getAllTransactions().stream().filter(t -> t.id == txnId).findFirst().orElse(null);
            if (txn == null) {
                sendJson(ex, 404, "{\"error\":\"Transaction not found\"}");
                return;
            }
            if ("RETURNED".equalsIgnoreCase(txn.status)) {
                sendJson(ex, 400, "{\"error\":\"Book already returned\"}");
                return;
            }

            LocalDate due = LocalDate.parse(txn.dueDate);
            LocalDate ret = LocalDate.parse(returnDateStr);
            long overdueDays = ChronoUnit.DAYS.between(due, ret);
            double fine = overdueDays > 0 ? overdueDays * FINE_PER_DAY : 0.0;

            completeReturnTxn(txnId, returnDateStr, fine);
            sendJson(ex, 200, "{\"message\":\"Book status updated to RETURNED\",\"fine\":" + fine + ",\"overdueDays\":" + Math.max(0, overdueDays) + "}");
        });

        server.setExecutor(null);
        server.start();
        System.out.println("=================================================================");
        System.out.println("  VCET ILMS Backend Server Running on http://localhost:" + PORT);
        System.out.println("  Storage Engine : " + (mysqlConnected ? "MySQL Database (vcet_ilms_reg34)" : "CSV Storage (MySQL JDBC JAR optional)"));
        System.out.println("=================================================================");
    }

    private static void loadDbProperties() {
        Path p = Paths.get("../db.properties");
        if (Files.exists(p)) {
            try (InputStream is = Files.newInputStream(p)) {
                Properties props = new Properties();
                props.load(is);
                DB_URL = props.getProperty("db.url", DB_URL);
                DB_USER = props.getProperty("db.user", DB_USER);
                DB_PASS = props.getProperty("db.password", DB_PASS);
                FINE_PER_DAY = Double.parseDouble(props.getProperty("library.fine.per.day", "5.0"));
            } catch (Exception ignored) {}
        }
    }

    private static Connection getMySqlConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private static void initStorage() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = getMySqlConnection()) {
                mysqlConnected = true;
                System.out.println("[OK] Connected to MySQL Database: vcet_ilms_reg34");
                return;
            }
        } catch (Exception e) {
            mysqlConnected = false;
            System.out.println("[INFO] MySQL JDBC not active (" + e.getMessage() + "). Using local persistent CSV storage.");
        }

        try {
            Files.createDirectories(Paths.get(CSV_DIR));
            Path bPath = Paths.get(CSV_DIR, "books.csv");
            Path mPath = Paths.get(CSV_DIR, "members.csv");
            Path tPath = Paths.get(CSV_DIR, "transactions.csv");

            if (Files.exists(bPath) && Files.exists(mPath) && Files.exists(tPath)) {
                List<String> bLines = Files.readAllLines(bPath, StandardCharsets.UTF_8);
                for (int i = 1; i < bLines.size(); i++) {
                    String[] c = splitCsv(bLines.get(i));
                    if (c.length >= 10) {
                        int id = Integer.parseInt(c[0]);
                        books.add(new Book(id, c[1], c[2], c[3], c[4], Integer.parseInt(c[5]), Integer.parseInt(c[6]), c[7], Integer.parseInt(c[8]), c[9]));
                        bookIdSeq.set(Math.max(bookIdSeq.get(), id));
                    }
                }
                List<String> mLines = Files.readAllLines(mPath, StandardCharsets.UTF_8);
                for (int i = 1; i < mLines.size(); i++) {
                    String[] c = splitCsv(mLines.get(i));
                    if (c.length >= 6) {
                        int id = Integer.parseInt(c[0]);
                        members.add(new Member(id, c[1], c[2], c[3], c[4], c[5]));
                        memberIdSeq.set(Math.max(memberIdSeq.get(), id));
                    }
                }
                List<String> tLines = Files.readAllLines(tPath, StandardCharsets.UTF_8);
                for (int i = 1; i < tLines.size(); i++) {
                    String[] c = splitCsv(tLines.get(i));
                    if (c.length >= 8) {
                        int id = Integer.parseInt(c[0]);
                        transactions.add(new Transaction(id, Integer.parseInt(c[1]), Integer.parseInt(c[2]), c[3], c[4], c[5], Double.parseDouble(c[6]), c[7]));
                        txnIdSeq.set(Math.max(txnIdSeq.get(), id));
                    }
                }
                return;
            }
        } catch (Exception ignored) {}

        books.add(new Book(101, "978-0078022159", "Database System Concepts", "Abraham Silberschatz", "Database Systems", 15, 13, "Physical", 29, "2026-07-01"));
        books.add(new Book(102, "978-0133943030", "Software Engineering", "Ian Sommerville", "Engineering", 18, 17, "Physical", 41, "2026-07-01"));
        books.add(new Book(103, "978-1260440232", "Java: The Complete Reference", "Herbert Schildt", "Software & Java", 14, 13, "Physical", 34, "2026-07-02"));
        books.add(new Book(104, "978-0132126953", "Computer Networks", "Andrew S. Tanenbaum", "Computer Networks", 12, 11, "Physical", 22, "2026-07-03"));
        books.add(new Book(105, "978-0134610993", "Artificial Intelligence: A Modern Approach", "Stuart Russell", "Science & AI", 10, 10, "Physical", 19, "2026-07-05"));
        books.add(new Book(106, "978-0124077263", "Digital Design & Computer Architecture", "David Harris", "Engineering", 9, 8, "Physical", 15, "2026-07-06"));
        books.add(new Book(107, "978-1491957660", "Python for Data Analysis", "Wes McKinney", "Science & AI", 10, 10, "E-Book", 27, "2026-07-08"));
        books.add(new Book(108, "978-0132350884", "Clean Code", "Robert C. Martin", "Software & Java", 10, 10, "E-Book", 24, "2026-07-10"));

        members.add(new Member(1, "34", "Harshana M", "IT", "harshana34@vcet.ac.in", "2026-07-01"));
        members.add(new Member(2, "04", "Neha Patel", "CSE", "neha04@vcet.ac.in", "2026-07-03"));
        members.add(new Member(3, "64", "Arun Kumar", "ECE", "arun64@vcet.ac.in", "2026-07-05"));
        members.add(new Member(4, "94", "Priya Singh", "IT", "priya94@vcet.ac.in", "2026-07-08"));
        members.add(new Member(5, "21", "Karthik Raja", "CSE", "karthik21@vcet.ac.in", "2026-07-12"));

        transactions.add(new Transaction(1001, 103, 4, "2026-11-19", "2026-12-03", "", 0.0, "ISSUED"));
        transactions.add(new Transaction(1002, 102, 1, "2026-11-18", "2026-12-02", "", 0.0, "ISSUED"));
        transactions.add(new Transaction(1003, 101, 1, "2026-11-17", "2026-12-01", "2026-11-19", 0.0, "RETURNED"));
        transactions.add(new Transaction(1004, 104, 2, "2026-11-16", "2026-11-30", "2026-11-18", 0.0, "RETURNED"));
        transactions.add(new Transaction(1005, 106, 4, "2026-10-01", "2026-10-15", "", 180.0, "ISSUED"));
        transactions.add(new Transaction(1006, 104, 3, "2026-09-25", "2026-10-09", "", 210.0, "ISSUED"));
        transactions.add(new Transaction(1007, 101, 2, "2026-09-28", "2026-10-12", "", 195.0, "ISSUED"));
        saveCsvFallback();
    }

    private static List<Book> getAllBooks() {
        if (mysqlConnected) {
            List<Book> list = new ArrayList<>();
            String sql = "SELECT b.book_id, b.isbn, b.title, a.author_name, c.category_name, " +
                         "b.total_copies, b.available_copies, b.book_type, " +
                         "COALESCE(tx.cnt, b.borrow_count) AS actual_borrow_count, b.added_date " +
                         "FROM books b " +
                         "JOIN authors a ON b.author_id = a.author_id " +
                         "JOIN categories c ON b.category_id = c.category_id " +
                         "LEFT JOIN (SELECT book_id, COUNT(*) AS cnt FROM transactions GROUP BY book_id) tx ON b.book_id = tx.book_id " +
                         "ORDER BY b.book_id DESC";
            try (Connection conn = getMySqlConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(new Book(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getInt(9), rs.getString(10)));
                }
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(books);
    }

    private static Book addBook(String isbn, String title, String author, String category, int copies, String bookType) {
        String today = LocalDate.now().toString();
        if (mysqlConnected) {
            try (Connection conn = getMySqlConnection()) {
                try (PreparedStatement psA = conn.prepareStatement("INSERT IGNORE INTO authors (author_name) VALUES (?)")) {
                    psA.setString(1, author);
                    psA.executeUpdate();
                }
                try (PreparedStatement psC = conn.prepareStatement("INSERT IGNORE INTO categories (category_name) VALUES (?)")) {
                    psC.setString(1, category);
                    psC.executeUpdate();
                }
                int authorId = 1, catId = 1;
                try (PreparedStatement ps = conn.prepareStatement("SELECT author_id FROM authors WHERE author_name = ?")) {
                    ps.setString(1, author);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) authorId = rs.getInt(1);
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT category_id FROM categories WHERE category_name = ?")) {
                    ps.setString(1, category);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) catId = rs.getInt(1);
                }
                String ins = "INSERT INTO books (isbn, title, author_id, category_id, total_copies, available_copies, book_type, borrow_count, added_date) VALUES (?,?,?,?,?,?,?,0,?)";
                try (PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, isbn);
                    ps.setString(2, title);
                    ps.setInt(3, authorId);
                    ps.setInt(4, catId);
                    ps.setInt(5, copies);
                    ps.setInt(6, copies);
                    ps.setString(7, bookType);
                    ps.setString(8, today);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    int newId = keys.next() ? keys.getInt(1) : 200;
                    return new Book(newId, isbn, title, author, category, copies, copies, bookType, 0, today);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        Book b = new Book(bookIdSeq.incrementAndGet(), isbn, title, author, category, copies, copies, bookType, 0, today);
        books.add(0, b);
        saveCsvFallback();
        return b;
    }

    private static boolean deleteBookById(int id) {
        if (mysqlConnected) {
            try (Connection conn = getMySqlConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { return false; }
        }
        boolean ok = books.removeIf(b -> b.id == id);
        if (ok) saveCsvFallback();
        return ok;
    }

    private static List<Member> getAllMembers() {
        if (mysqlConnected) {
            List<Member> list = new ArrayList<>();
            try (Connection conn = getMySqlConnection(); Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT member_id, reg_no, full_name, department, email, joined_date FROM members ORDER BY member_id DESC")) {
                while (rs.next()) {
                    list.add(new Member(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
                }
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(members);
    }

    private static Member addMember(String regNo, String name, String dept, String email) {
        String today = LocalDate.now().toString();
        if (mysqlConnected) {
            try (Connection conn = getMySqlConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO members (reg_no, full_name, department, email, joined_date) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, regNo);
                ps.setString(2, name);
                ps.setString(3, dept);
                ps.setString(4, email);
                ps.setString(5, today);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                int newId = keys.next() ? keys.getInt(1) : 20;
                return new Member(newId, regNo, name, dept, email, today);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        Member m = new Member(memberIdSeq.incrementAndGet(), regNo, name, dept, email, today);
        members.add(0, m);
        saveCsvFallback();
        return m;
    }

    private static List<Transaction> getAllTransactions() {
        if (mysqlConnected) {
            List<Transaction> list = new ArrayList<>();
            try (Connection conn = getMySqlConnection(); Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT txn_id, book_id, member_id, issue_date, due_date, COALESCE(return_date,''), fine_amount, status FROM transactions ORDER BY txn_id DESC")) {
                while (rs.next()) {
                    list.add(new Transaction(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getDouble(7), rs.getString(8)));
                }
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(transactions);
    }

    private static Transaction issueBookTxn(int bookId, int memberId, String issueDate, String dueDate) {
        if (mysqlConnected) {
            try (Connection conn = getMySqlConnection()) {
                try (PreparedStatement upd = conn.prepareStatement("UPDATE books SET available_copies = available_copies - 1, borrow_count = borrow_count + 1 WHERE book_id = ?")) {
                    upd.setInt(1, bookId);
                    upd.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO transactions (book_id, member_id, issue_date, due_date, fine_amount, status) VALUES (?,?,?,?,0.00,'ISSUED')", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setInt(1, bookId);
                    ins.setInt(2, memberId);
                    ins.setString(3, issueDate);
                    ins.setString(4, dueDate);
                    ins.executeUpdate();
                    ResultSet keys = ins.getGeneratedKeys();
                    int newId = keys.next() ? keys.getInt(1) : 2000;
                    return new Transaction(newId, bookId, memberId, issueDate, dueDate, "", 0.0, "ISSUED");
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        books.stream().filter(b -> b.id == bookId).findFirst().ifPresent(b -> {
            b.availableCopies -= 1;
            b.borrowCount += 1;
        });
        Transaction t = new Transaction(txnIdSeq.incrementAndGet(), bookId, memberId, issueDate, dueDate, "", 0.0, "ISSUED");
        transactions.add(0, t);
        saveCsvFallback();
        return t;
    }

    private static void completeReturnTxn(int txnId, String returnDate, double fine) {
        if (mysqlConnected) {
            try (Connection conn = getMySqlConnection()) {
                int bookId = 0;
                try (PreparedStatement sel = conn.prepareStatement("SELECT book_id FROM transactions WHERE txn_id = ?")) {
                    sel.setInt(1, txnId);
                    ResultSet rs = sel.executeQuery();
                    if (rs.next()) bookId = rs.getInt(1);
                }
                try (PreparedStatement upd = conn.prepareStatement("UPDATE transactions SET return_date = ?, fine_amount = ?, status = 'RETURNED' WHERE txn_id = ?")) {
                    upd.setString(1, returnDate);
                    upd.setDouble(2, fine);
                    upd.setInt(3, txnId);
                    upd.executeUpdate();
                }
                if (bookId > 0) {
                    try (PreparedStatement bUpd = conn.prepareStatement("UPDATE books SET available_copies = LEAST(total_copies, available_copies + 1) WHERE book_id = ?")) {
                        bUpd.setInt(1, bookId);
                        bUpd.executeUpdate();
                    }
                }
                return;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        transactions.stream().filter(t -> t.id == txnId).findFirst().ifPresent(t -> {
            t.returnDate = returnDate;
            t.fine = fine;
            t.status = "RETURNED";
            books.stream().filter(b -> b.id == t.bookId).findFirst().ifPresent(b -> {
                if (b.availableCopies < b.totalCopies) b.availableCopies += 1;
            });
        });
        saveCsvFallback();
    }

    private static synchronized void saveCsvFallback() {
        try {
            Files.createDirectories(Paths.get(CSV_DIR));
            List<String> bOut = new ArrayList<>();
            bOut.add("book_id,isbn,title,author,category,total_copies,available_copies,book_type,borrow_count,added_date");
            for (Book b : books) {
                bOut.add(b.id + "," + esc(b.isbn) + "," + esc(b.title) + "," + esc(b.author) + "," + esc(b.category) + "," + b.totalCopies + "," + b.availableCopies + "," + esc(b.bookType) + "," + b.borrowCount + "," + esc(b.addedDate));
            }
            Files.write(Paths.get(CSV_DIR, "books.csv"), bOut, StandardCharsets.UTF_8);

            List<String> mOut = new ArrayList<>();
            mOut.add("member_id,reg_no,name,department,email,joined_date");
            for (Member m : members) {
                mOut.add(m.id + "," + esc(m.regNo) + "," + esc(m.name) + "," + esc(m.department) + "," + esc(m.email) + "," + esc(m.joinedDate));
            }
            Files.write(Paths.get(CSV_DIR, "members.csv"), mOut, StandardCharsets.UTF_8);

            List<String> tOut = new ArrayList<>();
            tOut.add("txn_id,book_id,member_id,issue_date,due_date,return_date,fine_amount,status");
            for (Transaction t : transactions) {
                tOut.add(t.id + "," + t.bookId + "," + t.memberId + "," + esc(t.issueDate) + "," + esc(t.dueDate) + "," + esc(t.returnDate) + "," + t.fine + "," + esc(t.status));
            }
            Files.write(Paths.get(CSV_DIR, "transactions.csv"), tOut, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private static String buildDashboardJson() {
        List<Book> allB = getAllBooks();
        List<Member> allM = getAllMembers();
        List<Transaction> allT = getAllTransactions();
        LocalDate today = LocalDate.now();

        int totalCopies = allB.stream().mapToInt(b -> b.totalCopies).sum();
        int ebooks = allB.stream().filter(b -> "E-Book".equalsIgnoreCase(b.bookType)).mapToInt(b -> b.totalCopies).sum();
        long issued = allT.stream().filter(t -> "ISSUED".equalsIgnoreCase(t.status)).count();
        long returned = allT.stream().filter(t -> "RETURNED".equalsIgnoreCase(t.status)).count();
        long overdue = allT.stream().filter(t -> "ISSUED".equalsIgnoreCase(t.status) && LocalDate.parse(t.dueDate).isBefore(today)).count();

        TreeMap<String, int[]> dailyMap = new TreeMap<>();
        for (Transaction t : allT) {
            dailyMap.putIfAbsent(t.issueDate, new int[]{0, 0});
            dailyMap.get(t.issueDate)[0] += 1;
            if (t.returnDate != null && !t.returnDate.isEmpty()) {
                dailyMap.putIfAbsent(t.returnDate, new int[]{0, 0});
                dailyMap.get(t.returnDate)[1] += 1;
            }
        }
        String dailyJson = dailyMap.entrySet().stream()
                .map(e -> "{\"date\":\"" + e.getKey() + "\",\"issued\":" + e.getValue()[0] + ",\"returned\":" + e.getValue()[1] + "}")
                .collect(Collectors.joining(","));

        Map<String, Integer> catCounts = new LinkedHashMap<>();
        for (Book b : allB) {
            String catName = (b.category == null || b.category.isEmpty()) ? "General" : b.category;
            int weight = Math.max(1, b.borrowCount);
            catCounts.put(catName, catCounts.getOrDefault(catName, 0) + weight);
        }
        String catJson = catCounts.entrySet().stream()
                .map(e -> "{\"category\":\"" + jsonEsc(e.getKey()) + "\",\"count\":" + e.getValue() + "}")
                .collect(Collectors.joining(","));

        return "{"
                + "\"storageMode\":\"" + (mysqlConnected ? "MySQL Connected" : "CSV Synced") + "\","
                + "\"totalBooks\":" + totalCopies + ","
                + "\"totalMembers\":" + allM.size() + ","
                + "\"booksIssued\":" + issued + ","
                + "\"booksReturned\":" + returned + ","
                + "\"overdueBooks\":" + overdue + ","
                + "\"activeEbooks\":" + ebooks + ","
                + "\"dailyActivity\":[" + dailyJson + "],"
                + "\"topCategories\":[" + catJson + "]"
                + "}";
    }

    private static String booksToJson() {
        return "[" + getAllBooks().stream().map(LibraryApiServer::bookToJson).collect(Collectors.joining(",")) + "]";
    }

    private static String bookToJson(Book b) {
        return "{\"id\":" + b.id + ",\"isbn\":\"" + jsonEsc(b.isbn) + "\",\"title\":\"" + jsonEsc(b.title) + "\",\"author\":\"" + jsonEsc(b.author) + "\",\"category\":\"" + jsonEsc(b.category) + "\",\"totalCopies\":" + b.totalCopies + ",\"availableCopies\":" + b.availableCopies + ",\"bookType\":\"" + jsonEsc(b.bookType) + "\",\"borrowCount\":" + b.borrowCount + ",\"addedDate\":\"" + jsonEsc(b.addedDate) + "\"}";
    }

    private static String membersToJson() {
        return "[" + getAllMembers().stream().map(LibraryApiServer::memberToJson).collect(Collectors.joining(",")) + "]";
    }

    private static String memberToJson(Member m) {
        return "{\"id\":" + m.id + ",\"regNo\":\"" + jsonEsc(m.regNo) + "\",\"name\":\"" + jsonEsc(m.name) + "\",\"department\":\"" + jsonEsc(m.department) + "\",\"email\":\"" + jsonEsc(m.email) + "\",\"joinedDate\":\"" + jsonEsc(m.joinedDate) + "\"}";
    }

    private static String transactionsToJson() {
        List<Book> allB = getAllBooks();
        List<Member> allM = getAllMembers();
        LocalDate today = LocalDate.now();
        return "[" + getAllTransactions().stream().map(t -> {
            Book b = allB.stream().filter(bk -> bk.id == t.bookId).findFirst().orElse(null);
            Member m = allM.stream().filter(mb -> mb.id == t.memberId).findFirst().orElse(null);
            String title = b != null ? b.title : "Book #" + t.bookId;
            String cat = b != null ? b.category : "General";
            String mName = m != null ? m.name : "Member #" + t.memberId;
            String reg = m != null ? m.regNo : "-";
            long overdueDays = 0;
            double fine = t.fine;
            boolean isOverdue = false;
            if ("ISSUED".equalsIgnoreCase(t.status)) {
                LocalDate due = LocalDate.parse(t.dueDate);
                if (due.isBefore(today)) {
                    isOverdue = true;
                    overdueDays = ChronoUnit.DAYS.between(due, today);
                    fine = overdueDays * FINE_PER_DAY;
                }
            }
            return "{\"id\":" + t.id + ",\"bookId\":" + t.bookId + ",\"bookTitle\":\"" + jsonEsc(title) + "\",\"category\":\"" + jsonEsc(cat) + "\",\"memberId\":" + t.memberId + ",\"memberName\":\"" + jsonEsc(mName) + "\",\"regNo\":\"" + jsonEsc(reg) + "\",\"issueDate\":\"" + jsonEsc(t.issueDate) + "\",\"dueDate\":\"" + jsonEsc(t.dueDate) + "\",\"returnDate\":\"" + jsonEsc(t.returnDate) + "\",\"fine\":" + fine + ",\"overdueDays\":" + overdueDays + ",\"isOverdue\":" + isOverdue + ",\"status\":\"" + jsonEsc(t.status) + "\"}";
        }).collect(Collectors.joining(",")) + "]";
    }

    private static boolean handleCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        String clean = json.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);
        List<String> pairs = new ArrayList<>();
        boolean inQ = false;
        StringBuilder cur = new StringBuilder();
        for (char c : clean.toCharArray()) {
            if (c == '"') inQ = !inQ;
            if (c == ',' && !inQ) { pairs.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        if (cur.length() > 0) pairs.add(cur.toString());
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx > 0) {
                map.put(pair.substring(0, idx).trim().replaceAll("^\"|\"$", ""), pair.substring(idx + 1).trim().replaceAll("^\"|\"$", ""));
            }
        }
        return map;
    }

    private static int extractIdFromQuery(String q) {
        if (q == null) return -1;
        for (String p : q.split("&")) {
            if (p.startsWith("id=")) {
                try { return Integer.parseInt(p.substring(3)); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return (s.contains(",") || s.contains("\"")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        boolean inQ = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') inQ = !inQ;
            else if (c == ',' && !inQ) { out.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
