import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Starts the whole project: shows the pages from the frontend folder and answers the /api/... calls
 * by running the calculators in the backend folder.
 *
 * Start it with:   java -cp out Server        then open http://localhost:8080
 */
public class Server {

    private record Response(int status, String json) {
    }

    private static final class HttpError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final int MAX_BODY = 100_000;
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8", "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon",
            "txt", "text/plain; charset=utf-8");
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("dob", "Date of birth"), Map.entry("dobA", "Date of birth (A)"), Map.entry("dobB", "Date of birth (B)"),
            Map.entry("onDate", "Calculation date"), Map.entry("start", "Start"), Map.entry("end", "End"),
            Map.entry("date", "Date"), Map.entry("amount", "Amount"), Map.entry("unit", "Unit"),
            Map.entry("operation", "Operation"), Map.entry("category", "Category"), Map.entry("from", "From unit"),
            Map.entry("to", "To unit"), Map.entry("value", "Value"), Map.entry("total", "Total classes"),
            Map.entry("attended", "Attended classes"), Map.entry("event", "Event"), Map.entry("cgpa", "CGPA"));

    private final AgeCalculator ageCalc = new AgeCalculator();
    private final DateCalculator dateCalc = new DateCalculator();
    private final TimeCalculator timeCalc = new TimeCalculator();
    private final UnitConverter converter = new UnitConverter();
    private final AttendanceCalculator attendance = new AttendanceCalculator();
    private final PercentageCalculator percentage = new PercentageCalculator();
    private final AverageCalculator averages = new AverageCalculator();
    private final CgpaCalculator cgpaCalc = new CgpaCalculator();
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private final Path frontend;
    private final RateLimiter limiter;
    private final boolean trustProxy;
    private final String allowedOrigin;

    private Server(Path frontend, int ratePerMinute, boolean trustProxy, String allowedOrigin) {
        this.frontend = frontend;
        this.limiter = new RateLimiter(ratePerMinute);
        this.trustProxy = trustProxy;
        this.allowedOrigin = allowedOrigin;
    }

    // ===================== Starting up =====================

    public static void main(String[] args) throws IOException {
        boolean hosted = System.getenv("PORT") != null;
        int port = envNumber("PORT", 8080);
        int ratePerMinute = envNumber("RATE_LIMIT_PER_MIN", 120);
        boolean trustProxy = hosted || System.getenv("TRUST_PROXY") != null;
        String origin = System.getenv("ALLOWED_ORIGIN");
        if (origin != null && origin.isBlank()) {
            origin = null;
        }
        Path frontendDir = findFrontend();
        HttpServer http = start(port, frontendDir, ratePerMinute, trustProxy, origin);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> http.stop(0)));
        String url = "http://localhost:" + http.getAddress().getPort();
        System.out.println("Allin1Calc is running at " + url);
        System.out.println("Pages are served from: " + frontendDir);
        System.out.println("Press Ctrl+C to stop.");
        if (!hosted) {
            openBrowser(url);
        }
    }

    /**
     * Starts the server and returns it. Port 0 picks a free port (the tests use this).
     * ratePerMinute is the number of /api requests one client may make per minute; 0 switches the limit off.
     * allowedOrigin is only needed when the pages are hosted on a different site than this server; null keeps CORS off.
     */
    public static HttpServer start(int port, Path frontendDir, int ratePerMinute, boolean trustProxy, String allowedOrigin) throws IOException {
        // slow or stuck connections are closed after 30 seconds
        System.setProperty("sun.net.httpserver.maxReqTime", "30");
        System.setProperty("sun.net.httpserver.maxRspTime", "30");
        Server app = new Server(frontendDir, ratePerMinute, trustProxy, allowedOrigin);
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/", app::handle);
        http.setExecutor(Executors.newFixedThreadPool(8));
        http.start();
        return http;
    }

    private static int envNumber(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println(name + " must be a number, but was: " + value);
            System.exit(1);
            return fallback;
        }
    }

    private static Path findFrontend() {
        List<String> candidates = new ArrayList<>();
        if (System.getenv("FRONTEND_DIR") != null) {
            candidates.add(System.getenv("FRONTEND_DIR"));
        }
        candidates.add("frontend");
        candidates.add("../frontend");
        for (String c : candidates) {
            Path p = Paths.get(c).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("index.html"))) {
                return p;
            }
        }
        System.err.println("Could not find the frontend folder. Run the server from the Allin1Calc folder.");
        System.exit(1);
        return null;
    }

    /** Opens the home page on your own computer. Skipped when there is no desktop. */
    private static void openBrowser(String url) {
        if (System.getenv("NO_BROWSER") != null) {
            return;
        }
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | Error e) {
            // not a problem: just open the link yourself
        }
    }

    // ===================== HTTP =====================

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            addSecurityHeaders(ex);
            if ("OPTIONS".equals(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (path.startsWith("/api/")) {
                handleApi(ex, method, path);
            } else {
                servePage(ex, method, path);
            }
        } catch (RuntimeException e) {
            System.err.println("Server error: " + e);
            sendJson(ex, error(500, "Something went wrong on the server."));
        } finally {
            ex.close();
        }
    }

    private void handleApi(HttpExchange ex, String method, String path) throws IOException {
        if (!limiter.allow(clientAddress(ex))) {
            sendJson(ex, error(429, "Too many requests. Please wait a minute and try again."));
            return;
        }
        byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            discardBody(ex.getRequestBody());
            sendJson(ex, error(413, "Request is too large."));
            return;
        }
        sendJson(ex, route(method, path, ex.getRequestURI().getRawQuery(), new String(body, StandardCharsets.UTF_8)));
    }

    /**
     * Reads and throws away up to 1 MB of an oversized request. Without this the connection is closed while the
     * client is still sending, and the client sees a "connection reset" instead of the 413 message.
     */
    private static void discardBody(InputStream in) {
        byte[] buffer = new byte[8192];
        long discarded = 0;
        try {
            int n;
            while (discarded < 1_000_000 && (n = in.read(buffer)) > 0) {
                discarded += n;
            }
        } catch (IOException e) {
            // the client gave up, nothing more to do
        }
    }

    private void addSecurityHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (allowedOrigin != null) {
            h.set("Access-Control-Allow-Origin", allowedOrigin);
            h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            h.set("Access-Control-Allow-Headers", "Content-Type");
            h.set("Vary", "Origin");
        }
    }

    /** Behind a hosting proxy the real client is the last address the proxy added to X-Forwarded-For. */
    private String clientAddress(HttpExchange ex) {
        String forwarded = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (trustProxy && forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private void servePage(HttpExchange ex, String method, String path) throws IOException {
        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendJson(ex, error(405, "Method not allowed."));
            return;
        }
        String name = path.equals("/") ? "index.html" : path.substring(1);
        Path file = frontend.resolve(name).normalize();
        if (!file.startsWith(frontend) || !Files.isRegularFile(file)) {
            sendJson(ex, error(404, "Not found."));
            return;
        }
        String fileName = file.getFileName().toString();
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (method.equals("HEAD")) {
            ex.sendResponseHeaders(200, -1);
            return;
        }
        byte[] data = Files.readAllBytes(file);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private static void sendJson(HttpExchange ex, Response response) throws IOException {
        byte[] data = response.json().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(response.status(), ex.getRequestMethod().equals("HEAD") ? -1 : data.length);
        if (!ex.getRequestMethod().equals("HEAD")) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(data);
            }
        }
    }

    /** At most {@code limit} requests per client per minute. Old entries are dropped when the table grows. */
    private static final class RateLimiter {
        private static final long WINDOW_MS = 60_000;
        private final int limit;
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

        private static final class Window {
            final long startedAt;
            final AtomicInteger count = new AtomicInteger();

            Window(long startedAt) {
                this.startedAt = startedAt;
            }
        }

        RateLimiter(int limit) {
            this.limit = limit;
        }

        boolean allow(String client) {
            if (limit <= 0) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (windows.size() > 5000) {
                windows.values().removeIf(w -> now - w.startedAt >= WINDOW_MS);
            }
            Window w = windows.compute(client, (key, old) -> old == null || now - old.startedAt >= WINDOW_MS ? new Window(now) : old);
            return w.count.incrementAndGet() <= limit;
        }
    }

    // ===================== API routes =====================

    private Response route(String method, String path, String query, String body) {
        try {
            return new Response(200, Json.write(dispatch(method, path, query, body)));
        } catch (Validator.InvalidInputException e) {
            return error(400, e.getMessage());
        } catch (HttpError e) {
            return error(e.status, e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Unexpected error on " + method + " " + path + ": " + e);
            return error(500, "Something went wrong on the server.");
        }
    }

    private static Response error(int status, String message) {
        return new Response(status, Json.write(map("error", message)));
    }

    private Map<String, Object> dispatch(String method, String path, String query, String body) {
        return switch (path) {
            case "/api/health" -> {
                need(method, "GET");
                yield map("status", "ok", "app", "Allin1Calc");
            }
            case "/api/units" -> {
                need(method, "GET");
                yield units();
            }
            case "/api/leap" -> {
                need(method, "GET");
                yield leap(query);
            }
            case "/api/age" -> age(post(method, body));
            case "/api/age/difference" -> ageDifference(post(method, body));
            case "/api/date/difference" -> dateDifference(post(method, body));
            case "/api/date/add" -> dateAdd(post(method, body));
            case "/api/date/workdays" -> workdays(post(method, body));
            case "/api/time/calc" -> timeCalc(post(method, body));
            case "/api/time/hours-worked" -> hoursWorked(post(method, body));
            case "/api/time/countdown" -> countdown(post(method, body));
            case "/api/convert" -> convert(post(method, body));
            case "/api/attendance" -> attendance(post(method, body));
            case "/api/percentage" -> percentage(post(method, body));
            case "/api/average" -> average(post(method, body));
            case "/api/cgpa" -> cgpa(post(method, body));
            default -> throw new HttpError(404, "Not found.");
        };
    }

    // ---------- Age and date ----------

    private Map<String, Object> age(Map<String, Object> b) {
        LocalDate today = optDate(b, "today", LocalDate.now());
        LocalDate dob = date(b, "dob");
        LocalDate on = optDate(b, "onDate", today);
        Validator.requireNotFuture(dob, today, "Date of birth");
        AgeCalculator.AgeResult r = ageCalc.calculateAge(dob, on);
        return map("years", r.years(), "months", r.months(), "days", r.days(),
                "totalDays", ageCalc.totalDays(dob, on), "totalWeeks", ageCalc.totalWeeks(dob, on),
                "totalHours", ageCalc.totalHours(dob, on), "birthWeekday", Validator.weekday(ageCalc.birthWeekday(dob)),
                "nextBirthday", ageCalc.nextBirthday(dob, on).toString(),
                "daysToNextBirthday", ageCalc.daysUntilBirthday(dob, on));
    }

    private Map<String, Object> ageDifference(Map<String, Object> b) {
        LocalDate today = optDate(b, "today", LocalDate.now());
        LocalDate a = date(b, "dobA");
        LocalDate c = date(b, "dobB");
        Validator.requireNotFuture(a, today, "Date of birth (A)");
        Validator.requireNotFuture(c, today, "Date of birth (B)");
        AgeCalculator.AgeDifference d = ageCalc.ageDifference(a, c);
        String older = d.olderPerson() == 0 ? "same" : d.olderPerson() == 1 ? "A" : "B";
        return map("older", older, "years", d.gap().years(), "months", d.gap().months(), "days", d.gap().days(),
                "totalDays", d.totalDays());
    }

    private Map<String, Object> dateDifference(Map<String, Object> b) {
        LocalDate start = date(b, "start");
        LocalDate end = date(b, "end");
        Period p = dateCalc.difference(start, end);
        long days = dateCalc.daysBetween(start, end);
        return map("years", p.getYears(), "months", p.getMonths(), "days", p.getDays(), "totalDays", days,
                "totalWeeks", days / 7, "remainingDays", days % 7, "startAfterEnd", start.isAfter(end));
    }

    private Map<String, Object> dateAdd(Map<String, Object> b) {
        LocalDate date = date(b, "date");
        int amount = whole(b, "amount", 1_000_000);
        if (amount < 0) {
            throw Validator.fail("Amount cannot be negative.");
        }
        DateCalculator.Unit unit = DateCalculator.Unit.parse(str(b, "unit"));
        String op = str(b, "operation").toLowerCase(Locale.ROOT);
        LocalDate result;
        if (op.equals("add")) {
            result = dateCalc.add(date, amount, unit);
        } else if (op.equals("subtract")) {
            result = dateCalc.subtract(date, amount, unit);
        } else {
            throw Validator.fail("Operation must be add or subtract.");
        }
        return map("result", result.toString(), "weekday", Validator.weekday(result.getDayOfWeek()));
    }

    private Map<String, Object> workdays(Map<String, Object> b) {
        LocalDate start = date(b, "start");
        LocalDate end = date(b, "end");
        return map("workdays", dateCalc.workdaysBetween(start, end), "weekendDays", dateCalc.weekendDaysBetween(start, end),
                "totalDays", dateCalc.inclusiveDays(start, end));
    }

    private Map<String, Object> leap(String query) {
        Map<String, String> q = parseQuery(query);
        if (q.containsKey("year")) {
            int year = year(q.get("year"));
            return map("year", year, "leap", dateCalc.isLeapYear(year));
        }
        if (q.containsKey("from") && q.containsKey("to")) {
            int from = year(q.get("from"));
            int to = year(q.get("to"));
            List<Integer> years = dateCalc.leapYearsBetween(from, to);
            return map("from", from, "to", to, "leapYears", years, "count", years.size());
        }
        throw Validator.fail("Provide year, or from and to.");
    }

    private static int year(String text) {
        int y = Validator.parseWholeNumber(text);
        if (y < 1 || y > 9999) {
            throw Validator.fail("Year must be between 1 and 9999.");
        }
        return y;
    }

    // ---------- Time ----------

    private Map<String, Object> timeCalc(Map<String, Object> b) {
        Duration a = Validator.parseDuration(str(b, "a"));
        Duration c = Validator.parseDuration(str(b, "b"));
        String op = str(b, "operation").toLowerCase(Locale.ROOT);
        Duration result;
        if (op.equals("add")) {
            result = timeCalc.add(a, c);
        } else if (op.equals("subtract")) {
            result = timeCalc.subtract(a, c);
        } else {
            throw Validator.fail("Operation must be add or subtract.");
        }
        return map("result", TimeCalculator.formatHm(result), "hours", result.toHours(), "minutes", result.toMinutesPart(),
                "totalMinutes", result.toMinutes());
    }

    private Map<String, Object> hoursWorked(Map<String, Object> b) {
        LocalTime start = Validator.parseTime(str(b, "start"));
        LocalTime end = Validator.parseTime(str(b, "end"));
        Duration brk = b.get("break") == null ? Duration.ZERO : Validator.parseDuration(str(b, "break"));
        Duration worked = timeCalc.hoursWorked(start, end, brk);
        return map("worked", TimeCalculator.formatHm(worked), "hours", worked.toHours(), "minutes", worked.toMinutesPart(),
                "decimalHours", Validator.round(TimeCalculator.decimalHours(worked), 2), "overnight", timeCalc.isOvernight(start, end));
    }

    private Map<String, Object> countdown(Map<String, Object> b) {
        LocalDateTime event = dateTime(str(b, "event"));
        LocalDateTime now = b.get("now") == null ? LocalDateTime.now() : dateTime(str(b, "now"));
        Duration left = timeCalc.countdown(event, now);
        if (left.isNegative() || left.isZero()) {
            throw Validator.fail("That date and time has already passed.");
        }
        return map("days", left.toDays(), "hours", left.toHoursPart(), "minutes", left.toMinutesPart(),
                "seconds", left.toSecondsPart(), "totalSeconds", left.getSeconds());
    }

    private static LocalDateTime dateTime(String text) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(text);
            Validator.requireSupportedDate(parsed.toLocalDate());
            return parsed;
        } catch (DateTimeParseException e) {
            throw Validator.fail("Invalid date or time. Use the form 2026-12-31T18:30.");
        }
    }

    // ---------- Units ----------

    private Map<String, Object> units() {
        List<Object> list = new ArrayList<>();
        for (UnitConverter.Category c : UnitConverter.Category.values()) {
            list.add(map("category", c.name(), "name", c.toString(), "temperature", c.isTemperature(), "units", c.unitNames()));
        }
        return map("categories", list);
    }

    private Map<String, Object> convert(Map<String, Object> b) {
        UnitConverter.Category category = converter.parseCategory(str(b, "category"));
        String from = str(b, "from");
        String to = str(b, "to");
        double value = num(b, "value");
        double result = converter.convert(category, from, to, value);
        return map("category", category.toString(), "from", from, "to", to, "value", value, "result", Validator.round(result, 10));
    }

    // ---------- Student tools ----------

    private Map<String, Object> attendance(Map<String, Object> b) {
        boolean scheduleInput = b.containsKey("scheduled") || b.containsKey("classesDone");
        int scheduled;
        int total;
        int attended = whole(b, "attended", 1_000_000);
        if (scheduleInput) {
            if (!b.containsKey("scheduled") || !b.containsKey("classesDone")) {
                throw Validator.fail("Enter both total scheduled classes and classes done.");
            }
            scheduled = whole(b, "scheduled", 1_000_000);
            total = whole(b, "classesDone", 1_000_000);
            attendance.validateSchedule(scheduled, total, attended);
        } else {
            // Keep the original API shape for the bunk planner and existing callers.
            total = whole(b, "total", 1_000_000);
            scheduled = total;
            attendance.validate(attended, total);
        }
        double target = b.get("target") == null ? 75 : num(b, "target");
        attendance.validateTarget(target);
        int planMiss = b.get("planMiss") == null ? 0 : whole(b, "planMiss", 1_000_000);
        int remaining = scheduled - total;
        boolean started = total > 0;
        double pct = started ? attendance.percentage(attended, total) : 0;
        int needed = started ? attendance.classesNeeded(attended, total, target) : 0;
        int safe = started ? attendance.safeBunks(attended, total, target) : 0;
        if (scheduleInput) {
            safe = Math.min(safe, remaining);
        }
        List<Object> scheduleProjections = new ArrayList<>();
        if (scheduleInput) {
            for (int k = 1; k <= Math.min(5, remaining); k++) {
                scheduleProjections.add(map("upcoming", k,
                        "attend", Validator.round(attendance.projectedPercentage(attended, total, k, true), 2),
                        "miss", Validator.round(attendance.projectedPercentage(attended, total, k, false), 2)));
            }
        }
        List<Object> projections = new ArrayList<>();
        if (!scheduleInput) {
            for (int k = 1; k <= 5; k++) {
                projections.add(map("missed", k, "percentage", Validator.round(attendance.percentageAfterMissing(attended, total, k), 2)));
            }
        }
        Map<String, Object> out = map("percentage", started ? Validator.round(pct, 2) : null,
                "target", target, "status", started ? attendance.status(pct, target) : "Not started",
                "classesNeeded", needed, "safeBunks", safe, "scheduled", scheduled,
                "classesDone", total, "attended", attended, "remaining", remaining);
        out.put("percentageAfterAttending", started && needed > 0
                ? Validator.round(attendance.percentageAfterAttending(attended, total, needed), 2) : null);
        out.put("scheduleProjections", scheduleProjections);
        out.put("projections", projections);
        if (!scheduleInput) {
            double planned = attendance.percentageAfterMissing(attended, total, planMiss);
            out.put("plan", map("missed", planMiss, "percentage", Validator.round(planned, 2), "meetsTarget", planned >= target));
        } else {
            out.put("plan", null);
        }
        return out;
    }

    private Map<String, Object> percentage(Map<String, Object> b) {
        String op = str(b, "operation").toLowerCase(Locale.ROOT);
        double x = num(b, "a");
        double y = num(b, "b");
        double result = switch (op) {
            case "percent-of" -> percentage.percentOf(x, y);
            case "what-percent" -> percentage.whatPercent(x, y);
            case "find-whole" -> percentage.findWhole(x, y);
            case "increase" -> percentage.increase(y, x);
            case "decrease" -> percentage.decrease(y, x);
            case "change" -> percentage.percentChange(x, y);
            default -> throw Validator.fail("Choose one of the six supported percentage operations.");
        };
        return map("operation", op, "result", Validator.round(result, 6));
    }

    private Map<String, Object> average(Map<String, Object> b) {
        Object raw = b.get("numbers");
        List<Double> numbers = new ArrayList<>();
        if (raw instanceof List<?> list) {
            if (list.size() > 1000) {
                throw Validator.fail("Please enter at most 1000 numbers.");
            }
            for (Object o : list) {
                numbers.add(o instanceof Number n ? finite(n.doubleValue()) : Validator.parseNumber(String.valueOf(o)));
            }
        } else if (raw instanceof String s) {
            numbers = Validator.parseNumberList(s);
        } else {
            throw Validator.fail("Numbers is required.");
        }
        AverageCalculator.Stats s = averages.stats(numbers);
        return map("count", s.count(), "sum", Validator.round(s.sum(), 6), "average", Validator.round(s.average(), 6),
                "min", s.min(), "max", s.max());
    }

    private Map<String, Object> cgpa(Map<String, Object> b) {
        double cgpa = num(b, "cgpa");
        double multiplier = b.get("multiplier") == null ? 9.5 : num(b, "multiplier");
        return map("cgpa", cgpa, "multiplier", multiplier, "percentage", Validator.round(cgpaCalc.toPercentage(cgpa, multiplier), 2));
    }

    // ---------- Reading the request ----------

    private static void need(String method, String expected) {
        if (!expected.equals(method)) {
            throw new HttpError(405, "Method not allowed. Use " + expected + ".");
        }
    }

    private static Map<String, Object> post(String method, String body) {
        need(method, "POST");
        if (body == null || body.isBlank()) {
            throw Validator.fail("Request body is empty.");
        }
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map<?, ?> m)) {
            throw Validator.fail("Request body must be a JSON object.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    private static String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    private static String str(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw Validator.fail(label(key) + " is required.");
        }
        if (v instanceof String s) {
            return s.trim();
        }
        throw Validator.fail(label(key) + " must be text.");
    }

    private static double finite(double d) {
        if (!Double.isFinite(d)) {
            throw Validator.fail("Enter a valid number.");
        }
        return d;
    }

    private static double num(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw Validator.fail(label(key) + " is required.");
        }
        if (v instanceof Number n) {
            return finite(n.doubleValue());
        }
        if (v instanceof String s) {
            return Validator.parseNumber(s);
        }
        throw Validator.fail("Enter a valid number.");
    }

    private static int whole(Map<String, Object> b, String key, int max) {
        double d = num(b, key);
        if (d != Math.rint(d)) {
            throw Validator.fail("Enter a valid whole number.");
        }
        if (Math.abs(d) > max) {
            throw Validator.fail(label(key) + " is too large.");
        }
        return (int) d;
    }

    private static LocalDate date(Map<String, Object> b, String key) {
        try {
            return Validator.requireSupportedDate(LocalDate.parse(str(b, key)));
        } catch (DateTimeParseException e) {
            throw Validator.fail("Invalid date.");
        }
    }

    private static LocalDate optDate(Map<String, Object> b, String key, LocalDate fallback) {
        return b.get(key) == null ? fallback : date(b, key);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
