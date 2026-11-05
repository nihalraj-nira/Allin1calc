import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Date difference, add/subtract, workdays and leap years. Run it alone with:  java -cp out DateCalculator */
public class DateCalculator {

    public enum Unit {
        DAYS, WEEKS, MONTHS, YEARS;

        public static Unit parse(String text) {
            String t = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
            if (!t.endsWith("S")) {
                t = t + "S";
            }
            try {
                return valueOf(t);
            } catch (IllegalArgumentException e) {
                throw Validator.fail("Unit must be days, weeks, months or years.");
            }
        }
    }

    /** Difference between two dates, whichever comes first. */
    public Period difference(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? Period.between(b, a) : Period.between(a, b);
    }

    public long daysBetween(LocalDate a, LocalDate b) {
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }

    public LocalDate add(LocalDate date, long amount, Unit unit) {
        LocalDate result;
        try {
            result = switch (unit) {
                case DAYS -> date.plusDays(amount);
                case WEEKS -> date.plusWeeks(amount);
                case MONTHS -> date.plusMonths(amount);
                case YEARS -> date.plusYears(amount);
            };
        } catch (DateTimeException | ArithmeticException e) {
            throw Validator.fail("The resulting date is outside the supported range.");
        }
        if (result.getYear() < 1 || result.getYear() > 9999) {
            throw Validator.fail("The resulting date is outside the supported range (years 1 to 9999).");
        }
        return result;
    }

    public LocalDate subtract(LocalDate date, long amount, Unit unit) {
        return add(date, -amount, unit);
    }

    /** Working days (Monday to Friday) from start to end, both included. Public holidays are not counted. */
    public long workdaysBetween(LocalDate a, LocalDate b) {
        LocalDate start = a.isAfter(b) ? b : a;
        long totalDays = inclusiveDays(a, b);
        // every full week has exactly 5 working days; only the leftover days need checking
        long workdays = (totalDays / 7) * 5;
        LocalDate day = start.plusDays((totalDays / 7) * 7);
        for (long i = 0; i < totalDays % 7; i++) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workdays++;
            }
            day = day.plusDays(1);
        }
        return workdays;
    }

    /** Number of days from start to end, both included. */
    public long inclusiveDays(LocalDate a, LocalDate b) {
        return daysBetween(a, b) + 1;
    }

    public long weekendDaysBetween(LocalDate a, LocalDate b) {
        return inclusiveDays(a, b) - workdaysBetween(a, b);
    }

    public boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public List<Integer> leapYearsBetween(int from, int to) {
        if (from > to) {
            throw Validator.fail("Start year must not be after the end year.");
        }
        if (to - from > 10000) {
            throw Validator.fail("Please use a range of at most 10,000 years.");
        }
        List<Integer> years = new ArrayList<>();
        for (int y = from; y <= to; y++) {
            if (isLeapYear(y)) {
                years.add(y);
            }
        }
        return years;
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        DateCalculator calc = new DateCalculator();
        System.out.println("=== Date Calculator (text version) ===");
        while (true) {
            System.out.println();
            System.out.println("  1  Date difference");
            System.out.println("  2  Add / subtract date");
            System.out.println("  3  Workdays");
            System.out.println("  4  Leap year");
            System.out.println("  0  Exit");
            String choice = Validator.ask("Choose an option");
            try {
                switch (choice) {
                    case "0" -> {
                        System.out.println("Bye!");
                        return;
                    }
                    case "1" -> {
                        LocalDate a = Validator.askDate("Start date");
                        LocalDate b = Validator.askDate("End date");
                        Period p = calc.difference(a, b);
                        long days = calc.daysBetween(a, b);
                        System.out.println();
                        System.out.println("  Difference : " + p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays() + " days");
                        System.out.println("  Total days : " + Validator.grouped(days) + " (" + days / 7 + " weeks and " + days % 7 + " days)");
                    }
                    case "2" -> {
                        LocalDate date = Validator.askDate("Date");
                        int amount = Validator.parseNonNegativeWhole(Validator.ask("Number"));
                        Unit unit = Unit.parse(Validator.ask("Unit (days / weeks / months / years)"));
                        String op = Validator.ask("Add or subtract").toLowerCase(Locale.ROOT);
                        if (!op.startsWith("a") && !op.startsWith("s")) {
                            throw Validator.fail("Type add or subtract.");
                        }
                        LocalDate result = op.startsWith("a") ? calc.add(date, amount, unit) : calc.subtract(date, amount, unit);
                        System.out.println();
                        System.out.println("  Result : " + Validator.show(result) + " (" + Validator.weekday(result.getDayOfWeek()) + ")");
                    }
                    case "3" -> {
                        LocalDate a = Validator.askDate("Start date");
                        LocalDate b = Validator.askDate("End date");
                        System.out.println();
                        System.out.println("  Working days (Mon-Fri) : " + calc.workdaysBetween(a, b));
                        System.out.println("  Weekend days           : " + calc.weekendDaysBetween(a, b));
                        System.out.println("  Total days (inclusive) : " + calc.inclusiveDays(a, b));
                        System.out.println("  (Public holidays are not counted.)");
                    }
                    case "4" -> {
                        String[] parts = Validator.ask("Enter a year, or two years for a range (example: 2020 2030)").split("\\s+");
                        System.out.println();
                        if (parts.length == 1) {
                            int y = Validator.parseWholeNumber(parts[0]);
                            System.out.println("  " + y + (calc.isLeapYear(y) ? " is a leap year." : " is not a leap year."));
                        } else if (parts.length == 2) {
                            List<Integer> years = calc.leapYearsBetween(Validator.parseWholeNumber(parts[0]), Validator.parseWholeNumber(parts[1]));
                            System.out.println("  Leap years (" + years.size() + "): " + years);
                        } else {
                            throw Validator.fail("Enter one year or two years.");
                        }
                    }
                    default -> System.out.println("  Please choose a number from the menu.");
                }
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
