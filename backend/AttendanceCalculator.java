import java.util.*;
public final class AttendanceCalculator {
 public static Map<String,Object> calculate(long total,long attended,double target){if(total<=0)throw new IllegalArgumentException("Total classes must be greater than zero.");if(attended<0||attended>total)throw new IllegalArgumentException("Attended classes must be from 0 to total classes.");if(target<=0||target>=100)throw new IllegalArgumentException("Target percentage must be greater than 0 and less than 100.");double pct=100.0*attended/total;long needed=Math.max(0,(long)Math.ceil((target*total-100.0*attended)/(100-target)));long bunks=Math.max(0,(long)Math.floor(100.0*attended/target-total+1e-10));Map<String,Object>r=new LinkedHashMap<>();r.put("percentage",pct);r.put("target",target);r.put("status",pct+1e-9>=target?"Safe":"Below target");r.put("classesNeeded",needed);r.put("safeBunks",bunks);return r;}
 public static void main(String[]args){try{System.out.println(Json.stringify(calculate(40,32,75)));}catch(Exception e){System.err.println(e.getMessage());}}
}

