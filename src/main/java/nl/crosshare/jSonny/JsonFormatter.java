package nl.crosshare.jSonny;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class JsonFormatter {
    public static String formatJson(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(input, Object.class);
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            return writer.writeValueAsString(json);
        } catch (Exception e) {
            return input;
        }
    }
}