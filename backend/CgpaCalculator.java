import java.util.*;
public final class CgpaCalculator {
 public static Map<String,Object> convert(double cgpa,double multiplier){if(cgpa<0||cgpa>10)throw new IllegalArgumentException("CGPA must be from 0 to 10.");if(multiplier<=0||multiplier>100)throw new IllegalArgumentException("Multiplier must be greater than 0 and at most 100.");Map<String,Object>r=new LinkedHashMap<>();r.put("cgpa",cgpa);r.put("multiplier",multiplier);r.put("percentage",cgpa*multiplier);return r;}
 public static void main(String[]args){System.out.println(Json.stringify(convert(8.5,9.5)));}
}

