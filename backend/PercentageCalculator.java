import java.util.*;
public final class PercentageCalculator {
 public static double calculate(String op,double a,double b){switch(op){case "percent_of":return a*b/100.0;case "what_percent":if(b==0)throw new IllegalArgumentException("The whole value cannot be zero.");return a/b*100.0;case "find_whole":if(b==0)throw new IllegalArgumentException("Percentage cannot be zero.");return a*100.0/b;case "change":if(a==0)throw new IllegalArgumentException("Starting value cannot be zero.");return (b-a)/Math.abs(a)*100.0;case "increase":return a*(1+b/100.0);case "decrease":return a*(1-b/100.0);default:throw new IllegalArgumentException("Choose one of the six percentage operations.");}}
 public static void main(String[]args){System.out.println(calculate("percent_of",20,150));}
}

