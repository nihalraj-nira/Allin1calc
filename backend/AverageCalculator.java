import java.util.List;

/** Count, sum, average, smallest and largest of a list of numbers. Run it alone with:  java -cp out AverageCalculator */
public class AverageCalculator {

    public record Stats(int count, double sum, double average, double min, double max) {
    }

    public Stats stats(List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw Validator.fail("Enter at least one number.");
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double n : numbers) {
            sum += n;
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        Validator.finiteResult(sum);
        return new Stats(numbers.size(), sum, sum / numbers.size(), min, max);
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        AverageCalculator calc = new AverageCalculator();
        System.out.println("=== Average Calculator (text version) ===");
        while (true) {
            System.out.println();
            String line = Validator.ask("Numbers separated by commas or spaces (type 0 to exit)");
            if (line.equals("0")) {
                System.out.println("Bye!");
                return;
            }
            try {
                Stats s = calc.stats(Validator.parseNumberList(line));
                System.out.println();
                System.out.println("  Count   : " + s.count());
                System.out.println("  Sum     : " + Validator.plain(Validator.round(s.sum(), 4)));
                System.out.println("  Average : " + Validator.plain(Validator.round(s.average(), 4)));
                System.out.println("  Min/Max : " + Validator.plain(s.min()) + " / " + Validator.plain(s.max()));
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
