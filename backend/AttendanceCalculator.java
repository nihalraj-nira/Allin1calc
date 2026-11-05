import java.util.Locale;

/** Attendance percentage, classes needed and safe bunks. Run it alone with:  java -cp out AttendanceCalculator */
public class AttendanceCalculator {
    private static final double EPS = 1e-9;

    public void validate(int attended, int total) {
        if (attended < 0 || total < 0) {
            throw Validator.fail("Value cannot be negative.");
        }
        if (total == 0) {
            throw Validator.fail("Total classes must be greater than zero.");
        }
        if (attended > total) {
            throw Validator.fail("Attended cannot exceed total.");
        }
    }

    public void validateTarget(double target) {
        if (!(target >= 1 && target <= 99)) {
            throw Validator.fail("Target must be between 1 and 99.");
        }
    }

    public double percentage(int attended, int total) {
        validate(attended, total);
        return attended * 100.0 / total;
    }

    /** Classes to attend in a row (with no absences) to reach the target; 0 if already met. */
    public int classesNeeded(int attended, int total, double target) {
        validateTarget(target);
        if (percentage(attended, total) >= target) {
            return 0;
        }
        double n = (target * total - 100.0 * attended) / (100.0 - target);
        return (int) Math.ceil(n - EPS);
    }

    /** Classes that can still be missed while staying at or above the target; 0 if below the target. */
    public int safeBunks(int attended, int total, double target) {
        validateTarget(target);
        if (percentage(attended, total) < target) {
            return 0;
        }
        double m = attended * 100.0 / target - total;
        return Math.max(0, (int) Math.floor(m + EPS));
    }

    public double percentageAfterMissing(int attended, int total, int missed) {
        validate(attended, total);
        requireNonNegative(missed);
        return attended * 100.0 / (total + missed);
    }

    public double percentageAfterAttending(int attended, int total, int extra) {
        validate(attended, total);
        requireNonNegative(extra);
        return (attended + extra) * 100.0 / (total + extra);
    }

    /** Safe when at least 3 points above the target, borderline when above but closer, shortage when below. */
    public String status(double percentage, double target) {
        if (percentage < target) {
            return "Shortage";
        }
        return percentage < target + 3 ? "Borderline" : "Safe";
    }

    private void requireNonNegative(int n) {
        if (n < 0) {
            throw Validator.fail("Value cannot be negative.");
        }
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        AttendanceCalculator calc = new AttendanceCalculator();
        System.out.println("=== Attendance Calculator (text version) ===");
        while (true) {
            System.out.println();
            System.out.println("  1  Attendance calculator");
            System.out.println("  2  Bunk planner");
            System.out.println("  0  Exit");
            String choice = Validator.ask("Choose an option");
            try {
                if (choice.equals("0")) {
                    System.out.println("Bye!");
                    return;
                }
                if (!choice.equals("1") && !choice.equals("2")) {
                    System.out.println("  Please choose 0, 1 or 2.");
                    continue;
                }
                int total = Validator.parseWholeNumber(Validator.ask("Total classes"));
                int attended = Validator.parseWholeNumber(Validator.ask("Classes attended"));
                String t = Validator.ask("Target percentage (press Enter for 75)");
                double target = t.isEmpty() ? 75 : Validator.parseNumber(t);
                double pct = calc.percentage(attended, total);
                int needed = calc.classesNeeded(attended, total, target);
                int safe = calc.safeBunks(attended, total, target);
                System.out.println();
                System.out.printf(Locale.US, "  Current attendance : %.2f%%  (%d of %d)%n", pct, attended, total);
                System.out.println("  Status             : " + calc.status(pct, target) + " (target " + Validator.plain(target) + "%)");
                if (needed > 0) {
                    System.out.printf(Locale.US, "  To reach target    : attend the next %d classes in a row (then %.2f%%)%n",
                            needed, calc.percentageAfterAttending(attended, total, needed));
                } else {
                    System.out.println("  Safe bunks         : " + safe + (safe == 1 ? " class" : " classes") + " can still be missed");
                }
                if (choice.equals("2")) {
                    int plan = Validator.parseNonNegativeWhole(Validator.ask("How many classes do you plan to miss"));
                    double after = calc.percentageAfterMissing(attended, total, plan);
                    System.out.println();
                    System.out.println("  If you miss the next classes:");
                    for (int k = 1; k <= 5; k++) {
                        System.out.printf(Locale.US, "    miss %d -> %.2f%%%n", k, calc.percentageAfterMissing(attended, total, k));
                    }
                    System.out.printf(Locale.US, "  Your plan (miss %d) -> %.2f%%  %s%n", plan, after, after >= target ? "(still OK)" : "(below target!)");
                }
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
