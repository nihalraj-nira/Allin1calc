import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helper used by every calculator: checks input, gives friendly error messages,
 * and has a few small helpers for the text (console) versions.
 */
public final class Validator {

    /** Thrown when the user enters something a calculator cannot work with. The message is safe to show on screen. */
    public static class InvalidInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidInputException(String message) {
            super(message);
        }
    }

    public static InvalidInputException fail(String message) {
        return new InvalidInputException(message);
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,9}");
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,3}):(\\d{2})");
    private static final int MAX_NUMBER_LENGTH = 40;
    private static final int MIN_YEAR = 1;
    private static final int MAX_YEAR = 9999;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Scanner IN = new Scanner(System.in);

    private Validator() {
    }

    // ---------- Checking input ----------

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    public static double parseNumber(String text) {
        String t = clean(text);
        if (t.length() > MAX_NUMBER_LENGTH || !NUMBER.matcher(t).matches()) {
            throw fail("Enter a valid number.");
        }
        double value = Double.parseDouble(t);
        if (!Double.isFinite(value)) {
            throw fail("Enter a valid number.");
        }
        return value;
    }

    public static int parseWholeNumber(String text) {
        String t = clean(text);
        if (!WHOLE.matcher(t).matches()) {
            throw fail("Enter a valid whole number.");
        }
        return Integer.parseInt(t);
    }

    public static int parseNonNegativeWhole(String text) {
        int value = parseWholeNumber(text);
        if (value < 0) {
            throw fail("Value cannot be negative.");
        }
        return value;
    }

    /** Numbers separated by commas, semicolons or spaces. */
    public static List<Double> parseNumberList(String text) {
        String t = clean(text);
        if (t.isEmpty()) {
            throw fail("Enter at least one number.");
        }
        List<Double> numbers = new ArrayList<>();
        for (String part : t.split("[,;\\s]+")) {
            if (!part.isEmpty()) {
                numbers.add(parseNumber(part));
            }
        }
        if (numbers.isEmpty()) {
            throw fail("Enter at least one number.");
        }
        return numbers;
    }

    /** HH:MM where hours may be more than 23 (a duration such as 27:15). */
    public static Duration parseDuration(String text) {
        Matcher m = CLOCK.matcher(clean(text));
        if (!m.matches()) {
            throw fail("Enter time as HH:MM (for example 02:45).");
        }
        int minutes = Integer.parseInt(m.group(2));
        if (minutes > 59) {
            throw fail("Minutes must be between 00 and 59.");
        }
        return Duration.ofHours(Integer.parseInt(m.group(1))).plusMinutes(minutes);
    }

    /** A 24-hour clock time such as 09:30. */
    public static LocalTime parseTime(String text) {
        Duration d = parseDuration(text);
        if (d.toHours() > 23) {
            throw fail("Hours must be between 00 and 23.");
        }
        return LocalTime.of((int) d.toHours(), d.toMinutesPart());
    }

    /** A date written as dd-mm-yyyy or yyyy-mm-dd. */
    public static LocalDate parseDate(String text) {
        String t = clean(text);
        for (DateTimeFormatter f : new DateTimeFormatter[]{DMY, DateTimeFormatter.ISO_LOCAL_DATE}) {
            try {
                return requireSupportedDate(LocalDate.parse(t, f));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw fail("Invalid date. Use dd-mm-yyyy, for example 14-03-2005.");
    }

    /** Every calculator works with years 1 to 9999. Anything else is rejected with a clear message. */
    public static LocalDate requireSupportedDate(LocalDate date) {
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw fail("Dates must be between the years " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
        return date;
    }

    /** Overflowing calculations give Infinity, which must never reach the user as a silent blank. */
    public static double finiteResult(double value) {
        if (!Double.isFinite(value)) {
            throw fail("The result is too large to show.");
        }
        return value;
    }

    /** Keeps user text short when it is repeated inside an error message. */
    public static String shorten(String text, int max) {
        String t = text == null ? "" : text;
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    public static void requireNotFuture(LocalDate date, LocalDate today, String label) {
        if (date.isAfter(today)) {
            throw fail(label + " cannot be in the future.");
        }
    }

    // ---------- Small helpers for the text (console) versions ----------

    public static String ask(String prompt) {
        System.out.print("  " + prompt + ": ");
        if (!IN.hasNextLine()) {
            System.out.println();
            System.exit(0);
        }
        return IN.nextLine().trim();
    }

    public static LocalDate askDate(String label) {
        return parseDate(ask(label + " (dd-mm-yyyy)"));
    }

    public static LocalDate askDateOrToday(String label) {
        String t = ask(label + " (dd-mm-yyyy, press Enter for today)");
        return t.isEmpty() ? LocalDate.now() : parseDate(t);
    }

    public static int pick(String prompt, int max) {
        int n = parseWholeNumber(ask(prompt));
        if (n < 1 || n > max) {
            throw fail("Choose a number from 1 to " + max + ".");
        }
        return n;
    }

    // ---------- Formatting ----------

    public static String show(LocalDate d) {
        return String.format("%02d-%02d-%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    public static String weekday(DayOfWeek d) {
        return d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public static double round(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** 8.0 becomes "8", 71.1111 stays "71.1111". */
    public static String plain(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** 1234567 becomes "1,234,567". */
    public static String grouped(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
