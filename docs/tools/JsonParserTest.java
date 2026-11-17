import cinesmart.util.JsonParser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Focused checks for the JSON responses used by the online movie and cinema lookups. */
public class JsonParserTest {

    public static void main(String[] args) {
        Object parsed = JsonParser.parse(
                "{\"title\":\"A \\\"quote\\\" and \\u2605\","
                + "\"items\":[true,null,-1.25e2],\"nested\":{\"ok\":false}}");
        Map<?, ?> root = asMap(parsed);
        check("escaped string is decoded",
                "A \"quote\" and \u2605".equals(root.get("title")));
        List<?> items = (List<?>) root.get("items");
        check("array values preserve JSON types",
                Boolean.TRUE.equals(items.get(0)) && items.get(1) == null
                        && new BigDecimal("-1.25e2").equals(items.get(2)));
        check("nested object is parsed", Boolean.FALSE.equals(asMap(root.get("nested")).get("ok")));
        expectInvalid("{\"missing\":}");
        expectInvalid("[] trailing");
        expectInvalid("[01]");
        System.out.println("JsonParserTest: ALL CHECKS PASSED");
    }

    private static Map<?, ?> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("Expected an object");
        }
        return (Map<?, ?>) value;
    }

    private static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }

    private static void expectInvalid(String text) {
        try {
            JsonParser.parse(text);
            throw new AssertionError("Expected invalid JSON to be rejected: " + text);
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: invalid JSON rejected");
        }
    }
}
