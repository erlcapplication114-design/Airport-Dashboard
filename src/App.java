import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Group 12 - Airport Operations : Flight information board.
 * Composants de présentation : Label (cellules), HBox (une ligne), VBox (le
 * tableau).
 */
public class App extends Application {

    // ====================== Statuts ======================
    private static final String ON_TIME = "ON TIME";
    private static final String DELAYED = "DELAYED";
    private static final String BOARDING = "BOARDING";
    private static final String DEPARTED = "DEPARTED";

    // ====================== Style (CSS JavaFX) ======================
    // Fort contraste : fond quasi noir, texte blanc / couleurs vives.
    private static final String BG = "#0b0f1a";
    private static final String BG_ALT = "#151c30"; // lignes paires (zébrage)
    private static final String BG_HL = "#7a5a00"; // surbrillance changement de porte
    private static final String TXT = "#ffffff";
    private static final String DIM = "#8a94a6";
    private static final String GREEN = "#3ddc84";
    private static final String RED = "#ff6b6b";
    private static final String YELLOW = "#ffd400";

    private static final String FONT = "-fx-font-family: 'Segoe UI', Arial, sans-serif; ";
    // Grande police (26 px) pour rester lisible à 5 mètres
    private static final String CELL = FONT + "-fx-font-size: 26px; -fx-text-fill: " + TXT + "; ";

    // ====================== Largeurs des colonnes ======================
    private static final double W_FLIGHT = 160;
    private static final double W_DEST = 380;
    private static final double W_TIME = 270;
    private static final double W_GATE = 120;
    private static final double W_STATUS = 270;
    private static final double ROW_H = 46;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ====================== Données simulées ======================
    private static final String[] DESTINATIONS = {
            "Alger", "Paris Orly", "Oran", "Marseille", "Istanbul", "Dubaï", "Madrid",
            "Londres Heathrow", "Rome Fiumicino", "Tunis", "Casablanca", "Le Caire", "Lyon",
            "Francfort-sur-le-Main", "Doha", "Barcelone", "Montréal-Trudeau", "Genève",
            "Bruxelles", "Constantine"
    };
    private static final String[] EXTRA_DESTINATIONS = {
            "Vienne", "Lisbonne", "Amsterdam", "Berlin", "Athènes", "Jeddah", "Munich", "Milan Malpensa"
    };
    private static final String[] AIRLINES = { "AH", "AF", "TK", "IB", "BA", "EK" };

    private final Random rnd = new Random();
    private final List<FlightRow> rows = new ArrayList<>();
    private final VBox board = new VBox(2); // VBox : le tableau = pile de lignes
    private final Label clock = new Label();
    private ScrollPane scroll;
    private Timeline tick;
    private int created = 0; // compteur de lignes créées (zébrage)
    private int extraIdx = 0;

    // =====================================================================
    // Modèle : un vol (propriétés observables => binding avec les Label)
    // =====================================================================
    static class Flight {
        final StringProperty code = new SimpleStringProperty();
        final StringProperty destination = new SimpleStringProperty();
        final StringProperty time = new SimpleStringProperty(); // heure prévue
        final StringProperty newTime = new SimpleStringProperty(""); // nouvelle heure (retard)
        final StringProperty gate = new SimpleStringProperty();
        final StringProperty status = new SimpleStringProperty(ON_TIME);
        final LocalTime scheduled;
        int delay = 0;

        Flight(String code, String destination, LocalTime scheduled, String gate) {
            this.code.set(code);
            this.destination.set(destination);
            this.scheduled = scheduled;
            this.time.set(scheduled.format(HM));
            this.gate.set(gate);
        }

        /**
         * Règle d'alarme : un retard affiche la nouvelle heure à côté de l'ancienne.
         */
        void addDelay(int minutes) {
            delay += minutes;
            newTime.set(scheduled.plusMinutes(delay).format(HM));
            status.set(DELAYED);
        }
    }

    // =====================================================================
    // Vue : une ligne du tableau = un HBox de Label
    // =====================================================================
    static class FlightRow extends HBox {
        final Flight flight;
        private final String base;
        private final Label status = cell(W_STATUS, Pos.CENTER_LEFT, "-fx-font-weight: bold;");
        private Timeline blink;
        private boolean lit = false;

        FlightRow(Flight f, int index) {
            this.flight = f;
            this.base = (index % 2 == 0) ? BG : BG_ALT;
            setAlignment(Pos.CENTER_LEFT);
            setMinHeight(ROW_H);
            setPadding(new Insets(4, 16, 4, 16));
            paint();

            // --- Cellule 1 : n° de vol
            Label code = cell(W_FLIGHT, Pos.CENTER_LEFT, "-fx-font-weight: bold;");
            code.textProperty().bind(f.code);

            // --- Cellule 2 : destination (retour à la ligne si trop longue)
            Label dest = cell(W_DEST, Pos.CENTER_LEFT, "");
            dest.setWrapText(true);
            dest.textProperty().bind(f.destination);

            // --- Cellule 3 : heure (ancienne barrée + nouvelle à côté)
            Label oldT = new Label();
            oldT.textProperty().bind(f.time);
            oldT.styleProperty().bind(Bindings.createStringBinding(
                    () -> CELL + (f.newTime.get().isEmpty()
                            ? "-fx-font-weight: bold;"
                            : "-fx-text-fill: " + DIM + ";"),
                    f.newTime));
            Line strike = new Line();
            strike.endXProperty().bind(oldT.widthProperty());
            strike.setStroke(Color.web(DIM));
            strike.setStrokeWidth(3);
            strike.visibleProperty().bind(f.newTime.isNotEmpty());
            StackPane oldBox = new StackPane(oldT, strike);
            oldBox.setAlignment(Pos.CENTER_LEFT);
            oldBox.setMinWidth(105);
            oldBox.setPrefWidth(105);
            oldBox.setMaxWidth(105);

            Label newT = new Label();
            newT.setStyle(CELL + "-fx-font-weight: bold; -fx-text-fill: " + RED + ";");
            newT.textProperty().bind(f.newTime);
            newT.visibleProperty().bind(f.newTime.isNotEmpty());
            newT.managedProperty().bind(newT.visibleProperty());

            HBox timeBox = new HBox(10, oldBox, newT);
            timeBox.setAlignment(Pos.CENTER_LEFT);
            timeBox.setMinWidth(W_TIME);
            timeBox.setPrefWidth(W_TIME);
            timeBox.setMaxWidth(W_TIME);

            // --- Cellule 4 : porte
            Label gate = cell(W_GATE, Pos.CENTER, "-fx-font-weight: bold;");
            gate.textProperty().bind(f.gate);

            // --- Cellule 5 : statut (texte + pastille de couleur = redondance)
            f.status.addListener((o, a, b) -> applyStatus());
            applyStatus();

            // --- Objectif bonus : changement de porte => la ligne clignote 5 s
            f.gate.addListener((o, a, b) -> blink());

            getChildren().addAll(code, dest, timeBox, gate, status);
        }

        private void paint() {
            setStyle("-fx-background-color: " + (lit ? BG_HL : base) + ";");
        }

        private void applyStatus() {
            String s = flight.status.get();
            String color = statusColor(s);
            status.setText(s);
            status.setStyle(CELL + "-fx-font-weight: bold; -fx-text-fill: " + color + ";");
            status.setGraphic(new Circle(11, Color.web(color)));
            status.setGraphicTextGap(12);
            setOpacity(DEPARTED.equals(s) ? 0.55 : 1.0);
        }

        /** Clignotement : 10 bascules x 500 ms = 5 secondes. */
        private void blink() {
            if (blink != null) {
                blink.stop();
            }
            lit = true;
            paint();
            blink = new Timeline(new KeyFrame(Duration.millis(500), e -> {
                lit = !lit;
                paint();
            }));
            blink.setCycleCount(10);
            blink.setOnFinished(e -> {
                lit = false;
                paint();
            });
            blink.play();
        }
    }

    private static String statusColor(String s) {
        switch (s) {
            case ON_TIME:
                return GREEN;
            case DELAYED:
                return RED;
            case BOARDING:
                return YELLOW;
            default:
                return DIM;
        }
    }

    /**
     * Cellule standard : Label de largeur fixe pour garder les colonnes alignées.
     */
    private static Label cell(double width, Pos align, String extraStyle) {
        Label l = new Label();
        l.setMinWidth(width);
        l.setPrefWidth(width);
        l.setMaxWidth(width);
        l.setAlignment(align);
        l.setStyle(CELL + extraStyle);
        return l;
    }

    private static Label headCell(String text, double width, Pos align) {
        Label l = cell(width, align,
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #9fc1ff;");
        l.setText(text);
        return l;
    }

    // =====================================================================
    // Application
    // =====================================================================
    @Override
    public void start(Stage stage) {
        // ---- Barre de titre + horloge
        Label title = new Label("FLIGHT INFORMATION BOARD");
        title.setStyle(FONT + "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + YELLOW + ";");
        clock.setStyle(FONT + "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + TXT + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleBar = new HBox(title, spacer, clock);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(12, 16, 12, 16));
        titleBar.setStyle("-fx-background-color: #000000;");

        // ---- En-tête des colonnes (mêmes largeurs que les lignes)
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 16, 6, 16));
        header.setStyle("-fx-background-color: #1d2b4a;");
        header.getChildren().addAll(
                headCell("FLIGHT", W_FLIGHT, Pos.CENTER_LEFT),
                headCell("DESTINATION", W_DEST, Pos.CENTER_LEFT),
                headCell("TIME", W_TIME, Pos.CENTER_LEFT),
                headCell("GATE", W_GATE, Pos.CENTER),
                headCell("STATUS", W_STATUS, Pos.CENTER_LEFT));

        // ---- Le tableau (VBox) dans un ScrollPane
        board.setStyle("-fx-background-color: " + BG + ";");
        scroll = new ScrollPane(board);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");

        initFlights();

        // ---- Barre de contrôle (pour la démo)
        Button addBtn = new Button("+ Add flight");
        addBtn.setOnAction(e -> addFlight());

        Button delayBtn = new Button("Force delay");
        delayBtn.setOnAction(e -> {
            FlightRow r = pickRow();
            if (r != null) {
                r.flight.addDelay(5 * (2 + rnd.nextInt(10)));
            }
        });

        Button gateBtn = new Button("Change gate");
        gateBtn.setOnAction(e -> {
            FlightRow r = pickRow();
            if (r != null) {
                changeGate(r.flight);
            }
        });

        ToggleButton pauseBtn = new ToggleButton("Pause");
        pauseBtn.selectedProperty().addListener((o, was, paused) -> {
            if (paused) {
                tick.pause();
            } else {
                tick.play();
            }
        });

        Slider spacing = new Slider(0, 20, 2);
        spacing.setPrefWidth(160);
        board.spacingProperty().bind(spacing.valueProperty()); // démo : espacement du VBox
        Label spacingLbl = new Label();
        spacingLbl.setStyle(FONT + "-fx-font-size: 16px; -fx-text-fill: " + TXT + ";");
        spacingLbl.textProperty().bind(Bindings.format("Spacing: %.0f px", spacing.valueProperty()));

        HBox controls = new HBox(12, addBtn, delayBtn, gateBtn, pauseBtn, spacingLbl, spacing);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(10, 16, 10, 16));
        controls.setStyle("-fx-background-color: #101626;");

        // ---- Scène
        BorderPane root = new BorderPane();
        root.setTop(new VBox(titleBar, header));
        root.setCenter(scroll);
        root.setBottom(controls);
        root.setStyle("-fx-background-color: " + BG + ";");

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root,
                Math.min(1290, vb.getWidth()),
                Math.min(860, vb.getHeight() - 40)); // marge pour la barre de titre
        stage.setTitle("IHM APP - Flight board");
        stage.setScene(scene);
        stage.setX(vb.getMinX());
        stage.setY(vb.getMinY());
        stage.show();

        // ---- Mise à jour toutes les 3 secondes + horloge chaque seconde
        tick = new Timeline(new KeyFrame(Duration.seconds(3), e -> tick()));
        tick.setCycleCount(Animation.INDEFINITE);
        tick.play();

        updateClock();
        Timeline clockTl = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTl.setCycleCount(Animation.INDEFINITE);
        clockTl.play();
    }

    private void updateClock() {
        clock.setText(LocalTime.now().format(HMS));
    }

    // =====================================================================
    // Simulation
    // =====================================================================
    /**
     * 20 vols : quelques DEPARTED / BOARDING, le reste ON TIME, 3 retards
     * aléatoires.
     */
    private void initFlights() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        LocalTime start = now.minusMinutes(now.getMinute() % 5).minusMinutes(20);
        for (int i = 0; i < 20; i++) {
            String code = String.format("%s %03d", AIRLINES[i % AIRLINES.length], 100 + (i * 137) % 900);
            Flight f = new Flight(code, DESTINATIONS[i], start.plusMinutes(i * 12L), randomGate());
            if (i < 2) {
                f.status.set(DEPARTED);
            } else if (i < 5) {
                f.status.set(BOARDING);
            }
            appendRow(f);
        }
        for (int k = 0; k < 3; k++) { // retards ajoutés aléatoirement
            FlightRow r = rows.get(5 + rnd.nextInt(15));
            if (r.flight.delay == 0) {
                r.flight.addDelay(delayMinutes());
            }
        }
    }

    private void appendRow(Flight f) {
        FlightRow row = new FlightRow(f, created++);
        rows.add(row);
        board.getChildren().add(row); // VBox : ajout d'une nouvelle ligne
    }

    private void addFlight() {
        LocalTime last = rows.get(rows.size() - 1).flight.scheduled;
        String dest = EXTRA_DESTINATIONS[extraIdx++ % EXTRA_DESTINATIONS.length];
        String code = String.format("%s %03d", AIRLINES[rnd.nextInt(AIRLINES.length)], 100 + rnd.nextInt(900));
        appendRow(new Flight(code, dest, last.plusMinutes(12), randomGate()));
        if (rows.size() > 26) { // on garde le tableau d'une taille raisonnable
            rows.remove(0);
            board.getChildren().remove(0);
        }
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private int delayMinutes() {
        return 5 * (2 + rnd.nextInt(10)); // 10 à 55 minutes
    }

    private String randomGate() {
        return "" + (char) ('A' + rnd.nextInt(4)) + (1 + rnd.nextInt(20));
    }

    private void changeGate(Flight f) {
        String g;
        do {
            g = randomGate();
        } while (g.equals(f.gate.get()));
        f.gate.set(g);
    }

    /** Choisit au hasard une ligne dont le vol n'est pas encore parti. */
    private FlightRow pickRow() {
        List<FlightRow> active = new ArrayList<>();
        for (FlightRow r : rows) {
            if (!DEPARTED.equals(r.flight.status.get())) {
                active.add(r);
            }
        }
        return active.isEmpty() ? null : active.get(rnd.nextInt(active.size()));
    }

    /** Un événement aléatoire toutes les 3 secondes. */
    private void tick() {
        FlightRow r = pickRow();
        if (r == null || rows.size() - countDeparted() < 8) {
            addFlight();
            return;
        }
        Flight f = r.flight;
        int d = rnd.nextInt(100);
        switch (f.status.get()) {
            case ON_TIME:
                if (d < 40) {
                    f.addDelay(delayMinutes());
                } else if (d < 65) {
                    f.status.set(BOARDING);
                } else if (d < 85) {
                    changeGate(f);
                }
                break;
            case DELAYED:
                if (d < 35) {
                    f.status.set(BOARDING);
                } else if (d < 55) {
                    changeGate(f);
                } else if (d < 70) {
                    f.addDelay(5 * (1 + rnd.nextInt(4)));
                }
                break;
            case BOARDING:
                if (d < 50) {
                    f.status.set(DEPARTED);
                } else if (d < 70) {
                    changeGate(f);
                }
                break;
            default:
                break;
        }
    }

    private int countDeparted() {
        int n = 0;
        for (FlightRow r : rows) {
            if (DEPARTED.equals(r.flight.status.get())) {
                n++;
            }
        }
        return n;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
