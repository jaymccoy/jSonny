package nl.crosshare.jSonny;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class ApiClientController {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML private Label errorLabel;
    @FXML private ComboBox<String> methodBox;
    @FXML private TextField urlField;
    @FXML private TextArea headersArea;

    private CodeArea requestCodeArea;
    private CodeArea responseCodeArea;

    @FXML
    private VBox requestBodyContainer; // Add fx:id to the VBox in FXML
    @FXML
    private VBox responseContainer;    // Add fx:id to the VBox in FXML

    @FXML private ListView<String> httpFilesList;
    private File httpFilesDir = new File("requests"); // folder for .http files
    private Preferences prefs = Preferences.userNodeForPackage(ApiClientController.class);
    private static final String LAST_EXECUTED_KEY = "lastExecutedHttpFile";



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

        loadHttpFilesList();
        httpFilesList.setCellFactory(lv -> {
            javafx.scene.control.ListCell<String> cell = new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };

            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

            javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("Delete");
            deleteItem.setOnAction(e -> {
                String fileName = cell.getItem();
                if (fileName != null) {
                    File file = new File(httpFilesDir, fileName);
                    if (file.delete()) {
                        loadHttpFilesList();
                    }
                }
            });

            javafx.scene.control.MenuItem duplicateItem = new javafx.scene.control.MenuItem("Duplicate");
            duplicateItem.setOnAction(e -> {
                String fileName = cell.getItem();
                if (fileName != null) {
                    File original = new File(httpFilesDir, fileName);
                    String newName = fileName.replace(".http", "_copy_" + System.currentTimeMillis() + ".http");
                    File copy = new File(httpFilesDir, newName);
                    try {
                        Files.copy(original.toPath(), copy.toPath());
                        loadHttpFilesList();
                    } catch (IOException ex) {
                        errorLabel.setText("Failed to duplicate: " + ex.getMessage());
                    }
                }
            });

            javafx.scene.control.MenuItem renameItem = new javafx.scene.control.MenuItem("Rename");
            renameItem.setOnAction(e -> {
                String fileName = cell.getItem();
                if (fileName != null) {
                    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(fileName);
                    dialog.setTitle("Rename Request");
                    dialog.setHeaderText("Enter new file name:");
                    dialog.setContentText("New name:");
                    dialog.showAndWait().ifPresent(newName -> {
                        if (!newName.endsWith(".http")) newName += ".http";
                        File original = new File(httpFilesDir, fileName);
                        File renamed = new File(httpFilesDir, newName);
                        if (!renamed.exists() && original.renameTo(renamed)) {
                            loadHttpFilesList();
                            httpFilesList.getSelectionModel().select(newName);
                        } else {
                            errorLabel.setText("Rename failed (file may already exist).");
                        }
                    });
                }
            });

            contextMenu.getItems().addAll(deleteItem, duplicateItem, renameItem);

            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                cell.setContextMenu(isNowEmpty ? null : contextMenu);
            });

            return cell;
        });

        httpFilesList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                File file = new File(httpFilesDir, newVal);
                try {
                    loadRequestFromFile(file);
                } catch (IOException e) {
                    errorLabel.setText("Failed to load: " + e.getMessage());
                }
            }
        });
        // Optionally auto-select last executed
        String last = prefs.get(LAST_EXECUTED_KEY, null);
        if (last != null) {
            httpFilesList.getSelectionModel().select(last);
        }

        // Auto-refresh the list every minute
        Timeline refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(10), event -> loadHttpFilesList())
        );
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    @FXML
    private void sendRequest() {
        try {
            String method = methodBox.getValue();
            String url = urlField.getText();
            String headersText = headersArea.getText();
            String body = requestCodeArea.getText();

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            // Add method and body
            if ("POST".equals(method) || "PUT".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
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

            // Send it!
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

            // Save request as a new .http file
            String fileName = "request_" + System.currentTimeMillis() + ".http";
            File newFile = new File(httpFilesDir, fileName);
            saveRequestToFile(newFile);

            // Reload the list to include the new file
            loadHttpFilesList();

            // Select and store the latest file
            httpFilesList.getSelectionModel().select(fileName);
            prefs.put(LAST_EXECUTED_KEY, fileName);

        } catch (Exception e) {
            responseCodeArea.replaceText("Error: " + e.getMessage());
        }


    }

    private void loadHttpFilesList() {
        String selected = httpFilesList.getSelectionModel().getSelectedItem();
        // Save changes to the currently selected file before refreshing
        if (selected != null) {
            File selectedFile = new File(httpFilesDir, selected);
            try {
                saveRequestToFile(selectedFile);
            } catch (IOException e) {
                errorLabel.setText("Failed to save: " + e.getMessage());
            }
        }
        if (!httpFilesDir.exists()) httpFilesDir.mkdirs();
        File[] files = httpFilesDir.listFiles((dir, name) -> name.endsWith(".http"));
        ObservableList<String> items = FXCollections.observableArrayList();
        if (files != null) {
            for (File f : files) items.add(f.getName());
        }
        httpFilesList.setItems(items);
        if (selected != null && items.contains(selected)) {
            httpFilesList.getSelectionModel().select(selected);
        }
        System.out.println("Loaded HTTP files: " + items);
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

    public void saveRequestToFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(methodBox.getValue()).append(" ").append(urlField.getText()).append("\n");
        String[] headers = headersArea.getText().split("\\n");
        for (String header : headers) {
            if (!header.trim().isEmpty()) sb.append(header).append("\n");
        }
        sb.append("\n");
        sb.append(requestCodeArea.getText());
        Files.write(file.toPath(), sb.toString().getBytes());
    }

    public void loadRequestFromFile(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.isEmpty()) return;

        // Parse first line: METHOD URL PROTOCOL
        String[] firstLine = lines.get(0).split(" ", 3);
        methodBox.setValue(firstLine.length > 0 ? firstLine[0] : "");
        String urlFieldPath = firstLine.length > 1 ? firstLine[1] : "";
        String httpVersion = firstLine.length > 2 ? firstLine[2] : "http";

        StringBuilder headers = new StringBuilder();
        StringBuilder comments = new StringBuilder();
        String host = "";
        int i = 1;
        boolean inBody = false;
        StringBuilder body = new StringBuilder();

        while (i < lines.size()) {
            String line = lines.get(i);

            if (!inBody) {
                if (line.trim().isEmpty()) {
                    inBody = true;
                    i++;
                    continue;
                }
                if (line.startsWith("#") || line.trim().startsWith("//")) {
                    comments.append(line).append("\n");
                } else if (line.startsWith("Host:")) {
                    host = line.substring(5).trim();
                } else {
                    headers.append(line).append("\n");
                }
            } else {
                body.append(line).append("\n");
            }
            i++;
        }

        System.out.println("httpVersion: " + httpVersion);
        // Instead of always prepending "http://"
        if (!host.isEmpty() && !urlFieldPath.startsWith("http")) {
            urlField.setText("http://" + host + urlFieldPath);
        } else {
            urlField.setText(urlFieldPath);
        }
        headersArea.setText(headers.toString().trim());
        requestCodeArea.replaceText(body.toString().trim());
        applyHighlightingAndValidation(requestCodeArea); // <-- Add this line
        // Optionally, display comments and host somewhere in the UI if needed

    }

}