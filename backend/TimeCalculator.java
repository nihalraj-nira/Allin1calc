import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

/** Time calculator, hours worked and countdown. Run it alone with:  java -cp out TimeCalculator */
public class TimeCalculator {

    public Duration add(Duration a, Duration b) {
        return a.plus(b);
    }

    public Duration subtract(Duration a, Duration b) {
        if (b.compareTo(a) > 0) {
            throw Validator.fail("The second time is larger, so the result would be negative.");
        }
        return a.minus(b);
    }

    /** Time worked between two clock times minus a break. If the end is before the start, an overnight shift is assumed. */
    public Duration hoursWorked(LocalTime start, LocalTime end, Duration breakTime) {
        Duration span = Duration.between(start, end);
        if (span.isNegative()) {
            span = span.plusHours(24);
        }
        if (breakTime.compareTo(span) > 0) {
            throw Validator.fail("Break is longer than the time worked.");
        }
        return span.minus(breakTime);
    }

    public boolean isOvernight(LocalTime start, LocalTime end) {
        return end.isBefore(start);
    }

    /** Time left until the event; negative if the event is already in the past. */
    public Duration countdown(LocalDateTime event, LocalDateTime now) {
        return Duration.between(now, event);
    }

    public static String formatHm(Duration d) {
        return String.format("%02d:%02d", d.toHours(), d.toMinutesPart());
    }

    public static double decimalHours(Duration d) {
        return d.toMinutes() / 60.0;
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        TimeCalculator calc = new TimeCalculator();
        System.out.println("=== Time Calculator (text version) ===");
        while (true) {
            System.out.println();
            System.out.println("  1  Time calculator (add / subtract)");
            System.out.println("  2  Hours worked");
            System.out.println("  3  Countdown");
            System.out.println("  0  Exit");
            String choice = Validator.ask("Choose an option");
            try {
                switch (choice) {
                    case "0" -> {
                        System.out.println("Bye!");
                        return;
                    }
                    case "1" -> {
                        Duration a = Validator.parseDuration(Validator.ask("First time (HH:MM)"));
                        Duration b = Validator.parseDuration(Validator.ask("Second time (HH:MM)"));
                        String op = Validator.ask("Add or subtract").toLowerCase(Locale.ROOT);
                        if (!op.startsWith("a") && !op.startsWith("s")) {
                            throw Validator.fail("Type add or subtract.");
                        }
                        Duration r = op.startsWith("a") ? calc.add(a, b) : calc.subtract(a, b);
                        System.out.println();
                        System.out.println("  Result : " + formatHm(r) + " (" + r.toHours() + " hours " + r.toMinutesPart() + " minutes)");
                    }
                    case "2" -> {
                        LocalTime start = Validator.parseTime(Validator.ask("Start time (HH:MM, 24-hour)"));
                        LocalTime end = Validator.parseTime(Validator.ask("End time (HH:MM, 24-hour)"));
                        String brk = Validator.ask("Break (HH:MM, press Enter for none)");
                        Duration worked = calc.hoursWorked(start, end, brk.isEmpty() ? Duration.ZERO : Validator.parseDuration(brk));
                        System.out.println();
                        System.out.println("  Total working time : " + worked.toHours() + " hours " + worked.toMinutesPart() + " minutes ("
                                + Validator.round(decimalHours(worked), 2) + " hours)");
                        if (calc.isOvernight(start, end)) {
                            System.out.println("  (End time is before start time, so an overnight shift was assumed.)");
                        }
                    }
                    case "3" -> {
                        LocalDate date = Validator.askDate("Event date");
                        LocalTime time = Validator.parseTime(Validator.ask("Event time (HH:MM, 24-hour)"));
                        Duration left = calc.countdown(LocalDateTime.of(date, time), LocalDateTime.now());
                        if (left.isNegative() || left.isZero()) {
                            throw Validator.fail("That date and time has already passed.");
                        }
                        System.out.println();
                        System.out.println("  Time left : " + left.toDays() + " days, " + left.toHoursPart() + " hours, "
                                + left.toMinutesPart() + " minutes, " + left.toSecondsPart() + " seconds");
                    }
                    default -> System.out.println("  Please choose 0, 1, 2 or 3.");
                }
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
