import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
public final class AgeCalculator {
 public static Map<String,Object> age(LocalDate dob,LocalDate on){if(dob.isAfter(on))throw new IllegalArgumentException("Date of birth cannot be after the selected date.");Period p=Period.between(dob,on);LocalDate next=dob.withYear(on.getYear());if(!next.isAfter(on))next=dob.withYear(on.getYear()+1);Map<String,Object>r=new LinkedHashMap<>();r.put("years",p.getYears());r.put("months",p.getMonths());r.put("days",p.getDays());r.put("totalDays",Duration.between(dob.atStartOfDay(),on.atStartOfDay()).toDays());r.put("weekday",dob.getDayOfWeek().toString());r.put("nextBirthdayDays",ChronoUnit.DAYS.between(on,next));return r;}
 public static Map<String,Object> difference(LocalDate a,LocalDate b){LocalDate lo=a.isBefore(b)?a:b,hi=a.isBefore(b)?b:a;Period p=Period.between(lo,hi);Map<String,Object>r=new LinkedHashMap<>();r.put("years",p.getYears());r.put("months",p.getMonths());r.put("days",p.getDays());r.put("totalDays",ChronoUnit.DAYS.between(lo,hi));return r;}
 public static void main(String[]args){try{LocalDate dob=LocalDate.parse(args.length>0?args[0]:"2005-03-14"),on=LocalDate.parse(args.length>1?args[1]:LocalDate.now().toString());System.out.println(Json.stringify(age(dob,on)));}catch(Exception e){System.err.println(e.getMessage());}}
}

