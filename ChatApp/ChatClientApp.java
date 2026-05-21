import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatClientApp extends Application {
    private TextField hostField;
    private TextField portField;
    private TextField userField;
    private TextArea chatArea;
    private TextField messageField;
    private Button connectButton;
    private Button disconnectButton;
    private Button sendButton;
    private Button exportButton;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread listenThread;
    private String startupUser = "";
    private String startupHost = "127.0.0.1";
    private String startupPort = "5555";

    @Override
    public void start(Stage stage) {
        loadStartupArgs();
        Label title = new Label("JavaFX Chat Client");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        hostField = new TextField(startupHost);
        portField = new TextField(startupPort);
        userField = new TextField(startupUser.isBlank() ? "user" + (int) (Math.random() * 900 + 100) : startupUser);

        connectButton = new Button("Connect");
        disconnectButton = new Button("Disconnect");
        disconnectButton.setDisable(true);
        exportButton = new Button("Export Chat");

        HBox topControls = new HBox(
                8,
                new Label("Host"), hostField,
                new Label("Port"), portField,
                new Label("User"), userField,
                connectButton, disconnectButton, exportButton
        );
        topControls.setPadding(new Insets(10, 0, 10, 0));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-control-inner-background: #0b1220; -fx-text-fill: #dbeafe; -fx-font-family: Consolas, monospace;");

        messageField = new TextField();
        messageField.setPromptText("Write a message...");
        sendButton = new Button("Send");
        sendButton.setDisable(true);
        HBox bottom = new HBox(8, messageField, sendButton);
        bottom.setPadding(new Insets(10, 0, 0, 0));

        connectButton.setOnAction(e -> connect());
        disconnectButton.setOnAction(e -> disconnect());
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        exportButton.setOnAction(e -> exportChat(stage));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(6, title, topControls));
        root.setCenter(chatArea);
        root.setBottom(bottom);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #111827, #0b1220);");
        topControls.setStyle("-fx-background-color: #0f172a; -fx-padding: 10; -fx-background-radius: 8;");
        bottom.setStyle("-fx-background-color: #0f172a; -fx-padding: 10; -fx-background-radius: 8;");
        connectButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700;");
        disconnectButton.setStyle("-fx-background-color: #b91c1c; -fx-text-fill: white; -fx-font-weight: 700;");
        sendButton.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700;");
        exportButton.setStyle("-fx-background-color: #334155; -fx-text-fill: #e2e8f0; -fx-font-weight: 700;");

        Scene scene = new Scene(root, 900, 540);
        stage.setTitle("Chat Client");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> disconnect());
        stage.show();
    }

    private void loadStartupArgs() {
        var args = getParameters().getRaw();
        if (args.size() > 0 && !args.get(0).isBlank()) {
            startupUser = args.get(0).trim();
        }
        if (args.size() > 1 && !args.get(1).isBlank()) {
            startupHost = args.get(1).trim();
        }
        if (args.size() > 2 && !args.get(2).isBlank()) {
            startupPort = args.get(2).trim();
        }
    }

    private void connect() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String username = userField.getText().trim();
            if (username.isBlank()) {
                showError("Username cannot be empty.");
                return;
            }

            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            writer.write("LOGIN|" + username);
            writer.newLine();
            writer.flush();

            listenThread = new Thread(this::listenLoop, "chat-listener");
            listenThread.setDaemon(true);
            listenThread.start();

            appendLocal("Connected as " + username);
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            sendButton.setDisable(false);
            hostField.setDisable(true);
            portField.setDisable(true);
            userField.setDisable(true);
        } catch (Exception ex) {
            showError("Connection failed: " + ex.getMessage());
        }
    }

    private void listenLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CHAT|")) {
                    String[] parts = line.split("\\|", 4);
                    if (parts.length == 4) {
                        appendChat(parts[1], parts[2], parts[3]);
                    }
                } else if (line.startsWith("SYSTEM|")) {
                    appendSystem(line.substring("SYSTEM|".length()));
                }
            }
        } catch (IOException ignored) {
        } finally {
            Platform.runLater(this::disconnectUiOnly);
        }
    }

    private void sendMessage() {
        if (writer == null) return;
        String msg = messageField.getText().trim();
        if (msg.isBlank()) return;
        try {
            writer.write("MSG|" + msg);
            writer.newLine();
            writer.flush();
            messageField.clear();
        } catch (IOException ex) {
            showError("Send failed: " + ex.getMessage());
            disconnect();
        }
    }

    private void exportChat(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Chat Transcript");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        chooser.setInitialFileName("chat-transcript.txt");
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) return;

        try {
            Files.writeString(selected.toPath(), chatArea.getText(), StandardCharsets.UTF_8);
            appendLocal("Chat exported to " + selected.getAbsolutePath());
        } catch (IOException ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        } finally {
            disconnectUiOnly();
        }
    }

    private void disconnectUiOnly() {
        boolean wasConnected = (socket != null || writer != null || reader != null);
        connectButton.setDisable(false);
        disconnectButton.setDisable(true);
        sendButton.setDisable(true);
        hostField.setDisable(false);
        portField.setDisable(false);
        userField.setDisable(false);
        socket = null;
        reader = null;
        writer = null;
        if (wasConnected) {
            appendLocal("Disconnected.");
        }
    }

    private void appendChat(String user, String message, String timestamp) {
        Platform.runLater(() ->
                chatArea.appendText("[" + timestamp + "] " + user + ": " + message + System.lineSeparator())
        );
    }

    private void appendSystem(String message) {
        Platform.runLater(() ->
                chatArea.appendText("[SYSTEM] " + message + System.lineSeparator())
        );
    }

    private void appendLocal(String message) {
        String ts = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
        Platform.runLater(() ->
                chatArea.appendText("[LOCAL " + ts + "] " + message + System.lineSeparator())
        );
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
