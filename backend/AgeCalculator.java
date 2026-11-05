import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/** Age calculator and age difference. Run it alone with:  java -cp out AgeCalculator */
public class AgeCalculator {

    public record AgeResult(int years, int months, int days) {
        @Override
        public String toString() {
            return years + " years, " + months + " months, " + days + " days";
        }
    }

    /** olderPerson: 0 = same birth date, 1 = first person is older, 2 = second person is older. */
    public record AgeDifference(int olderPerson, AgeResult gap, long totalDays) {
    }

    public AgeResult calculateAge(LocalDate dob, LocalDate on) {
        if (on.isBefore(dob)) {
            throw Validator.fail("Calculation date cannot be before the date of birth.");
        }
        Period p = Period.between(dob, on);
        return new AgeResult(p.getYears(), p.getMonths(), p.getDays());
    }

    public long totalDays(LocalDate dob, LocalDate on) {
        return ChronoUnit.DAYS.between(dob, on);
    }

    public long totalWeeks(LocalDate dob, LocalDate on) {
        return totalDays(dob, on) / 7;
    }

    public long totalHours(LocalDate dob, LocalDate on) {
        return totalDays(dob, on) * 24;
    }

    /** The next birthday on or after {@code from}. A 29 February birthday falls on 28 February in non-leap years. */
    public LocalDate nextBirthday(LocalDate dob, LocalDate from) {
        LocalDate candidate = dob.withYear(from.getYear());
        if (candidate.isBefore(from)) {
            candidate = dob.withYear(from.getYear() + 1);
        }
        return candidate;
    }

    public long daysUntilBirthday(LocalDate dob, LocalDate from) {
        return ChronoUnit.DAYS.between(from, nextBirthday(dob, from));
    }

    public DayOfWeek birthWeekday(LocalDate dob) {
        return dob.getDayOfWeek();
    }

    public AgeDifference ageDifference(LocalDate dobA, LocalDate dobB) {
        if (dobA.equals(dobB)) {
            return new AgeDifference(0, new AgeResult(0, 0, 0), 0);
        }
        boolean aIsOlder = dobA.isBefore(dobB);
        LocalDate older = aIsOlder ? dobA : dobB;
        LocalDate younger = aIsOlder ? dobB : dobA;
        Period p = Period.between(older, younger);
        return new AgeDifference(aIsOlder ? 1 : 2, new AgeResult(p.getYears(), p.getMonths(), p.getDays()),
                ChronoUnit.DAYS.between(older, younger));
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        AgeCalculator calc = new AgeCalculator();
        System.out.println("=== Age Calculator (text version) ===");
        while (true) {
            System.out.println();
            System.out.println("  1  Age calculator");
            System.out.println("  2  Age difference");
            System.out.println("  0  Exit");
            String choice = Validator.ask("Choose an option");
            try {
                switch (choice) {
                    case "0" -> {
                        System.out.println("Bye!");
                        return;
                    }
                    case "1" -> {
                        LocalDate dob = Validator.askDate("Date of birth");
                        Validator.requireNotFuture(dob, LocalDate.now(), "Date of birth");
                        LocalDate on = Validator.askDateOrToday("Calculate age on");
                        System.out.println();
                        System.out.println("  Age on " + Validator.show(on) + " : " + calc.calculateAge(dob, on));
                        System.out.println("  Total days        : " + Validator.grouped(calc.totalDays(dob, on)));
                        System.out.println("  Total weeks       : " + Validator.grouped(calc.totalWeeks(dob, on)));
                        System.out.println("  Total hours       : " + Validator.grouped(calc.totalHours(dob, on)));
                        System.out.println("  Born on a         : " + Validator.weekday(calc.birthWeekday(dob)));
                        long left = calc.daysUntilBirthday(dob, on);
                        System.out.println("  Next birthday     : " + Validator.show(calc.nextBirthday(dob, on))
                                + (left == 0 ? " (today!)" : " (in " + left + " days)"));
                    }
                    case "2" -> {
                        AgeDifference d = calc.ageDifference(Validator.askDate("Person A date of birth"), Validator.askDate("Person B date of birth"));
                        System.out.println();
                        if (d.olderPerson() == 0) {
                            System.out.println("  Both were born on the same day.");
                        } else {
                            System.out.println("  Person " + (d.olderPerson() == 1 ? "A" : "B") + " is older by " + d.gap()
                                    + " (" + Validator.grouped(d.totalDays()) + " days).");
                        }
                    }
                    default -> System.out.println("  Please choose 0, 1 or 2.");
                }
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
