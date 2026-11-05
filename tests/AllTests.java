import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tests for the whole project, with no test library needed.
 * Run from the Allin1Calc folder:
 *   javac -d out *.java backend/*.java tests/AllTests.java
 *   java -cp out AllTests
 * Every line says PASS or FAIL. The program ends with exit code 1 if anything failed.
 */
public class AllTests {
    private static int passed = 0;
    private static int failed = 0;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String[] POST_ENDPOINTS = {"/api/age", "/api/age/difference", "/api/date/difference", "/api/date/add",
            "/api/date/workdays", "/api/time/calc", "/api/time/hours-worked", "/api/time/countdown", "/api/convert",
            "/api/attendance", "/api/percentage", "/api/average", "/api/cgpa"};

    private record Reply(int status, String body, java.net.http.HttpHeaders headers) {
        boolean has(String text) {
            return body.contains(text);
        }
    }

    public static void main(String[] args) {
        try {
            runAll();
        } catch (Throwable crash) {
            System.out.println("  FAIL  The test run crashed: " + crash);
            System.exit(1);
        }
    }

    private static void runAll() throws Exception {
        Path frontend = Files.isDirectory(Paths.get("frontend")) ? Paths.get("frontend") : Paths.get("../frontend");
        logicTests();
        inputTests();
        projectFileTests(frontend);

        HttpServer server = Server.start(0, frontend, 0, false, null);
        int port = server.getAddress().getPort();
        try {
            apiTests(port);
            badRequestTests(port);
            securityTests(port);
            pageTests(port, frontend);
        } finally {
            server.stop(0);
        }
        rateLimitTests(frontend);
        corsTests(frontend);

        System.out.println();
        System.out.println("Passed: " + passed + "   Failed: " + failed);
        System.out.println(failed == 0 ? "All good!" : "Some checks failed. Look for FAIL above.");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------- helpers ----------

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    /** Passes only when the code throws InvalidInputException (a clean message, not a crash and not silence). */
    private static void checkError(String name, Runnable code) {
        boolean rejected = false;
        try {
            code.run();
        } catch (Validator.InvalidInputException e) {
            rejected = true;
        } catch (RuntimeException e) {
            rejected = false;
        }
        check(name, rejected);
    }

    private static Reply call(int port, String method, String path, String body) throws Exception {
        return callWith(port, method, path, body, new String[0]);
    }

    private static Reply callWith(int port, String method, String path, String body, String[] headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), r.body(), r.headers());
    }

    private static Reply post(int port, String path, String body) throws Exception {
        return call(port, "POST", path, body);
    }

    /** Sends the path exactly as written (the HTTP client would tidy up ../ for us). */
    private static int rawStatus(int port, String path) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.0\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String first = new String(in.readNBytes(12), StandardCharsets.US_ASCII);
            return Integer.parseInt(first.substring(9, 12));
        }
    }

    // ---------- 1. calculation logic ----------

    private static void logicTests() {
        AgeCalculator age = new AgeCalculator();
        DateCalculator date = new DateCalculator();
        TimeCalculator time = new TimeCalculator();
        UnitConverter units = new UnitConverter();
        AttendanceCalculator att = new AttendanceCalculator();
        PercentageCalculator pct = new PercentageCalculator();
        AverageCalculator avg = new AverageCalculator();
        CgpaCalculator cg = new CgpaCalculator();

        System.out.println("--- Calculation logic ---");
        System.out.println("--- Age ---");
        check("Born 01-01-2000, on 01-01-2026 = 26y 0m 0d", age.calculateAge(LocalDate.of(2000, 1, 1), LocalDate.of(2026, 1, 1)).equals(new AgeCalculator.AgeResult(26, 0, 0)));
        check("Born 14-03-2005, on 22-10-2026 = 21y 7m 8d", age.calculateAge(LocalDate.of(2005, 3, 14), LocalDate.of(2026, 10, 22)).equals(new AgeCalculator.AgeResult(21, 7, 8)));
        check("Born 29-02-2000, on 01-03-2026 = 26y 0m 1d", age.calculateAge(LocalDate.of(2000, 2, 29), LocalDate.of(2026, 3, 1)).equals(new AgeCalculator.AgeResult(26, 0, 1)));
        check("Total days 14-03-2005 to 22-10-2026 = 7892", age.totalDays(LocalDate.of(2005, 3, 14), LocalDate.of(2026, 10, 22)) == 7892);
        check("Next birthday = 14-03-2027 (143 days)", age.nextBirthday(LocalDate.of(2005, 3, 14), LocalDate.of(2026, 10, 22)).equals(LocalDate.of(2027, 3, 14))
                && age.daysUntilBirthday(LocalDate.of(2005, 3, 14), LocalDate.of(2026, 10, 22)) == 143);
        check("14-03-2005 was a Monday", age.birthWeekday(LocalDate.of(2005, 3, 14)) == DayOfWeek.MONDAY);
        check("Age difference 2000-01-01 vs 2002-03-05 = A older by 2y 2m 4d", age.ageDifference(LocalDate.of(2000, 1, 1), LocalDate.of(2002, 3, 5)).gap().equals(new AgeCalculator.AgeResult(2, 2, 4)));
        checkError("Age on a date before birth is rejected", () -> age.calculateAge(LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1)));
        checkError("Future date of birth is rejected", () -> Validator.requireNotFuture(LocalDate.of(2999, 1, 1), LocalDate.now(), "Date of birth"));

        System.out.println("--- Dates ---");
        check("01-01-2026 to 31-12-2026 = 364 days", date.daysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)) == 364);
        check("31-01-2026 + 1 month = 28-02-2026", date.add(LocalDate.of(2026, 1, 31), 1, DateCalculator.Unit.MONTHS).equals(LocalDate.of(2026, 2, 28)));
        check("05-03-2026 - 10 days = 23-02-2026", date.subtract(LocalDate.of(2026, 3, 5), 10, DateCalculator.Unit.DAYS).equals(LocalDate.of(2026, 2, 23)));
        check("Workdays 19-10-2026 to 25-10-2026 = 5 (+2 weekend)", date.workdaysBetween(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)) == 5
                && date.weekendDaysBetween(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)) == 2);
        check("Leap years: 1900 no, 2000 yes, 2024 yes, 2100 no", !date.isLeapYear(1900) && date.isLeapYear(2000) && date.isLeapYear(2024) && !date.isLeapYear(2100));
        check("Leap years 2020-2030 = 2020, 2024, 2028", date.leapYearsBetween(2020, 2030).equals(List.of(2020, 2024, 2028)));

        System.out.println("--- Time ---");
        check("02:45 + 03:30 = 06:15", TimeCalculator.formatHm(time.add(Validator.parseDuration("02:45"), Validator.parseDuration("03:30"))).equals("06:15"));
        check("06:15 - 03:30 = 02:45", TimeCalculator.formatHm(time.subtract(Validator.parseDuration("06:15"), Validator.parseDuration("03:30"))).equals("02:45"));
        check("Worked 09:30 to 17:30, break 1h = 7 hours", time.hoursWorked(LocalTime.of(9, 30), LocalTime.of(17, 30), Duration.ofHours(1)).equals(Duration.ofHours(7)));
        check("Night shift 22:00 to 06:00 = 8 hours", time.hoursWorked(LocalTime.of(22, 0), LocalTime.of(6, 0), Duration.ZERO).equals(Duration.ofHours(8)));
        checkError("Subtracting a bigger time is rejected", () -> time.subtract(Duration.ofHours(1), Duration.ofHours(2)));
        checkError("Time 09:60 is rejected", () -> Validator.parseTime("09:60"));

        System.out.println("--- Units ---");
        check("1 Kilometer = 1000 Meter", near(units.convert(UnitConverter.Category.LENGTH, "Kilometer", "Meter", 1), 1000));
        check("1 Mile = 1.609344 Kilometer", near(units.convert(UnitConverter.Category.LENGTH, "Mile", "Kilometer", 1), 1.609344));
        check("1 Kilogram = 1000 Gram", near(units.convert(UnitConverter.Category.WEIGHT, "Kilogram", "Gram", 1), 1000));
        check("100 Celsius = 212 Fahrenheit", near(units.convert(UnitConverter.Category.TEMPERATURE, "Celsius", "Fahrenheit", 100), 212));
        check("0 Celsius = 273.15 Kelvin", near(units.convert(UnitConverter.Category.TEMPERATURE, "Celsius", "Kelvin", 0), 273.15));
        check("36 km/h = 10 m/s", near(units.convert(UnitConverter.Category.SPEED, "km/h", "m/s", 36), 10));
        check("1 GB = 1024 MB", near(units.convert(UnitConverter.Category.DATA_SIZE, "GB", "MB", 1), 1024));
        check("1 Day = 24 Hour", near(units.convert(UnitConverter.Category.TIME, "Day", "Hour", 1), 24));
        checkError("Negative length is rejected", () -> units.convert(UnitConverter.Category.LENGTH, "Meter", "Foot", -5));
        checkError("Below absolute zero is rejected", () -> units.convert(UnitConverter.Category.TEMPERATURE, "Celsius", "Kelvin", -300));

        System.out.println("--- Attendance ---");
        check("32 of 45 = 71.11%", Math.abs(att.percentage(32, 45) - 71.11) < 0.01);
        check("32 of 45, target 75: need 7 classes", att.classesNeeded(32, 45, 75) == 7);
        check("42 of 60, target 75: need 12 classes", att.classesNeeded(42, 60, 75) == 12);
        check("40 of 50, target 75: can miss 3 classes", att.safeBunks(40, 50, 75) == 3);
        check("Schedule projections calculate the next two classes", near(att.projectedPercentage(40, 50, 2, true), 42.0 / 52 * 100)
                && near(att.projectedPercentage(40, 50, 2, false), 40.0 / 52 * 100));
        checkError("Schedule detail is capped at 10,000 classes", () -> att.validateSchedule(10_001, 50, 40));
        check("28 of 30, target 75: can miss 7 classes", att.safeBunks(28, 30, 75) == 7);
        check("Below target means 0 safe bunks", att.safeBunks(32, 45, 75) == 0);
        check("Missing 3 more of 40/50 = 75.47%", Math.abs(att.percentageAfterMissing(40, 50, 3) - 75.47) < 0.01);
        checkError("Attended more than total is rejected", () -> att.percentage(50, 40));
        checkError("Total 0 is rejected", () -> att.percentage(0, 0));
        checkError("Target 100 is rejected", () -> att.classesNeeded(10, 20, 100));

        System.out.println("--- Percentage, average, CGPA ---");
        check("20% of 50 = 10", near(pct.percentOf(20, 50), 10));
        check("25 is 12.5% of 200", near(pct.whatPercent(25, 200), 12.5));
        check("Increase 200 by 10% = 220", near(pct.increase(200, 10), 220));
        check("Decrease 200 by 25% = 150", near(pct.decrease(200, 25), 150));
        check("Change 50 to 75 = +50%", near(pct.percentChange(50, 75), 50));
        check("Average of 85, 72, 91, 78, 88 = 82.8", near(avg.stats(List.of(85.0, 72.0, 91.0, 78.0, 88.0)).average(), 82.8));
        check("CGPA 7.5 x 9.5 = 71.25", near(cg.toPercentage(7.5, 9.5), 71.25));
        checkError("Percent change from 0 is rejected", () -> pct.percentChange(0, 5));
        checkError("CGPA 11 is rejected", () -> cg.toPercentage(11, 9.5));


        System.out.println("--- Attendance: random cases (minimum and maximum really are minimum and maximum) ---");
        Random cases = new Random(11);
        int neededWrong = 0;
        int bunksWrong = 0;
        for (int i = 0; i < 2000; i++) {
            int total = 1 + cases.nextInt(300);
            int attended = cases.nextInt(total + 1);
            double target = 1 + cases.nextInt(990) / 10.0;
            if (target > 99) {
                target = 99;
            }
            int need = att.classesNeeded(attended, total, target);
            boolean reaches = att.percentageAfterAttending(attended, total, need) >= target - 1e-9;
            boolean minimal = need == 0 || att.percentageAfterAttending(attended, total, need - 1) < target;
            if (!reaches || !minimal) {
                neededWrong++;
            }
            int bunks = att.safeBunks(attended, total, target);
            boolean stays = bunks == 0 ? true : att.percentageAfterMissing(attended, total, bunks) >= target - 1e-9;
            boolean maximal = att.percentageAfterMissing(attended, total, bunks + 1) < target || att.percentage(attended, total) < target;
            if (!stays || !maximal) {
                bunksWrong++;
            }
        }
        check("Classes needed is exactly the smallest number that reaches the target (2000 random cases)", neededWrong == 0);
        check("Safe bunks is exactly the largest number that keeps the target (2000 random cases)", bunksWrong == 0);
        check("30 of 46 with target 70 needs 8 classes (7.33 is rounded up)", att.classesNeeded(30, 46, 70) == 8);

        System.out.println("--- Regression: problems found in the audit ---");
        LocalDate longStart = LocalDate.of(1, 1, 1);
        LocalDate longEnd = LocalDate.of(9999, 12, 31);
        long begin = System.nanoTime();
        long fastCount = date.workdaysBetween(longStart, longEnd);
        long millis = (System.nanoTime() - begin) / 1_000_000;
        long slowCount = longStart.datesUntil(longEnd.plusDays(1)).filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        check("Workdays from year 1 to 9999 match a day-by-day count and are fast (" + millis + " ms)", fastCount == slowCount && millis < 200);
        Random random = new Random(7);
        boolean allSame = true;
        for (int i = 0; i < 300; i++) {
            LocalDate x = LocalDate.of(2000, 1, 1).plusDays(random.nextInt(15000));
            LocalDate y = x.plusDays(random.nextInt(900) - 300);
            long brute = (x.isAfter(y) ? y : x).datesUntil((x.isAfter(y) ? x : y).plusDays(1))
                    .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
            allSame &= brute == date.workdaysBetween(x, y);
        }
        check("Workdays formula matches a day-by-day count on 300 random ranges", allSame);
        checkError("Adding a million years is rejected", () -> date.add(LocalDate.of(2026, 1, 1), 1_000_000, DateCalculator.Unit.YEARS));
        checkError("Date result before year 1 is rejected", () -> date.subtract(LocalDate.of(5, 1, 1), 10, DateCalculator.Unit.YEARS));
        checkError("1e308 km to cm does not become a silent blank", () -> units.convert(UnitConverter.Category.LENGTH, "Kilometer", "Centimeter", 1e308));
        checkError("Percentage overflow is rejected", () -> pct.percentOf(1e308, 1e308));
        checkError("Average overflow is rejected", () -> avg.stats(List.of(1e308, 1e308)));
        check("Unknown unit message stays short", errorMessage(() -> units.convert(UnitConverter.Category.LENGTH, "Meter", "x".repeat(500), 1)).length() < 80);
    }

    private static String errorMessage(Runnable code) {
        try {
            code.run();
        } catch (Validator.InvalidInputException e) {
            return e.getMessage();
        }
        return "";
    }

    // ---------- 2. input checking ----------

    private static void inputTests() {
        System.out.println("--- Input checking never crashes ---");
        String[] junk = {"", "   ", "abc", "NaN", "Infinity", "--5", "1,5", "0x10", "5 5", "1e999", "9".repeat(100), "\u0663", "\uFF11\uFF12", "<script>", "'; DROP TABLE x;--"};
        boolean numberOk = true;
        boolean wholeOk = true;
        boolean timeOk = true;
        boolean dateOk = true;
        for (String s : junk) {
            numberOk &= throwsInvalid(() -> Validator.parseNumber(s));
            wholeOk &= throwsInvalid(() -> Validator.parseWholeNumber(s));
            timeOk &= throwsInvalid(() -> Validator.parseTime(s));
            dateOk &= throwsInvalid(() -> Validator.parseDate(s));
        }
        numberOk &= throwsInvalid(() -> Validator.parseNumber(null));
        check("15 kinds of bad text are rejected as numbers", numberOk);
        check("15 kinds of bad text are rejected as whole numbers", wholeOk);
        check("15 kinds of bad text are rejected as times", timeOk);
        check("15 kinds of bad text are rejected as dates", dateOk);
        check("A number longer than 40 characters is rejected", throwsInvalid(() -> Validator.parseNumber("1".repeat(41))));
        check("Valid numbers still work", near(Validator.parseNumber(" 12.5 "), 12.5) && near(Validator.parseNumber("1e3"), 1000) && near(Validator.parseNumber("-4"), -4));
        check("Time 9:30 and 27:15 (as duration) are understood", Validator.parseTime("9:30").equals(LocalTime.of(9, 30)) && Validator.parseDuration("27:15").equals(Duration.ofHours(27).plusMinutes(15)));
        check("Date 14-03-2005 and 2005-03-14 both work", Validator.parseDate("14-03-2005").equals(LocalDate.of(2005, 3, 14)) && Validator.parseDate("2005-03-14").equals(LocalDate.of(2005, 3, 14)));
        checkError("Date 31-02-2026 is rejected", () -> Validator.parseDate("31-02-2026"));
        checkError("Year 10000 is rejected", () -> Validator.requireSupportedDate(LocalDate.of(10000, 1, 1)));
        checkError("Year 0 is rejected", () -> Validator.requireSupportedDate(LocalDate.of(0, 1, 1)));
        checkError("Huge year in text is rejected", () -> Validator.parseDate("+999999999-01-01"));
        check("Number lists accept commas, spaces and semicolons", Validator.parseNumberList("85, 72 91;78").size() == 4);
        checkError("A number list with a letter is rejected", () -> Validator.parseNumberList("1, x, 3"));
    }

    private static boolean throwsInvalid(Runnable code) {
        try {
            code.run();
        } catch (Validator.InvalidInputException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    // ---------- 3. project files ----------

    private static void projectFileTests(Path frontend) throws Exception {
        System.out.println("--- Project files ---");
        Path root = frontend.toAbsolutePath().getParent();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().matches(".*\\.(java|js|html|css)$") && !p.toString().contains("/out/")).forEach(sources::add);
        }
        Pattern secret = Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token)\\s*[=:]\\s*[\"'][^\"']+[\"']");
        Pattern inlineHandler = Pattern.compile("(?i)<[^>]+\\son[a-z]+\\s*=");
        boolean noSecrets = true;
        boolean noHandlers = true;
        for (Path p : sources) {
            String text = Files.readString(p);
            if (!p.getFileName().toString().equals("AllTests.java")) {
                noSecrets &= !secret.matcher(text).find();
            }
            if (p.toString().endsWith(".html")) {
                noHandlers &= !inlineHandler.matcher(text).find();
            }
        }
        check("No passwords, keys or tokens in the source files (" + sources.size() + " files scanned)", noSecrets);
        check("No inline onclick-style handlers in the HTML pages (keeps the security policy strict)", noHandlers);
        long calculatorPages = Files.list(frontend).filter(p -> p.toString().endsWith(".html")).count();
        check("Frontend has 17 pages (home, 15 calculators, history)", calculatorPages == 17);
        long backendFiles = Files.list(root.resolve("backend")).filter(p -> p.toString().endsWith(".java")).count();
        check("Backend has 8 calculator files", backendFiles == 8);
    }

    // ---------- 4. API ----------

    private static void apiTests(int port) throws Exception {
        System.out.println("--- API: normal use ---");
        Reply r = call(port, "GET", "/api/health", null);
        check("Health check answers ok", r.status() == 200 && r.has("\"status\":\"ok\""));
        check("API answers are JSON and are not cached", r.headers().firstValue("Content-Type").orElse("").startsWith("application/json") && r.headers().firstValue("Cache-Control").orElse("").equals("no-store"));
        r = post(port, "/api/age", "{\"dob\":\"2005-03-14\",\"onDate\":\"2026-10-22\",\"today\":\"2026-10-24\"}");
        check("Age: 21 years, 7 months, 8 days, 7892 total days, Monday", r.has("\"years\":21") && r.has("\"months\":7") && r.has("\"days\":8") && r.has("\"totalDays\":7892") && r.has("Monday"));
        r = post(port, "/api/attendance", "{\"total\":45,\"attended\":32,\"target\":75}");
        check("Attendance 32/45: 71.11%, Shortage, attend 7", r.has("71.11") && r.has("Shortage") && r.has("\"classesNeeded\":7"));
        r = post(port, "/api/attendance", "{\"total\":50,\"attended\":40,\"planMiss\":2}");
        check("Attendance 40/50: 80%, 3 safe bunks, plan 76.92%", r.has("\"percentage\":80") && r.has("\"safeBunks\":3") && r.has("76.92"));
        r = post(port, "/api/attendance", "{\"scheduled\":60,\"classesDone\":50,\"attended\":40,\"target\":75}");
        check("Attendance schedule returns every remaining class projection (10 rows)",
                r.body().split("\\\"upcoming\\\":").length == 11 && r.has("\"upcoming\":10") && r.has("\"attend\":81.82") && r.has("\"miss\":72.73"));
        r = post(port, "/api/attendance", "{\"scheduled\":3,\"classesDone\":0,\"attended\":0}");
        check("Attendance with no completed classes is Not started and projects all three", r.has("\"percentage\":null") && r.has("\"status\":\"Not started\"") && r.has("\"upcoming\":3"));
        check("Schedule above 10,000 classes is rejected", post(port, "/api/attendance", "{\"scheduled\":10001,\"classesDone\":50,\"attended\":40}").status() == 400);
        r = post(port, "/api/convert", "{\"category\":\"temperature\",\"from\":\"Celsius\",\"to\":\"Fahrenheit\",\"value\":100}");
        check("100 Celsius = 212 Fahrenheit", r.has("\"result\":212"));
        r = post(port, "/api/date/add", "{\"date\":\"2026-01-31\",\"amount\":1,\"unit\":\"months\",\"operation\":\"add\"}");
        check("31-01-2026 + 1 month = 28-02-2026", r.has("2026-02-28"));
        r = post(port, "/api/date/workdays", "{\"start\":\"2026-10-19\",\"end\":\"2026-10-25\"}");
        check("Workdays 19-10 to 25-10-2026: 5 working, 2 weekend", r.has("\"workdays\":5") && r.has("\"weekendDays\":2"));
        r = post(port, "/api/time/hours-worked", "{\"start\":\"22:00\",\"end\":\"06:00\"}");
        check("Night shift 22:00 to 06:00 = 8 hours", r.has("\"hours\":8") && r.has("\"overnight\":true"));
        r = post(port, "/api/time/countdown", "{\"event\":\"2026-10-25T12:30\",\"now\":\"2026-10-22T10:00:00\"}");
        check("Countdown: 3 days 2 hours 30 minutes", r.has("\"days\":3") && r.has("\"hours\":2") && r.has("\"minutes\":30"));
        check("Average of 85,72,91,78,88 = 82.8", post(port, "/api/average", "{\"numbers\":[85,72,91,78,88]}").has("\"average\":82.8"));
        check("CGPA 7.5 x 9.5 = 71.25", post(port, "/api/cgpa", "{\"cgpa\":7.5}").has("\"percentage\":71.25"));
        check("Percentage change 50 to 75 = 50", post(port, "/api/percentage", "{\"operation\":\"change\",\"a\":50,\"b\":75}").has("\"result\":50"));
        check("Leap years 2020-2030 = 2020, 2024, 2028", call(port, "GET", "/api/leap?from=2020&to=2030", null).has("[2020,2024,2028]"));
        check("Unit list has 6 categories", call(port, "GET", "/api/units", null).body().split("\"category\"").length == 7);
    }

    private static void badRequestTests(int port) throws Exception {
        System.out.println("--- API: wrong and hostile input ---");
        String[] bodies = {"", "{}", "null", "[]", "\"text\"", "123", "{\"a\":", "{\"total\":[1]}", "{\"dob\":123}", "{\"numbers\":{\"a\":1}}", "{\"value\":true}", "{'a':1}", "[".repeat(200)};
        List<String> bad = new ArrayList<>();
        for (String endpoint : POST_ENDPOINTS) {
            for (String body : bodies) {
                int status = post(port, endpoint, body).status();
                if (status != 400) {
                    bad.add(endpoint + " with " + body.substring(0, Math.min(body.length(), 12)) + " gave " + status);
                }
            }
        }
        check("13 endpoints x 13 broken bodies always give a clean 400 (no 200, no 500)" + (bad.isEmpty() ? "" : " " + bad.subList(0, Math.min(3, bad.size()))), bad.isEmpty());

        String[] badLeap = {"", "?year=abc", "?year=0", "?year=99999", "?year=-5", "?from=2030&to=2020", "?from=1&to=20000", "?from=a&to=b"};
        boolean leapOk = true;
        for (String q : badLeap) {
            leapOk &= call(port, "GET", "/api/leap" + q, null).status() == 400;
        }
        check("Leap year endpoint rejects 8 bad queries", leapOk);

        Reply r = post(port, "/api/attendance", "{\"total\":40,\"attended\":50}");
        check("Attended more than total: clear message", r.status() == 400 && r.has("Attended cannot exceed total."));
        check("Target 100 is rejected", post(port, "/api/attendance", "{\"total\":40,\"attended\":30,\"target\":100}").has("Target must be between 1 and 99."));
        check("Total 0 is rejected", post(port, "/api/attendance", "{\"total\":0,\"attended\":0}").status() == 400);
        check("Negative length is rejected", post(port, "/api/convert", "{\"category\":\"LENGTH\",\"from\":\"Meter\",\"to\":\"Foot\",\"value\":-1}").has("Value cannot be negative."));
        check("Future date of birth is rejected", post(port, "/api/age", "{\"dob\":\"2999-01-01\",\"today\":\"2026-10-24\"}").has("Date of birth cannot be in the future."));
        check("31 February is rejected", post(port, "/api/date/difference", "{\"start\":\"2026-02-31\",\"end\":\"2026-03-01\"}").has("Invalid date."));
        check("Full-width digits in a date are rejected", post(port, "/api/age", "{\"dob\":\"\uFF12\uFF10\uFF10\uFF15-03-14\"}").status() == 400);

        // problems found in the audit
        r = post(port, "/api/convert", "{\"category\":\"LENGTH\",\"from\":\"Kilometer\",\"to\":\"Centimeter\",\"value\":1e308}");
        check("Overflowing conversion is a 400 'too large', not a blank result", r.status() == 400 && r.has("too large"));
        check("Overflowing average is a 400", post(port, "/api/average", "{\"numbers\":[1e308,1e308]}").status() == 400);
        check("Overflowing percentage is a 400", post(port, "/api/percentage", "{\"operation\":\"percent-of\",\"a\":1e308,\"b\":1e308}").status() == 400);
        check("Date range of a billion years is a clean 400, not a 500", post(port, "/api/date/workdays", "{\"start\":\"-999999999-01-01\",\"end\":\"+999999999-12-31\"}").status() == 400);
        check("Adding a million years is a clean 400", post(port, "/api/date/add", "{\"date\":\"2026-01-01\",\"amount\":1000000,\"unit\":\"years\",\"operation\":\"add\"}").status() == 400);
        check("Countdown to year 999999 is a clean 400", post(port, "/api/time/countdown", "{\"event\":\"+999999-01-01T00:00\"}").status() == 400);
        r = post(port, "/api/convert", "{\"category\":\"LENGTH\",\"from\":\"" + "x".repeat(500) + "\",\"to\":\"Meter\",\"value\":1}");
        check("A 500-character unit name is not echoed back in full", r.status() == 400 && r.body().length() < 120);
        check("A script tag as a unit name only comes back as JSON text", post(port, "/api/convert", "{\"category\":\"LENGTH\",\"from\":\"<img onerror=alert(1)>\",\"to\":\"Meter\",\"value\":1}").headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    // ---------- 5. security ----------

    private static void securityTests(int port) throws Exception {
        System.out.println("--- Security ---");
        String[] traversal = {"/../Server.java", "/%2e%2e/Server.java", "/..%2fServer.java", "/frontend/../Server.java", "/app.js/../../Server.java", "/backend/AgeCalculator.java", "/Server.java", "/README.md", "/tests/AllTests.java", "//etc/passwd"};
        boolean blocked = true;
        for (String p : traversal) {
            int status = rawStatus(port, p);
            blocked &= status == 404 || status == 400;
        }
        check("10 path-traversal and source-file requests are all blocked", blocked);
        check("Wrong methods get 405", call(port, "GET", "/api/age", null).status() == 405 && call(port, "POST", "/api/health", "{}").status() == 405 && call(port, "PUT", "/api/age", "{}").status() == 405 && call(port, "POST", "/index.html", "x").status() == 405);
        check("Unknown API path gets 404", call(port, "GET", "/api/nothing", null).status() == 404);
        check("A 200 KB request body gets 413", post(port, "/api/average", "x".repeat(200_000)).status() == 413);
        boolean hugeRefused;
        try {
            hugeRefused = post(port, "/api/average", "x".repeat(5_000_000)).status() == 413;
        } catch (java.io.IOException e) {
            hugeRefused = true; // closing the connection on a 5 MB body is also a refusal
        }
        check("A 5 MB request body is refused (413 or closed connection)", hugeRefused);
        Reply page = call(port, "GET", "/index.html", null);
        Reply api = call(port, "GET", "/api/health", null);
        for (Reply r : new Reply[]{page, api}) {
            check("Security headers present on " + (r == page ? "pages" : "API answers"),
                    r.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff")
                            && r.headers().firstValue("X-Frame-Options").orElse("").equals("DENY")
                            && r.headers().firstValue("Referrer-Policy").orElse("").equals("no-referrer")
                            && r.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'")
                            && r.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
        }
        Reply withOrigin = callWith(port, "GET", "/api/health", null, new String[]{"Origin", "https://evil.example"});
        check("CORS is off by default (no Access-Control-Allow-Origin)", withOrigin.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    // ---------- 6. pages and links ----------

    private static void pageTests(int port, Path frontend) throws Exception {
        System.out.println("--- Pages and links ---");
        List<String> missing = new ArrayList<>();
        List<String> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(frontend)) {
            list.map(p -> p.getFileName().toString()).sorted().forEach(files::add);
        }
        int pagesChecked = 0;
        for (String file : files) {
            Reply r = call(port, "GET", "/" + file, null);
            if (r.status() != 200) {
                missing.add(file);
            }
            if (file.endsWith(".html")) {
                pagesChecked++;
                String text = Files.readString(frontend.resolve(file));
                Matcher m = Pattern.compile("href=\"([a-z0-9-]+\\.(?:html|css))\"").matcher(text);
                while (m.find()) {
                    if (!Files.exists(frontend.resolve(m.group(1)))) {
                        missing.add(file + " -> " + m.group(1));
                    }
                }
            }
        }
        check("All " + files.size() + " frontend files are served and no page links to a missing file" + (missing.isEmpty() ? "" : " " + missing), missing.isEmpty());
        String appJs = Files.readString(frontend.resolve("app.js"));
        Matcher menu = Pattern.compile("\\[\"([a-z0-9-]+\\.html)\"").matcher(appJs);
        int menuLinks = 0;
        boolean menuOk = true;
        while (menu.find()) {
            menuLinks++;
            menuOk &= Files.exists(frontend.resolve(menu.group(1)));
        }
        check("Every one of the " + menuLinks + " menu entries in app.js points to a real page", menuOk && menuLinks == 16);
        boolean structureOk = true;
        for (String file : files) {
            if (file.endsWith(".html") && !file.equals("index.html") && !file.equals("history.html")) {
                String text = Files.readString(frontend.resolve(file));
                structureOk &= text.contains("id=\"form\"") && text.contains("src=\"app.js\"") && text.contains("Calc.init");
            }
        }
        check("Every calculator page has its form, app.js and Calc.init", structureOk);
        boolean footersOk = true;
        for (String file : files) {
            if (file.endsWith(".html")) {
                footersOk &= Files.readString(frontend.resolve(file)).contains("<footer>developed by Nihal RAJ \u26a1</footer>");
            }
        }
        check("Every page uses the requested footer", footersOk);
        check("Projection table rows are included in Copy result", Files.readString(frontend.resolve("app.js")).contains("res.table.rows.map((row) => row.join(\"\\t\"))"));
        check("Home page links to all 15 calculators", Pattern.compile("class=\"card-link\"").matcher(Files.readString(frontend.resolve("index.html"))).results().count() == 15);
        check("Pages are sent as text/html and scripts as JavaScript", call(port, "GET", "/age.html", null).headers().firstValue("Content-Type").orElse("").startsWith("text/html")
                && call(port, "GET", "/app.js", null).headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
        check("(" + pagesChecked + " HTML pages checked)", pagesChecked == 17);
    }

    // ---------- 7. rate limit and CORS ----------

    private static void rateLimitTests(Path frontend) throws Exception {
        System.out.println("--- Rate limit ---");
        HttpServer limited = Server.start(0, frontend, 5, false, null);
        try {
            int port = limited.getAddress().getPort();
            int ok = 0;
            for (int i = 0; i < 5; i++) {
                ok += call(port, "GET", "/api/health", null).status() == 200 ? 1 : 0;
            }
            Reply sixth = call(port, "GET", "/api/health", null);
            check("5 requests pass, the 6th gets 429 with a message", ok == 5 && sixth.status() == 429 && sixth.has("Too many requests"));
            check("Pages are not rate limited", call(port, "GET", "/index.html", null).status() == 200);
        } finally {
            limited.stop(0);
        }
        HttpServer proxied = Server.start(0, frontend, 2, true, null);
        try {
            int port = proxied.getAddress().getPort();
            String[] first = {"X-Forwarded-For", "9.9.9.9, 1.1.1.1"};
            call(port, "GET", "/api/health", null);
            callWith(port, "GET", "/api/health", null, first);
            callWith(port, "GET", "/api/health", null, first);
            int third = callWith(port, "GET", "/api/health", null, first).status();
            int other = callWith(port, "GET", "/api/health", null, new String[]{"X-Forwarded-For", "9.9.9.9, 2.2.2.2"}).status();
            check("Behind a proxy each client address gets its own limit (and a faked first address does not help)", third == 429 && other == 200);
        } finally {
            proxied.stop(0);
        }
    }

    private static void corsTests(Path frontend) throws Exception {
        System.out.println("--- CORS when switched on ---");
        HttpServer open = Server.start(0, frontend, 0, false, "https://my-frontend.example");
        try {
            int port = open.getAddress().getPort();
            Reply r = call(port, "GET", "/api/health", null);
            check("Only the configured origin is allowed", r.headers().firstValue("Access-Control-Allow-Origin").orElse("").equals("https://my-frontend.example"));
            check("Preflight request gets 204", call(port, "OPTIONS", "/api/age", null).status() == 204);
        } finally {
            open.stop(0);
        }
    }
}

