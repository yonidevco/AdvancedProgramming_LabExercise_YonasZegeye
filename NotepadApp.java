import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NotepadApp extends Application {
    private final TextArea editor = new TextArea();
    private final Label status = new Label("Ready");
    private final TextField searchField = new TextField();
    private File currentFile;

    @Override
    public void start(Stage stage) {
        Button newBtn = new Button("New");
        Button openBtn = new Button("Open");
        Button saveBtn = new Button("Save");
        Button saveAsBtn = new Button("Save As");
        Button findBtn = new Button("Find");

        newBtn.setOnAction(e -> newFile());
        openBtn.setOnAction(e -> openFile(stage));
        saveBtn.setOnAction(e -> save(stage, false));
        saveAsBtn.setOnAction(e -> save(stage, true));
        findBtn.setOnAction(e -> findNext());

        searchField.setPromptText("Find text...");
        searchField.setOnAction(e -> findNext());

        HBox topBar = new HBox(8, newBtn, openBtn, saveBtn, saveAsBtn, searchField, findBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f4f4f6;");

        editor.setWrapText(true);
        editor.setStyle("control-inner-background: #ffffff; text-fill: #000000; font-family: Consolas, monospace;");
        status.setStyle("text-fill: #94a3b8; -fx-padding: 6 10 8 10;");

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(editor);
        root.setBottom(status);
        root.setStyle("background-color: linear-gradient(to bottom, #111827, #0b1220);");

        Scene scene = new Scene(root, 900, 580);
        stage.setScene(scene);
        stage.setTitle("Notepad");
        stage.show();
    }

    private void newFile() {
        editor.clear();
        currentFile = null;
        status.setText("New file");
    }

    private void openFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Text File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md", "*.log"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            editor.setText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            currentFile = file;
            status.setText("Opened: " + file.getAbsolutePath());
        } catch (IOException ex) {
            showError("Open failed: " + ex.getMessage());
        }
    }

    private void save(Stage stage, boolean forceSaveAs) {
        File target = currentFile;
        if (target == null || forceSaveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Text File");
            chooser.setInitialFileName("notes.txt");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            target = chooser.showSaveDialog(stage);
            if (target == null) {
                return;
            }
        }

        try {
            Files.writeString(target.toPath(), editor.getText(), StandardCharsets.UTF_8);
            currentFile = target;
            status.setText("Saved: " + target.getAbsolutePath());
        } catch (IOException ex) {
            showError("Save failed: " + ex.getMessage());
        }
    }

    private void findNext() {
        String needle = searchField.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        String haystack = editor.getText();
        int from = Math.max(editor.getCaretPosition(), 0);
        int idx = haystack.indexOf(needle, from);
        if (idx < 0 && from > 0) {
            idx = haystack.indexOf(needle);
        }
        if (idx >= 0) {
            editor.selectRange(idx, idx + needle.length());
            status.setText("Found at position " + idx);
        } else {
            status.setText("Text not found: " + needle);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Notepad Error");
        alert.setHeaderText("Operation failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
