import java.util.Locale;

/** Attendance percentage, classes needed and safe bunks. Run it alone with:  java -cp out AttendanceCalculator */
public class AttendanceCalculator {
    private static final double EPS = 1e-9;
    private static final int MAX_SCHEDULED_CLASSES = 10_000;

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

    /** Validate a semester schedule, completed classes, and attended classes. */
    public void validateSchedule(int scheduled, int classesDone, int attended) {
        if (scheduled < 1) {
            throw Validator.fail("Total scheduled classes must be greater than zero.");
        }
        if (scheduled > MAX_SCHEDULED_CLASSES) {
            throw Validator.fail("Total scheduled classes cannot exceed 10,000.");
        }
        if (classesDone < 0 || attended < 0) {
            throw Validator.fail("Value cannot be negative.");
        }
        if (classesDone > scheduled) {
            throw Validator.fail("Classes done cannot exceed total scheduled classes.");
        }
        if (attended > classesDone) {
            throw Validator.fail("Attended cannot exceed classes done.");
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

    /** Project the percentage after future classes are all attended or all missed. */
    public double projectedPercentage(int attended, int classesDone, int futureClasses, boolean attendFuture) {
        if (futureClasses < 1) {
            throw Validator.fail("Upcoming classes must be greater than zero.");
        }
        long scheduled = (long) classesDone + futureClasses;
        if (scheduled > Integer.MAX_VALUE) {
            throw Validator.fail("Total scheduled classes is too large.");
        }
        validateSchedule((int) scheduled, classesDone, attended);
        long futureAttended = attendFuture ? futureClasses : 0;
        return (attended + futureAttended) * 100.0 / (classesDone + (double) futureClasses);
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
                int scheduled;
                int total;
                int attended;
                if (choice.equals("1")) {
                    scheduled = Validator.parseWholeNumber(Validator.ask("Total classes scheduled"));
                    total = Validator.parseNonNegativeWhole(Validator.ask("Classes done so far"));
                    attended = Validator.parseNonNegativeWhole(Validator.ask("Classes attended"));
                    calc.validateSchedule(scheduled, total, attended);
                } else {
                    total = Validator.parseWholeNumber(Validator.ask("Classes completed so far"));
                    attended = Validator.parseNonNegativeWhole(Validator.ask("Classes attended"));
                    scheduled = total;
                    calc.validate(attended, total);
                }
                String t = Validator.ask("Target percentage (press Enter for 75)");
                double target = t.isEmpty() ? 75 : Validator.parseNumber(t);
                calc.validateTarget(target);
                int remaining = scheduled - total;
                boolean started = total > 0;
                double pct = started ? calc.percentage(attended, total) : 0;
                int needed = started ? calc.classesNeeded(attended, total, target) : 0;
                int safe = started ? calc.safeBunks(attended, total, target) : 0;
                if (choice.equals("1")) {
                    safe = Math.min(safe, remaining);
                }
                System.out.println();
                if (started) {
                    System.out.printf(Locale.US, "  Current attendance : %.2f%%  (%d of %d classes done)%n", pct, attended, total);
                    System.out.println("  Status             : " + calc.status(pct, target) + " (target " + Validator.plain(target) + "%)");
                } else {
                    System.out.println("  Current attendance : Not started (no classes completed yet)");
                    System.out.println("  Status             : Not started");
                }
                if (started && needed > 0) {
                    double after = calc.percentageAfterAttending(attended, total, needed);
                    if (choice.equals("1") && needed > remaining) {
                        System.out.printf("  To reach target    : attend %d classes in a row; only %d remain on this schedule%n", needed, remaining);
                    } else {
                        System.out.printf(Locale.US, "  To reach target    : attend the next %d classes in a row (then %.2f%%)%n", needed, after);
                    }
                } else if (started) {
                    System.out.println("  Safe bunks         : " + safe + (safe == 1 ? " class" : " classes") + " can still be missed");
                }
                if (choice.equals("1")) {
                    System.out.printf("  Schedule           : %d scheduled, %d done, %d remaining%n", scheduled, total, remaining);
                    for (int k = 1; k <= remaining; k++) {
                        System.out.printf(Locale.US, "  Next %d class(es)    : attend all %.2f%%  |  miss all %.2f%%%n",
                                k, calc.projectedPercentage(attended, total, k, true),
                                calc.projectedPercentage(attended, total, k, false));
                    }
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

