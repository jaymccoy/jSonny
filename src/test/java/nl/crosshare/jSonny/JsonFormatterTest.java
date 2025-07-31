package nl.crosshare.jSonny;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;

public class JsonFormatterTest {

    @Test
    void testFormatJson_validJson() {
        String input = "{\"a\":1}";
        String expected = "{\n  \"a\" : 1\n}";
        String actual = JsonFormatter.formatJson(input);
        assertEquals(expected, actual);
    }

    @Test
    void testFormatJson_invalidJson() {
        String input = "not json";
        String actual = JsonFormatter.formatJson(input);
        assertEquals(input, actual);
    }
}