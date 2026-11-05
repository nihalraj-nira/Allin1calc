/** CGPA to percentage. Run it alone with:  java -cp out CgpaCalculator */
public class CgpaCalculator {

    /** Percentage = CGPA x multiplier. Different colleges use different multipliers, so it can be changed. */
    public double toPercentage(double cgpa, double multiplier) {
        if (cgpa < 0 || cgpa > 10) {
            throw Validator.fail("CGPA must be between 0 and 10.");
        }
        if (multiplier <= 0) {
            throw Validator.fail("Multiplier must be greater than zero.");
        }
        double result = cgpa * multiplier;
        if (result > 100) {
            throw Validator.fail("The result is above 100%. Check the multiplier.");
        }
        return result;
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        CgpaCalculator calc = new CgpaCalculator();
        System.out.println("=== CGPA to Percentage (text version) ===");
        while (true) {
            System.out.println();
            String c = Validator.ask("CGPA (type 0 to exit)");
            if (c.equals("0")) {
                System.out.println("Bye!");
                return;
            }
            try {
                double cgpa = Validator.parseNumber(c);
                String m = Validator.ask("Multiplier (press Enter for 9.5)");
                double mult = m.isEmpty() ? 9.5 : Validator.parseNumber(m);
                System.out.println();
                System.out.println("  Percentage : " + Validator.plain(Validator.round(calc.toPercentage(cgpa, mult), 2)) + "%");
                System.out.println("  (Check your own college's official formula.)");
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
