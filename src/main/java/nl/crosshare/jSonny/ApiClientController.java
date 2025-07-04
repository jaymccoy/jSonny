package nl.crosshare.jSonny;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.net.http.*;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

public class ApiClientController {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label errorLabel;
    @FXML private ComboBox<String> methodBox;
    @FXML private TextField urlField;
    @FXML private TextField bearerTokenField;
    @FXML private TextArea headersArea;

    private CodeArea requestCodeArea;
    private CodeArea responseCodeArea;

    @FXML
    private VBox requestBodyContainer; // Add fx:id to the VBox in FXML
    @FXML
    private VBox responseContainer;    // Add fx:id to the VBox in FXML



    @FXML
    public void initialize() {
        if (!methodBox.getItems().isEmpty() && methodBox.getValue() == null) {
            methodBox.getSelectionModel().selectFirst();
        }

        requestCodeArea = new CodeArea();
        VBox.setVgrow(requestCodeArea, Priority.ALWAYS);
        requestCodeArea.getStyleClass().add("code-area");
//        requestCodeArea.textProperty().addListener((obs, oldText, newText) -> validateJsonInput(requestCodeArea));
        requestBodyContainer.getChildren().add(requestCodeArea);
        requestCodeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlightingAndValidation(requestCodeArea));

        responseCodeArea = new CodeArea();
        VBox.setVgrow(responseCodeArea, Priority.ALWAYS);
        responseCodeArea.getStyleClass().add("code-area");
        responseContainer.getChildren().add(responseCodeArea);
        responseCodeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlightingAndValidation(responseCodeArea));
    }

    @FXML
    private void sendRequest() {
        try {
            String method = methodBox.getValue();
            String url = urlField.getText();
            String bearerToken = bearerTokenField.getText();
            String headersText = headersArea.getText();
            String body = requestCodeArea.getText();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            // Add method and body
            if ("POST".equals(method) || "PUT".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            // Add Bearer token if present
            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken.trim());
            }

            // Parse and add custom headers
            if (headersText != null && !headersText.isBlank()) {
                String[] lines = headersText.split("\\r?\\n");
                for (String line : lines) {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String name = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        if (!name.isEmpty() && !value.isEmpty()) {
                            builder.header(name, value);
                        }
                    }
                }
            }

            HttpRequest request = builder.build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(response -> Platform.runLater(() -> {
                        responseCodeArea.replaceText(response);
                        applyHighlightingAndValidation(responseCodeArea);
                    }))
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            responseCodeArea.replaceText("Error: " + e.getMessage());
                            applyHighlightingAndValidation(responseCodeArea);
                        });
                        return null;
                    });

        } catch (Exception e) {
            responseCodeArea.replaceText("Error: " + e.getMessage());
        }
    }

    private static final ObjectWriter PRETTY_PRINTER = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private void applyHighlightingAndValidation(CodeArea codeArea) {
        String text = codeArea.getText();
        codeArea.setStyleSpans(0, computeHighlighting(text));
        validateJsonInput(codeArea);
    }

    private void setJsonContent(CodeArea area, String json) {
        try {
            String prettyJson = PRETTY_PRINTER.writeValueAsString(new ObjectMapper().readTree(json));
            area.replaceText(prettyJson);
            area.setStyleSpans(0, computeHighlighting(prettyJson));
        } catch (Exception e) {
            area.replaceText(json); // fallback
        }
    }

    private void validateJsonInput(CodeArea codeArea) {
        String json = codeArea.getText();
        try {
            objectMapper.readTree(json); // Validate JSON
            codeArea.setStyle("-fx-border-color: green;"); // Valid JSON
            errorLabel.setText(""); // Clear error message
        } catch (Exception e) {
            codeArea.setStyle("-fx-border-color: red;"); // Invalid JSON
            errorLabel.setText("Invalid JSON: " + e.getMessage());
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Pattern pattern = Pattern.compile(
                "(\"[^\"]*\"\\s*:)|" +         // keys
                        "(\"[^\"]*\")|" +              // strings
                        "(\\b\\d+\\b)|" +              // numbers
                        "(\\btrue\\b|\\bfalse\\b)|" +  // booleans
                        "(\\bnull\\b)|" +              // null
                        "([\\[\\]\\{\\}])"             // brackets/braces
        );
        //TODO: add punctuation and operators
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group(1) != null ? "json-key" :
                            matcher.group(2) != null ? "json-string" :
                                    matcher.group(3) != null ? "json-number" :
                                            matcher.group(4) != null ? "json-boolean" :
                                                    matcher.group(5) != null ? "json-null" :
                                                            matcher.group(6) != null ? "json-bracket" :
                                                                    null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}