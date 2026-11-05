import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Length, weight, temperature, speed, data size and time. Run it alone with:  java -cp out UnitConverter */
public class UnitConverter {

    /** A group of units. Every unit stores its size relative to the category's base unit. */
    public enum Category {
        LENGTH("Length", units("Kilometer", 1000, "Meter", 1, "Centimeter", 0.01, "Mile", 1609.344, "Foot", 0.3048, "Inch", 0.0254)),
        WEIGHT("Weight", units("Kilogram", 1, "Gram", 0.001, "Pound", 0.45359237, "Ounce", 0.028349523125)),
        TEMPERATURE("Temperature", units("Celsius", 0, "Fahrenheit", 0, "Kelvin", 0)),
        SPEED("Speed", units("m/s", 1, "km/h", 1000.0 / 3600.0, "mph", 0.44704)),
        DATA_SIZE("Data Size", units("Byte", 1, "KB", 1024, "MB", 1048576, "GB", 1073741824)),
        TIME("Time", units("Second", 1, "Minute", 60, "Hour", 3600, "Day", 86400));

        private final String displayName;
        private final Map<String, Double> factors;

        Category(String displayName, Map<String, Double> factors) {
            this.displayName = displayName;
            this.factors = factors;
        }

        private static Map<String, Double> units(Object... pairs) {
            Map<String, Double> map = new LinkedHashMap<>();
            for (int i = 0; i < pairs.length; i += 2) {
                map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
            }
            return Collections.unmodifiableMap(map);
        }

        public boolean isTemperature() {
            return this == TEMPERATURE;
        }

        public List<String> unitNames() {
            return new ArrayList<>(factors.keySet());
        }

        /** Size of one unit in the base unit (meter, kilogram, m/s, byte, second). */
        public double factor(String unit) {
            Double f = factors.get(unit);
            if (f == null) {
                throw Validator.fail("Unknown unit: " + Validator.shorten(unit, 30));
            }
            return f;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final double ABSOLUTE_ZERO_C = -273.15;

    public Category parseCategory(String text) {
        String wanted = text == null ? "" : text.trim().replace("_", " ").toLowerCase(Locale.ROOT);
        for (Category c : Category.values()) {
            if (c.name().replace("_", " ").toLowerCase(Locale.ROOT).equals(wanted) || c.toString().toLowerCase(Locale.ROOT).equals(wanted)) {
                return c;
            }
        }
        throw Validator.fail("Unknown category: " + Validator.shorten(text, 30));
    }

    public double convert(Category category, String from, String to, double value) {
        if (!Double.isFinite(value)) {
            throw Validator.fail("Enter a valid number.");
        }
        requireKnownUnit(category, from);
        requireKnownUnit(category, to);
        if (category.isTemperature()) {
            return convertTemperature(from, to, value);
        }
        if (value < 0) {
            throw Validator.fail("Value cannot be negative.");
        }
        return Validator.finiteResult(value * category.factor(from) / category.factor(to));
    }

    public double convertTemperature(String from, String to, double value) {
        double celsius = switch (from) {
            case "Celsius" -> value;
            case "Fahrenheit" -> (value - 32) * 5 / 9;
            case "Kelvin" -> value - 273.15;
            default -> throw Validator.fail("Unknown unit: " + Validator.shorten(from, 30));
        };
        if (celsius < ABSOLUTE_ZERO_C - 1e-9) {
            throw Validator.fail("Temperature is below absolute zero.");
        }
        return switch (to) {
            case "Celsius" -> celsius;
            case "Fahrenheit" -> celsius * 9 / 5 + 32;
            case "Kelvin" -> celsius + 273.15;
            default -> throw Validator.fail("Unknown unit: " + Validator.shorten(to, 30));
        };
    }

    private void requireKnownUnit(Category category, String unit) {
        if (unit == null || !category.unitNames().contains(unit)) {
            throw Validator.fail("Unknown unit: " + Validator.shorten(unit, 30));
        }
    }

    // ---------- Text version ----------

    public static void main(String[] args) {
        UnitConverter converter = new UnitConverter();
        System.out.println("=== Unit Converter (text version) ===");
        while (true) {
            System.out.println();
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                System.out.println("  " + (i + 1) + "  " + cats[i]);
            }
            System.out.println("  0  Exit");
            String choice = Validator.ask("Category number");
            if (choice.equals("0")) {
                System.out.println("Bye!");
                return;
            }
            try {
                int n = Validator.parseWholeNumber(choice);
                if (n < 1 || n > cats.length) {
                    throw Validator.fail("Choose a number from 0 to " + cats.length + ".");
                }
                Category cat = cats[n - 1];
                List<String> units = cat.unitNames();
                for (int i = 0; i < units.size(); i++) {
                    System.out.println("    " + (i + 1) + "  " + units.get(i));
                }
                String from = units.get(Validator.pick("Convert FROM (unit number)", units.size()) - 1);
                String to = units.get(Validator.pick("Convert TO (unit number)", units.size()) - 1);
                double value = Validator.parseNumber(Validator.ask("Value"));
                double result = converter.convert(cat, from, to, value);
                System.out.println();
                System.out.println("  " + Validator.plain(value) + " " + from + " = " + Validator.plain(Validator.round(result, 6)) + " " + to);
            } catch (Validator.InvalidInputException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}
