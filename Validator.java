import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class Validator {
    private Validator() {}
    public static String text(Map<String,Object> m,String k){Object v=m.get(k);if(v==null)throw new IllegalArgumentException("Enter a value for " + k + ".");String s=String.valueOf(v).trim();if(s.isEmpty())throw new IllegalArgumentException("Enter a value for " + k + ".");if(s.length()>40)throw new IllegalArgumentException("Values must be 40 characters or fewer.");return s;}
    public static BigDecimal decimal(Map<String,Object>m,String k){try{return new BigDecimal(text(m,k));}catch(NumberFormatException e){throw new IllegalArgumentException(k+" must be a number.");}}
    public static double number(Map<String,Object>m,String k){double x=decimal(m,k).doubleValue();if(!Double.isFinite(x))throw new IllegalArgumentException(k+" is outside the supported range.");return x;}
    public static long whole(Map<String,Object>m,String k){try{return decimal(m,k).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException(k+" must be a whole number.");}}
    public static LocalDate date(Map<String,Object>m,String k){try{LocalDate d=LocalDate.parse(text(m,k));if(d.getYear()<1||d.getYear()>9999)throw new IllegalArgumentException("Dates must be in years 1 to 9999.");return d;}catch(DateTimeParseException e){throw new IllegalArgumentException(k+" must be a valid date (YYYY-MM-DD).");}}
    public static double range(double x,double min,double max,String name){if(x<min||x>max)throw new IllegalArgumentException(name+" must be between "+min+" and "+max+".");return x;}
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object o){if(!(o instanceof Map))throw new IllegalArgumentException("Request must be a JSON object.");return (Map<String,Object>)o;}
}

