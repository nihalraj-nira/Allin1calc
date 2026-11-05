import java.time.*;import java.time.format.*;import java.util.*;
public final class TimeCalculator {
 private static int parseHm(String s){if(s==null||!s.matches("(?:[0-9]{1,3}):[0-5][0-9]"))throw new IllegalArgumentException("Enter time as HH:MM.");String[]p=s.split(":");return Math.addExact(Math.multiplyExact(Integer.parseInt(p[0]),60),Integer.parseInt(p[1]));}
 private static String fmt(long total){long h=Math.floorDiv(total,60),m=Math.floorMod(total,60);return String.format(Locale.ROOT,"%02d:%02d",h,m);}
 public static Map<String,Object> calc(String a,String b,String op){int x=parseHm(a),y=parseHm(b);long total=op.equalsIgnoreCase("subtract")?(long)x-y:(long)x+y;Map<String,Object>r=new LinkedHashMap<>();r.put("time",fmt(total));r.put("totalMinutes",total);return r;}
 public static Map<String,Object> hoursWorked(String start,String end){int a=parseHm(start),b=parseHm(end);if(b<a)b+=1440;int mins=b-a;Map<String,Object>r=new LinkedHashMap<>();r.put("hours",mins/60);r.put("minutes",mins%60);r.put("duration",fmt(mins));r.put("overnight",b>=1440);return r;}
 public static Map<String,Object> countdown(String target){try{LocalDateTime t=LocalDateTime.parse(target);long sec=Duration.between(LocalDateTime.now(),t).getSeconds();sec=Math.max(0,sec);Map<String,Object>r=new LinkedHashMap<>();r.put("seconds",sec);r.put("days",sec/86400);r.put("hours",(sec%86400)/3600);r.put("minutes",(sec%3600)/60);r.put("remainingSeconds",sec%60);return r;}catch(DateTimeParseException e){throw new IllegalArgumentException("Choose a valid target date and time.");}}
 public static void main(String[]args){System.out.println(Json.stringify(calc(args.length>0?args[0]:"01:30",args.length>1?args[1]:"02:15","add")));}
}

