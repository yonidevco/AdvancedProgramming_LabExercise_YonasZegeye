import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServerApp extends Application {
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final TextArea logArea = new TextArea();
    private ChatServer chatServer;
    private TextField portField;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        Label title = new Label("JavaFX Chat Server");
        title.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        portField = new TextField("5555");
        portField.setPrefWidth(90);

        Button startButton = new Button("Start Server");
        Button stopButton = new Button("Stop Server");
        Button newClientButton = new Button("+ New Client Window");
        stopButton.setDisable(true);
        newClientButton.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700;");
        newClientButton.setOnAction(e -> {
            try {
                ChatProcessHelper.spawnClient(null);
                appendLog("Launched another client window.");
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Could not start client: " + ex.getMessage());
                alert.showAndWait();
            }
        });

        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 600;");

        startButton.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                chatServer = new ChatServer(port, this::appendLog, this::setOnlineUsers);
                chatServer.start();
                statusLabel.setText("Status: Running on port " + port);
                statusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: 700;");
                startButton.setDisable(true);
                stopButton.setDisable(false);
                portField.setDisable(true);
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Port must be a valid number.");
                alert.showAndWait();
            } catch (IOException ex) {
                appendLog("ERROR: Could not start server: " + ex.getMessage());
            }
        });

        stopButton.setOnAction(e -> {
            if (chatServer != null) {
                chatServer.stop();
                statusLabel.setText("Status: Stopped");
                statusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 600;");
                startButton.setDisable(false);
                stopButton.setDisable(true);
                portField.setDisable(false);
                setOnlineUsers(chatServer.getClientCountSnapshot());
                appendLog("Server stopped.");
            }
        });

        HBox controls = new HBox(10, new Label("Port:"), portField, startButton, stopButton, newClientButton);
        controls.setPadding(new Insets(10, 0, 10, 0));
        startButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700;");
        stopButton.setStyle("-fx-background-color: #b91c1c; -fx-text-fill: white; -fx-font-weight: 700;");

        ListView<String> usersView = new ListView<>(onlineUsers);
        usersView.setPrefWidth(220);

        VBox left = new VBox(8, new Label("Online Users"), usersView);
        left.setPadding(new Insets(10));
        left.setStyle("-fx-background-color: #1f2937; -fx-border-color: #374151; -fx-text-fill: #e5e7eb;");

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: Consolas, monospace; -fx-control-inner-background: #0b1220; -fx-text-fill: #dbeafe;");

        BorderPane root = new BorderPane();
        VBox top = new VBox(6, title, statusLabel, controls);
        top.setPadding(new Insets(12));
        top.setStyle("-fx-background-color: #0f172a;");
        root.setTop(top);
        root.setLeft(left);
        root.setCenter(logArea);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #111827, #0b1220);");

        Scene scene = new Scene(root, 900, 540);
        stage.setTitle("Chat Server");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (chatServer != null) {
            chatServer.stop();
        }
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + now() + "] " + message + System.lineSeparator());
        });
    }

    private void setOnlineUsers(int count) {
        Platform.runLater(() -> {
            if (count == 0 && onlineUsers.isEmpty()) {
                return;
            }
            statusLabel.setText(statusLabel.getText().replaceAll("\\s+\\|\\s+Online:.*$", "") + " | Online: " + count);
        });
    }

    private void setOnlineUsers(Map<String, ClientHandler> clients) {
        Platform.runLater(() -> onlineUsers.setAll(clients.keySet().stream().sorted().toList()));
    }

    private static String now() {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class ChatServer {
    private final int port;
    private final LogListener logListener;
    private final ClientListListener clientListListener;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final DatabaseService databaseService = new DatabaseService();
    private final FileAuditService auditService = new FileAuditService();
    private ServerSocket serverSocket;
    private volatile boolean running;

    ChatServer(int port, LogListener logListener, ClientListListener clientListListener) {
        this.port = port;
        this.logListener = logListener;
        this.clientListListener = clientListListener;
    }

    void start() throws IOException {
        databaseService.init();
        // Bind explicitly to IPv4 so clients using 127.0.0.1 can connect even on
        // systems where wildcard ServerSocket bindings become IPv6-only.
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
        running = true;
        log("Server started on port " + port);
        pool.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                pool.submit(handler);
            } catch (IOException e) {
                if (running) log("Accept error: " + e.getMessage());
            }
        }
    }

    void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        databaseService.upsertUser(username);
        auditService.writeUserSnapshot(clients.keySet());
        clientListListener.update(clients);
        log(username + " joined.");
        broadcastSystem(username + " joined the chat.");
    }

    boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    void broadcastChat(String username, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String payload = "CHAT|" + username + "|" + message + "|" + timestamp;
        for (ClientHandler client : clients.values()) {
            client.send(payload);
        }
        databaseService.saveMessage(username, message, timestamp);
        auditService.appendLine("[" + timestamp + "] " + username + ": " + message);
        log(username + ": " + message);
    }

    void broadcastSystem(String message) {
        String payload = "SYSTEM|" + message;
        for (ClientHandler client : clients.values()) {
            client.send(payload);
        }
        auditService.appendLine("[SYSTEM] " + message);
    }

    void removeClient(String username) {
        if (username == null) return;
        clients.remove(username);
        auditService.writeUserSnapshot(clients.keySet());
        clientListListener.update(clients);
        log(username + " left.");
        broadcastSystem(username + " left the chat.");
    }

    void stop() {
        running = false;
        for (ClientHandler client : clients.values()) {
            client.close();
        }
        clients.clear();
        clientListListener.update(clients);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    int getClientCountSnapshot() {
        return clients.size();
    }

    private void log(String message) {
        logListener.onLog(message);
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String username;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String loginLine = reader.readLine();
            if (loginLine == null || !loginLine.startsWith("LOGIN|")) {
                send("SYSTEM|Invalid login payload.");
                close();
                return;
            }
            String requested = loginLine.substring("LOGIN|".length()).trim();
            if (requested.isBlank()) {
                send("SYSTEM|Username cannot be empty.");
                close();
                return;
            }
            if (server.isUsernameTaken(requested)) {
                send("SYSTEM|Username already in use.");
                close();
                return;
            }

            username = requested;
            server.registerClient(username, this);
            send("SYSTEM|Welcome " + username + "!");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MSG|")) {
                    String message = line.substring("MSG|".length()).trim();
                    if (!message.isBlank()) {
                        server.broadcastChat(username, message);
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            server.removeClient(username);
            close();
        }
    }

    void send(String payload) {
        try {
            writer.write(payload);
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
            close();
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}

class DatabaseService {
    private static final Map<String, String> ENV_FILE_VALUES = EnvFile.load();
    private static final String DB_URL = envOrFile(
            "CHAT_DB_URL",
            "jdbc:mysql://localhost:3306/javafx_chat?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    );
    private static final String DB_USER = envOrFile("CHAT_DB_USER", "root");
    private static final String DB_PASSWORD = envOrFile("CHAT_DB_PASSWORD", "");
    private volatile boolean dbAvailable = true;

    void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            dbAvailable = false;
            System.err.println("MySQL JDBC driver not found; continuing without DB: " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) UNIQUE NOT NULL,
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) NOT NULL,
                        message_text TEXT NOT NULL,
                        sent_at DATETIME NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            dbAvailable = false;
            System.err.println("DB init failed; continuing without DB: " + e.getMessage());
        }
    }

    void upsertUser(String username) {
        if (!dbAvailable) return;
        String sql = """
                INSERT INTO users (username)
                VALUES (?)
                ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    void saveMessage(String username, String message, String sentAt) {
        if (!dbAvailable) return;
        String sql = "INSERT INTO messages (username, message_text, sent_at) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, message);
            ps.setString(3, sentAt);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private static String envOrFile(String key, String defaultValue) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String fileValue = ENV_FILE_VALUES.get(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }
        return defaultValue;
    }
}

class EnvFile {
    static Map<String, String> load() {
        Map<String, String> values = new HashMap<>();
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return values;
        }

        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eqIndex = trimmed.indexOf('=');
                if (eqIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eqIndex).trim();
                String value = trimmed.substring(eqIndex + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
        }
        return values;
    }
}

class FileAuditService {
    private final Path logPath = Path.of("logs", "chat.log");
    private final Path usersPath = Path.of("logs", "online-users.txt");

    FileAuditService() {
        try {
            Files.createDirectories(logPath.getParent());
            if (!Files.exists(logPath)) Files.createFile(logPath);
            if (!Files.exists(usersPath)) Files.createFile(usersPath);
        } catch (IOException ignored) {
        }
    }

    synchronized void appendLine(String line) {
        try {
            Files.writeString(
                    logPath,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    synchronized void writeUserSnapshot(Iterable<String> users) {
        StringBuilder sb = new StringBuilder();
        for (String user : users) {
            sb.append(user).append(System.lineSeparator());
        }
        try {
            Files.writeString(usersPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}

interface LogListener {
    void onLog(String message);
}

interface ClientListListener {
    void update(Map<String, ClientHandler> clients);
}
