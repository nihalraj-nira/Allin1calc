import java.util.*;
public final class AverageCalculator {
 public static Map<String,Object> calculate(List<Double> values){
  if(values.isEmpty())throw new IllegalArgumentException("Enter at least one number.");
  if(values.size()>1000)throw new IllegalArgumentException("Enter no more than 1000 numbers.");
  double sum=0;
  for(double x:values){
   if(!Double.isFinite(x))throw new IllegalArgumentException("Values must be finite numbers.");
   sum+=x;
   if(!Double.isFinite(sum))throw new IllegalArgumentException("The result is outside the supported range.");
  }
  double avg=sum/values.size();
  if(!Double.isFinite(avg))throw new IllegalArgumentException("The result is outside the supported range.");
  return Map.of("average",avg);
 }
 public static void main(String[]args){System.out.println(Json.stringify(calculate(Arrays.asList(12.0,15.0,18.0))));}
}

