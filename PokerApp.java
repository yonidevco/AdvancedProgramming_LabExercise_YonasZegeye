import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PokerApp extends Application {
    private enum Phase { DEAL, DRAW, SHOWDOWN, DONE }

    private final PokerGame game = new PokerGame();
    private final List<CardWidget> playerWidgets = new ArrayList<>();
    private final List<CardWidget> dealerWidgets = new ArrayList<>();

    private Phase phase = Phase.DONE;
    private int bankroll = 1000;
    private int wins = 0;
    private int losses = 0;
    private int pushes = 0;

    private Label bankrollValue;
    private Label playerRankLabel;
    private Label dealerRankLabel;
    private Label bannerLabel;
    private Label phaseDeal;
    private Label phaseDraw;
    private Label phaseShow;
    private TextArea log;
    private Label statsLabel;

    private Button dealBtn;
    private Button drawBtn;
    private Button showdownBtn;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.getStyleClass().add("root");

        root.setTop(buildHeader());
        root.setCenter(buildTable());
        root.setRight(buildSidebar());
        root.setBottom(buildControls());

        Scene scene = new Scene(root, 1180, 720);
        loadStylesheet(scene);

        stage.setTitle("Royal Draw Poker");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();

        startNewHand();
    }

    private VBox buildHeader() {
        Label title = new Label("Royal Draw Poker");
        title.getStyleClass().add("app-title");
        Label sub = new Label("5-card draw • Hold cards • Draw • Showdown");
        sub.getStyleClass().add("app-sub");

        bankrollValue = new Label("$" + bankroll);
        bankrollValue.getStyleClass().add("chip-value");
        Label chipCap = new Label("BANKROLL");
        chipCap.getStyleClass().add("chip-label");
        VBox chipBox = new VBox(2, chipCap, bankrollValue);
        chipBox.getStyleClass().add("chip-tray");
        chipBox.setAlignment(Pos.CENTER);

        HBox top = new HBox(20, new VBox(4, title, sub), new Region(), chipBox);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 14, 0));
        return new VBox(top);
    }

    private VBox buildTable() {
        VBox felt = new VBox(24);
        felt.getStyleClass().addAll("felt");

        VBox inner = new VBox(28);
        inner.getStyleClass().add("felt-inner");

        Label dealerTitle = new Label("DEALER");
        dealerTitle.getStyleClass().add("section-title");
        HBox dealerRow = new HBox(12);
        dealerRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 5; i++) {
            CardWidget w = new CardWidget(true);
            dealerWidgets.add(w);
            dealerRow.getChildren().add(w);
        }
        dealerRankLabel = new Label("Hand: hidden");
        dealerRankLabel.getStyleClass().add("rank-badge");

        Label playerTitle = new Label("YOU — click cards to HOLD");
        playerTitle.getStyleClass().add("section-title");
        HBox playerRow = new HBox(12);
        playerRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 5; i++) {
            CardWidget w = new CardWidget(false);
            w.setOnClick(() -> {
                if (phase == Phase.DRAW) {
                    w.toggleHold();
                    renderPlayerCards();
                }
            });
            playerWidgets.add(w);
            playerRow.getChildren().add(w);
        }
        playerRankLabel = new Label("Hand: —");
        playerRankLabel.getStyleClass().add("rank-badge");

        bannerLabel = new Label("Press Deal to start");
        bannerLabel.getStyleClass().add("banner-neutral");
        bannerLabel.setMaxWidth(Double.MAX_VALUE);
        bannerLabel.setAlignment(Pos.CENTER);

        VBox dealerBox = new VBox(10, dealerTitle, dealerRow, dealerRankLabel);
        dealerBox.setAlignment(Pos.CENTER);
        VBox playerBox = new VBox(10, playerTitle, playerRow, playerRankLabel);
        playerBox.setAlignment(Pos.CENTER);

        inner.getChildren().addAll(dealerBox, bannerLabel, playerBox);
        felt.getChildren().add(inner);
        VBox.setVgrow(felt, Priority.ALWAYS);
        return felt;
    }

    private VBox buildSidebar() {
        VBox side = new VBox(14);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(240);
        side.setPadding(new Insets(16));
        BorderPane.setMargin(side, new Insets(0, 0, 0, 16));

        Label payTitle = new Label("Hand rankings");
        payTitle.getStyleClass().add("section-title");
        VBox pay = new VBox(6,
                payRow("Straight Flush", "Jackpot feel"),
                payRow("Four of a Kind", "Monster"),
                payRow("Full House", "Strong"),
                payRow("Flush", "Solid"),
                payRow("Straight", "Nice"),
                payRow("Three of a Kind", "Good"),
                payRow("Two Pair", "OK"),
                payRow("One Pair", "Basic")
        );

        Label statsTitle = new Label("Session");
        statsTitle.getStyleClass().add("section-title");
        statsLabel = new Label();
        updateStatsLabel();

        log = new TextArea();
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(8);
        log.getStyleClass().add("log-area");

        side.getChildren().addAll(payTitle, pay, new Region(), statsTitle, statsLabel, new Label("Log"), log);
        VBox.setVgrow(log, Priority.ALWAYS);
        return side;
    }

    private HBox buildControls() {
        phaseDeal = phasePill("1 · DEAL", true);
        phaseDraw = phasePill("2 · DRAW", false);
        phaseShow = phasePill("3 · SHOWDOWN", false);
        HBox phases = new HBox(8, phaseDeal, phaseDraw, phaseShow);
        phases.setAlignment(Pos.CENTER);

        dealBtn = new Button("Deal ($25 ante)");
        drawBtn = new Button("Draw");
        showdownBtn = new Button("Showdown");
        dealBtn.getStyleClass().add("btn-primary");
        drawBtn.getStyleClass().add("btn-secondary");
        showdownBtn.getStyleClass().add("btn-gold");

        dealBtn.setOnAction(e -> startNewHand());
        drawBtn.setOnAction(e -> onDraw());
        showdownBtn.setOnAction(e -> onShowdown());

        HBox buttons = new HBox(10, dealBtn, drawBtn, showdownBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, phases, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(16, 0, 0, 0));
        return new HBox(box);
    }

    private Label phasePill(String text, boolean active) {
        Label l = new Label(text);
        l.getStyleClass().add("phase-pill");
        if (active) l.getStyleClass().add("phase-pill-active");
        return l;
    }

    private void setPhaseUi(Phase p) {
        phase = p;
        phaseDeal.getStyleClass().remove("phase-pill-active");
        phaseDraw.getStyleClass().remove("phase-pill-active");
        phaseShow.getStyleClass().remove("phase-pill-active");
        if (p == Phase.DEAL || p == Phase.DONE) phaseDeal.getStyleClass().add("phase-pill-active");
        if (p == Phase.DRAW) phaseDraw.getStyleClass().add("phase-pill-active");
        if (p == Phase.SHOWDOWN) phaseShow.getStyleClass().add("phase-pill-active");

        drawBtn.setDisable(p != Phase.DRAW);
        showdownBtn.setDisable(p != Phase.SHOWDOWN);
        dealBtn.setDisable(p == Phase.DRAW || p == Phase.SHOWDOWN);
    }

    private void startNewHand() {
        if (bankroll < game.ante()) {
            bannerLabel.setText("Out of chips! Resetting bankroll to $1000");
            bannerLabel.getStyleClass().setAll("banner-neutral");
            bankroll = 1000;
            bankrollValue.setText("$" + bankroll);
        }
        bankroll -= game.ante();
        bankrollValue.setText("$" + bankroll);

        game.newRound();
        for (CardWidget w : playerWidgets) w.clearHold();
        renderPlayerCards();
        renderDealerCards(false);

        playerRankLabel.setText("Hand: " + game.playerRank().label());
        dealerRankLabel.setText("Hand: hidden");
        bannerLabel.setText("Select cards to HOLD, then Draw");
        bannerLabel.getStyleClass().setAll("banner-neutral");
        log("── New hand (ante $" + game.ante() + ") ──");

        setPhaseUi(Phase.DRAW);
    }

    private void onDraw() {
        List<Boolean> holds = new ArrayList<>();
        for (CardWidget w : playerWidgets) holds.add(w.isHeld());
        int n = game.draw(holds);
        renderPlayerCards();
        renderDealerCards(false);
        playerRankLabel.setText("Hand: " + game.playerRank().label());
        log("Drew " + n + " card(s).");
        bannerLabel.setText("Ready for showdown");
        setPhaseUi(Phase.SHOWDOWN);
    }

    private void onShowdown() {
        renderDealerCards(true);
        PokerGame.ShowdownResult r = game.showdown();
        bankroll += r.payout();
        bankrollValue.setText("$" + bankroll);

        dealerRankLabel.setText("Hand: " + game.dealerRank().label());
        playerRankLabel.setText("Hand: " + game.playerRank().label());

        bannerLabel.setText(r.message() + (r.payout() != 0 ? "  (" + (r.payout() > 0 ? "+" : "") + r.payout() + ")" : ""));
        if (r.payout() > 0) {
            bannerLabel.getStyleClass().setAll("banner-win");
            wins++;
        } else if (r.payout() < 0) {
            bannerLabel.getStyleClass().setAll("banner-lose");
            losses++;
        } else {
            bannerLabel.getStyleClass().setAll("banner-neutral");
            pushes++;
        }
        log(r.message() + " | Bankroll $" + bankroll);
        updateStatsLabel();
        setPhaseUi(Phase.DONE);
    }

    private void renderPlayerCards() {
        List<Card> hand = game.playerHand();
        for (int i = 0; i < 5; i++) {
            playerWidgets.get(i).showFace(hand.get(i));
        }
    }

    private void renderDealerCards(boolean reveal) {
        List<Card> hand = game.dealerHand();
        for (int i = 0; i < 5; i++) {
            if (reveal) dealerWidgets.get(i).showFace(hand.get(i));
            else dealerWidgets.get(i).showBack();
        }
    }

    private void log(String line) {
        log.appendText(line + "\n");
    }

    private HBox payRow(String hand, String note) {
        Label h = new Label(hand);
        h.getStyleClass().add("paytable-hand");
        Label n = new Label(" — " + note);
        n.getStyleClass().add("paytable-row");
        HBox row = new HBox(h, n);
        return row;
    }

    private void updateStatsLabel() {
        if (statsLabel == null) return;
        statsLabel.setText("Wins: " + wins + "  Losses: " + losses + "  Push: " + pushes);
        statsLabel.getStyleClass().add("paytable-row");
    }

  /* ── playing card widget ── */
    private static final class CardWidget extends StackPane {
        private final boolean dealer;
        private boolean held;
        private Runnable onClick;

        CardWidget(boolean dealer) {
            this.dealer = dealer;
            getStyleClass().add("playing-card-back");
            setOnMouseClicked(e -> {
                if (onClick != null) onClick.run();
            });
        }

        void setOnClick(Runnable r) {
            onClick = r;
        }

        void toggleHold() {
            if (dealer) return;
            held = !held;
            getStyleClass().remove("playing-card-held");
            if (held) getStyleClass().add("playing-card-held");
        }

        boolean isHeld() {
            return held;
        }

        void clearHold() {
            held = false;
            getStyleClass().remove("playing-card-held");
        }

        void showBack() {
            getChildren().clear();
            getStyleClass().setAll("playing-card-back");
        }

        void showFace(Card card) {
            getChildren().clear();
            getStyleClass().setAll("playing-card");
            if (held) getStyleClass().add("playing-card-held");

            boolean red = card.suit().isRed();
            String colorClass = red ? "card-red" : "card-black";

            Label tl = new Label(card.rank().label() + card.suit().symbol());
            tl.getStyleClass().addAll("card-rank-tl", colorClass);
            StackPane.setAlignment(tl, Pos.TOP_LEFT);
            StackPane.setMargin(tl, new Insets(8, 0, 0, 10));

            Label center = new Label(card.suit().symbol());
            center.getStyleClass().addAll("card-suit-center", colorClass);

            Label br = new Label(card.rank().label() + card.suit().symbol());
            br.getStyleClass().addAll("card-rank-br", colorClass);
            StackPane.setAlignment(br, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(br, new Insets(0, 10, 8, 0));

            getChildren().addAll(center, tl, br);

            if (held && !dealer) {
                Label hold = new Label("HOLD");
                hold.getStyleClass().add("hold-tag");
                StackPane.setAlignment(hold, Pos.TOP_CENTER);
                StackPane.setMargin(hold, new Insets(6, 0, 0, 0));
                getChildren().add(hold);
            }
        }
    }

    private void loadStylesheet(Scene scene) {
        var resource = getClass().getResource("poker.css");
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
            return;
        }
        for (String path : new String[]{"poker.css", "src/poker.css", "../src/poker.css"}) {
            File file = new File(path);
            if (file.exists()) {
                scene.getStylesheets().add(file.toURI().toString());
                return;
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
