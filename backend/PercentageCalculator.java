/** Percentage operations. Run it alone with:  java -cp out PercentageCalculator */
public class PercentageCalculator {

    /** X% of Y. */
    public double percentOf(double percent, double number) {
        return Validator.finiteResult(number * percent / 100.0);
    }

    /** X is what percent of Y. */
    public double whatPercent(double part, double whole) {
        if (whole == 0) {
            throw Validator.fail("The whole cannot be zero.");
        }
        return Validator.finiteResult(part / whole * 100.0);
    }

    /** Find the whole when a known part represents a given percentage. */
    public double findWhole(double part, double percent) {
        if (percent == 0) {
            throw Validator.fail("The percentage cannot be zero.");
        }
        return Validator.finiteResult(part * 100.0 / percent);
    }

    public double increase(double value, double percent) {
        return Validator.finiteResult(value * (1 + percent / 100.0));
    }

    public double decrease(double value, double percent) {
        if (percent > 100) {
            throw Validator.fail("A decrease cannot be more than 100%.");
        }
        return Validator.finiteResult(value * (1 - percent / 100.0));
    }

    /** Percentage change from an old value to a new value. */
    public double percentChange(double oldValue, double newValue) {
        if (oldValue == 0) {
            throw Validator.fail("The old value cannot be zero.");
        }
        return Validator.finiteResult((newValue - oldValue) / Math.abs(oldValue) * 100.0);
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        PercentageCalculator calc = new PercentageCalculator();
        System.out.println("=== Percentage Calculator (text version) ===");
        while (true) {
            System.out.println();
            System.out.println("  1  X% of Y            4  Decrease Y by X%");
            System.out.println("  2  X is what % of Y   5  % change from X to Y");
            System.out.println("  3  Increase Y by X%   6  Find whole from part and %");
            System.out.println("  0  Exit");
            String choice = Validator.ask("Choose an option");
            if (choice.equals("0")) {
                System.out.println("Bye!");
                return;
            }
            try {
                int op = Validator.parseWholeNumber(choice);
                if (op < 1 || op > 6) {
                    throw Validator.fail("Choose a number from 0 to 6.");
                }
                double x = Validator.parseNumber(Validator.ask("X"));
                double y = Validator.parseNumber(Validator.ask("Y"));
                double result = switch (op) {
                    case 1 -> calc.percentOf(x, y);
                    case 2 -> calc.whatPercent(x, y);
                    case 3 -> calc.increase(y, x);
                    case 4 -> calc.decrease(y, x);
                    case 5 -> calc.percentChange(x, y);
                    default -> calc.findWhole(x, y);
                };
                System.out.println();
                System.out.println("  Result : " + Validator.plain(Validator.round(result, 4)) + (op == 1 || op == 3 || op == 4 ? "" : "%"));
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
