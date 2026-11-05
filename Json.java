import java.math.BigDecimal;
import java.util.*;

/** Small dependency-free JSON parser and serializer for the Allin1Calc API. */
public final class Json {
    private Json() {}
    public static Object parse(String s) { return new Parser(s).parse(); }
    public static String stringify(Object v) { StringBuilder b=new StringBuilder(); write(v,b); return b.toString(); }
    private static void write(Object v,StringBuilder b) {
        if(v==null){b.append("null");return;} if(v instanceof String){quote((String)v,b);return;}
        if(v instanceof Number || v instanceof Boolean){b.append(v);return;}
        if(v instanceof Map){b.append('{'); boolean first=true; for(Object e0:((Map<?,?>)v).entrySet()){Map.Entry<?,?>e=(Map.Entry<?,?>)e0;if(!first)b.append(',');first=false;quote(String.valueOf(e.getKey()),b);b.append(':');write(e.getValue(),b);}b.append('}');return;}
        if(v instanceof Iterable){b.append('[');boolean first=true;for(Object x:(Iterable<?>)v){if(!first)b.append(',');first=false;write(x,b);}b.append(']');return;}
        quote(String.valueOf(v),b);
    }
    private static void quote(String s,StringBuilder b){b.append('"');for(char c:s.toCharArray()){switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\b':b.append("\\b");break;case '\f':b.append("\\f");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}b.append('"');}
    private static final class Parser {
        final String s; int i=0; Parser(String s){this.s=s;}
        Object parse(){ws();Object x=value();ws();if(i!=s.length())throw new IllegalArgumentException("Unexpected content in JSON request.");return x;}
        Object value(){ws();if(i>=s.length())throw new IllegalArgumentException("JSON request is empty.");char c=s.charAt(i);if(c=='{')return object();if(c=='[')return array();if(c=='"')return string();if(c=='t'){word("true");return true;}if(c=='f'){word("false");return false;}if(c=='n'){word("null");return null;}return number();}
        Map<String,Object> object(){i++;Map<String,Object>m=new LinkedHashMap<>();ws();if(take('}'))return m;do{ws();if(i>=s.length()||s.charAt(i)!='"')bad();String k=string();ws();if(!take(':'))bad();m.put(k,value());ws();if(take('}'))return m;}while(take(','));bad();return m;}
        List<Object> array(){i++;List<Object>a=new ArrayList<>();ws();if(take(']'))return a;do{a.add(value());ws();if(take(']'))return a;}while(take(','));bad();return a;}
        String string(){i++;StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())bad();char e=s.charAt(i++);switch(e){case '"':case '\\':case '/':b.append(e);break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'u':if(i+4>s.length())bad();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:bad();}}else{if(c<32)bad();b.append(c);}}bad();return "";}
        Number number(){int start=i;if(take('-')){}if(take('0')){}else{digits();}if(take('.'))digits();if(take('e')||take('E')){if(take('+')||take('-')){}digits();}try{return new BigDecimal(s.substring(start,i));}catch(Exception e){bad();return 0;}}
        void digits(){int st=i;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i==st)bad();}
        void word(String w){if(!s.startsWith(w,i))bad();i+=w.length();} boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
        void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}void bad(){throw new IllegalArgumentException("Invalid JSON request.");}
    }
}

