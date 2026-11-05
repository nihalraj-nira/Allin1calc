import java.time.*;import java.time.temporal.*;import java.util.*;
public final class DateCalculator {
 public static Map<String,Object> difference(LocalDate a,LocalDate b){Map<String,Object>r=new LinkedHashMap<>();r.put("days",Math.abs(ChronoUnit.DAYS.between(a,b)));r.put("direction",b.isBefore(a)?"earlier":"later");r.put("weeks",Math.abs(ChronoUnit.WEEKS.between(a,b)));return r;}
 public static LocalDate add(LocalDate d,long n,String unit){switch(unit.toLowerCase(Locale.ROOT)){case "day":case "days":return d.plusDays(n);case "week":case "weeks":return d.plusWeeks(n);case "month":case "months":return d.plusMonths(n);case "year":case "years":return d.plusYears(n);default:throw new IllegalArgumentException("Choose days, weeks, months, or years.");}}
 public static long workdays(LocalDate a,LocalDate b){LocalDate start=a.isBefore(b)?a:b,end=a.isBefore(b)?b:a;long count=0;for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)){DayOfWeek w=d.getDayOfWeek();if(w!=DayOfWeek.SATURDAY&&w!=DayOfWeek.SUNDAY)count++;}return count;}
 public static boolean leap(int y){if(y<1||y>9999)throw new IllegalArgumentException("Year must be between 1 and 9999.");return Year.isLeap(y);}
 public static void main(String[]args){try{LocalDate d=LocalDate.parse(args.length>0?args[0]:"2026-01-01");System.out.println(add(d,args.length>1?Long.parseLong(args[1]):30,"days"));}catch(Exception e){System.err.println(e.getMessage());}}
}

