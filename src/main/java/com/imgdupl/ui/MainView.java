package com.imgdupl.ui;

import com.imgdupl.core.ImageRecord;
import com.imgdupl.core.PHashEngine;
import com.imgdupl.util.DeletionUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MainView {

    private final Stage stage;
    private final BorderPane root;
    private final PHashEngine hashEngine = new PHashEngine();

    // State
    private final List<ImageRecord> loadedRecords = new ArrayList<>();
    private List<List<ImageRecord>> groups        = new ArrayList<>();

    // UI references
    private StackPane dropZonePane;
    private VBox      groupsContainer;
    private Label     statusLabel;
    private ProgressBar progressBar;
    private Label     progressLabel;
    private VBox      progressBox;
    private ScrollPane resultsScroll;
    private Button    scanButton;
    private Button    clearButton;
    private Label     dropCountLabel;
    private HBox      actionBar;

    // Advanced Preview References
    private StackPane lightboxOverlay;
    private ImageView lightboxImageView;

    public MainView(Stage stage) {
        this.stage = stage;
        this.root  = new BorderPane();
        root.getStyleClass().add("main-root");
        buildUI();
    }

    public BorderPane getRoot() { return root; }

    // ── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
    }

    private Node buildHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        // Logo mark
        Label logo = new Label("⬡");
        logo.getStyleClass().add("logo-mark");

        VBox titleBox = new VBox(2);
        Label title   = new Label("Image Duplicate Detector");
        title.getStyleClass().add("app-title");
        Label sub = new Label("Visually similar image grouping & cleanup");
        sub.getStyleClass().add("app-subtitle");
        titleBox.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Scan button
        scanButton = new Button("⬡  SCAN FOR DUPLICATES");
        scanButton.getStyleClass().add("btn-primary");
        scanButton.setDisable(true);
        scanButton.setOnAction(e -> startScan());

        clearButton = new Button("CLEAR ALL");
        clearButton.getStyleClass().add("btn-ghost");
        clearButton.setVisible(false);
        clearButton.setOnAction(e -> clearAll());

        header.getChildren().addAll(logo, titleBox, spacer, clearButton, scanButton);
        HBox.setMargin(scanButton,  new Insets(0, 24, 0, 8));
        HBox.setMargin(clearButton, new Insets(0, 0,  0, 8));

        return header;
    }

    private Node buildCenter() {
        StackPane center = new StackPane();
        center.getStyleClass().add("center-area");

        // Drop zone (shown when no results yet)
        dropZonePane = buildDropZone();

        // Results area (shown after scan)
        resultsScroll = buildResultsScroll();
        resultsScroll.setVisible(false);

        // Progress overlay
        progressBox = buildProgressOverlay();
        progressBox.setVisible(false);

        // Interactive Full-scale Lightbox Overlay
        StackPane lightbox = buildLightboxOverlay();

        center.getChildren().addAll(dropZonePane, resultsScroll, progressBox, lightbox);
        return center;
    }

    private StackPane buildDropZone() {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("drop-zone");

        VBox inner = new VBox(16);
        inner.setAlignment(Pos.CENTER);

        Label icon      = new Label("⬡");
        icon.getStyleClass().add("drop-icon");

        Label mainText  = new Label("Drop Images Here");
        mainText.getStyleClass().add("drop-title");

        Label subText   = new Label("Drag & drop JPG, PNG, WEBP, BMP, GIF files");
        subText.getStyleClass().add("drop-subtitle");

        dropCountLabel  = new Label("");
        dropCountLabel.getStyleClass().add("drop-count");

        Separator sep   = new Separator();
        sep.setMaxWidth(200);
        sep.getStyleClass().add("drop-sep");

        Button browseBtn = new Button("  Browse Files  ");
        browseBtn.getStyleClass().add("btn-outline");
        browseBtn.setOnAction(e -> browseFiles());

        inner.getChildren().addAll(icon, mainText, subText, dropCountLabel, sep, browseBtn);
        pane.getChildren().add(inner);

        // Drag & Drop handlers
        pane.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                pane.getStyleClass().add("drop-zone-active");
            }
            event.consume();
        });

        pane.setOnDragExited(e -> pane.getStyleClass().remove("drop-zone-active"));

        pane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                addFiles(db.getFiles());
                event.setDropCompleted(true);
            }
            pane.getStyleClass().remove("drop-zone-active");
            event.consume();
        });

        return pane;
    }

    private ScrollPane buildResultsScroll() {
        groupsContainer = new VBox(20);
        groupsContainer.getStyleClass().add("groups-container");
        groupsContainer.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(groupsContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("results-scroll");
        return scroll;
    }

    private VBox buildProgressOverlay() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(400);
        box.setMaxHeight(160);
        box.getStyleClass().add("progress-overlay");

        progressLabel = new Label("Analysing images...");
        progressLabel.getStyleClass().add("progress-label");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("scan-progress-bar");
        progressBar.setPrefWidth(320);

        box.getChildren().addAll(progressLabel, progressBar);
        return box;
    }

    private StackPane buildLightboxOverlay() {
        lightboxOverlay = new StackPane();
        lightboxOverlay.setStyle("-fx-background-color: rgba(14, 14, 17, 0.92);");
        lightboxOverlay.setVisible(false);

        lightboxImageView = new ImageView();
        lightboxImageView.setPreserveRatio(true);
        lightboxImageView.fitWidthProperty().bind(lightboxOverlay.widthProperty().multiply(0.88));
        lightboxImageView.fitHeightProperty().bind(lightboxOverlay.heightProperty().multiply(0.88));

        Label closeHint = new Label("CLICK ANYWHERE TO CLOSE PREVIEW");
        closeHint.setStyle("-fx-text-fill: #55555e; -fx-font-size: 11px; -fx-font-weight: bold; -fx-letter-spacing: 1px;");
        StackPane.setAlignment(closeHint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(closeHint, new Insets(0, 0, 20, 0));

        lightboxOverlay.setOnMouseClicked(e -> lightboxOverlay.setVisible(false));
        lightboxOverlay.getChildren().addAll(lightboxImageView, closeHint);
        return lightboxOverlay;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready — drop images to begin");
        statusLabel.getStyleClass().add("status-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label version = new Label("v1.0.0");
        version.getStyleClass().add("version-label");

        bar.getChildren().addAll(statusLabel, spacer, version);
        return bar;
    }

    // ── File Handling ─────────────────────────────────────────────────────────

    private static final Set<String> SUPPORTED_EXTS = Set.of(
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "tiff", "tif"
    );

    private void browseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Images");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.jpg","*.jpeg","*.png","*.webp","*.bmp","*.gif","*.tiff","*.tif")
        );
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) addFiles(files);
    }

    private void addFiles(List<File> files) {
        Set<String> existing = loadedRecords.stream()
                .map(r -> r.getAbsolutePath())
                .collect(Collectors.toSet());

        int added = 0;
        for (File f : files) {
            String ext = getExt(f.getName());
            if (SUPPORTED_EXTS.contains(ext) && !existing.contains(f.getAbsolutePath())) {
                loadedRecords.add(new ImageRecord(f));
                existing.add(f.getAbsolutePath());
                added++;
            }
        }

        updateDropZoneCount();
        if (!loadedRecords.isEmpty()) {
            scanButton.setDisable(false);
        }

        int finalAdded = added;
        setStatus("Added " + finalAdded + " image(s). Total: " + loadedRecords.size());
    }

    private void updateDropZoneCount() {
        int n = loadedRecords.size();
        if (n == 0) {
            dropCountLabel.setText("");
        } else {
            dropCountLabel.setText(n + " image" + (n == 1 ? "" : "s") + " loaded");
        }
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1).toLowerCase() : "";
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    private void startScan() {
        if (loadedRecords.isEmpty()) return;

        scanButton.setDisable(true);
        progressBox.setVisible(true);
        dropZonePane.setVisible(false);
        resultsScroll.setVisible(false);

        int total = loadedRecords.size();
        AtomicInteger done = new AtomicInteger(0);

        Task<List<List<ImageRecord>>> task = new Task<>() {
            @Override
            protected List<List<ImageRecord>> call() {
                // Step 1: Compute MD5 + pHash for each image
                for (ImageRecord rec : loadedRecords) {
                    if (rec.getMd5() == null) {
                        rec.setMd5(hashEngine.computeMD5(rec.getFile()));
                    }
                    if (rec.getHash() == 0L) {
                        rec.setHash(hashEngine.computeHash(rec.getFile()));
                    }
                    int d = done.incrementAndGet();
                    double prog = (double) d / total * 0.8;
                    updateProgress(prog, 1.0);
                    updateMessage("Analysing: " + rec.getFileName() + " (" + d + "/" + total + ")");
                }

                // Step 2: Group
                updateMessage("Grouping similar images...");
                updateProgress(0.9, 1.0);
                List<List<ImageRecord>> result = hashEngine.groupSimilar(loadedRecords);
                updateProgress(1.0, 1.0);
                return result;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, o, n) ->
                Platform.runLater(() -> progressLabel.setText(n)));

        task.setOnSucceeded(e -> {
            groups = task.getValue();
            progressBar.progressProperty().unbind();
            progressBox.setVisible(false);
            scanButton.setDisable(false);
            clearButton.setVisible(true);
            showResults();
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBox.setVisible(false);
            dropZonePane.setVisible(true);
            scanButton.setDisable(false);
            setStatus("Scan failed: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // ── Results Display ───────────────────────────────────────────────────────

    private void showResults() {
        groupsContainer.getChildren().clear();

        if (groups.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(80));
            Label icon = new Label("✓");
            icon.getStyleClass().add("no-dupes-icon");
            Label msg = new Label("No Duplicates Found");
            msg.getStyleClass().add("no-dupes-title");
            Label sub = new Label("All " + loadedRecords.size() + " images appear to be unique");
            sub.getStyleClass().add("no-dupes-sub");
            empty.getChildren().addAll(icon, msg, sub);
            groupsContainer.getChildren().add(empty);
            setStatus("Scan complete — no duplicates found among " + loadedRecords.size() + " images");
        } else {
            // Summary header
            int totalDupes = groups.stream().mapToInt(g -> g.size() - 1).sum();
            HBox summary = buildSummaryBar(groups.size(), totalDupes);
            groupsContainer.getChildren().add(summary);

            // Render each group
            for (int i = 0; i < groups.size(); i++) {
                Node card = buildGroupCard(i + 1, groups.get(i));
                groupsContainer.getChildren().add(card);
            }

            setStatus("Found " + groups.size() + " duplicate group(s) — " + totalDupes + " redundant image(s)");
        }

        resultsScroll.setVisible(true);
    }

    private HBox buildSummaryBar(int groupCount, int dupeCount) {
        HBox bar = new HBox(32);
        bar.getStyleClass().add("summary-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        bar.getChildren().addAll(
                summaryChip("GROUPS FOUND",     String.valueOf(groupCount)),
                summaryChip("REDUNDANT FILES",  String.valueOf(dupeCount)),
                summaryChip("TOTAL SCANNED",    String.valueOf(loadedRecords.size()))
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button deleteAllBtn = new Button("DELETE ALL DUPLICATES");
        deleteAllBtn.getStyleClass().add("btn-danger");
        deleteAllBtn.setOnAction(e -> showDeleteAllDialog());
        bar.getChildren().addAll(spacer, deleteAllBtn);

        return bar;
    }

    private VBox summaryChip(String label, String value) {
        VBox chip = new VBox(2);
        chip.getStyleClass().add("summary-chip");
        Label val = new Label(value);
        val.getStyleClass().add("chip-value");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("chip-label");
        chip.getChildren().addAll(val, lbl);
        return chip;
    }

    private Node buildGroupCard(int groupNum, List<ImageRecord> group) {
        VBox card = new VBox(0);
        card.getStyleClass().add("group-card");

        // Card header
        HBox header = new HBox(12);
        header.getStyleClass().add("group-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label numLabel = new Label("GROUP " + groupNum);
        numLabel.getStyleClass().add("group-num");

        Label countLabel = new Label(group.size() + " similar images");
        countLabel.getStyleClass().add("group-count");

        // Interactive slider workspace launcher
        Button compareBtn = new Button("⬡ COMPARE GROUP");
        compareBtn.getStyleClass().add("btn-outline");
        compareBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10;");
        compareBtn.setOnAction(e -> openCompareDialog(group));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Ordered by file size (largest = best quality)");
        hint.getStyleClass().add("group-hint");

        header.getChildren().addAll(numLabel, countLabel, compareBtn, spacer, hint);
        card.getChildren().add(header);

        // Image rows
        for (int i = 0; i < group.size(); i++) {
            ImageRecord rec = group.get(i);
            boolean isKeep = (i == 0); // first = best quality
            Node row = buildImageRow(rec, isKeep, group);
            card.getChildren().add(row);
            if (i < group.size() - 1) {
                Separator sep = new Separator();
                sep.getStyleClass().add("row-sep");
                card.getChildren().add(sep);
            }
        }

        return card;
    }

    private Node buildImageRow(ImageRecord rec, boolean isBest, List<ImageRecord> group) {
        HBox row = new HBox(16);
        row.getStyleClass().add("image-row");
        if (isBest) row.getStyleClass().add("image-row-best");
        row.setAlignment(Pos.CENTER_LEFT);

        // Thumbnail UI & Lightbox interaction configuration
        ImageView thumb = new ImageView();
        thumb.getStyleClass().add("img-thumb");
        thumb.setFitWidth(72);
        thumb.setFitHeight(72);
        thumb.setPreserveRatio(true);
        thumb.setCursor(Cursor.HAND);
        thumb.setOnMouseClicked(e -> showLightbox(rec));
        loadThumbnail(rec.getFile(), thumb);

        // File info
        VBox info = new VBox(4);
        info.setMinWidth(240);

        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(rec.getFileName());
        name.getStyleClass().add("file-name");
        if (isBest) {
            Label badge = new Label("BEST");
            badge.getStyleClass().add("badge-best");
            nameRow.getChildren().addAll(name, badge);
        } else {
            nameRow.getChildren().add(name);
        }

        Label path = new Label(truncatePath(rec.getAbsolutePath()));
        path.getStyleClass().add("file-path");
        path.setTooltip(new Tooltip(rec.getAbsolutePath()));

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label size = new Label("⬡ " + rec.getFileSizeStr());
        size.getStyleClass().add("meta-tag");
        Label dims = new Label("⬡ " + rec.getDimensionStr());
        dims.getStyleClass().add("meta-tag");
        metaRow.getChildren().addAll(size, dims);

        info.getChildren().addAll(nameRow, path, metaRow);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button trashBtn = new Button("⬡ Trash");
        trashBtn.getStyleClass().add("btn-action-trash");

        Button permBtn = new Button("✕ Delete");
        permBtn.getStyleClass().add("btn-action-perm");

        trashBtn.setOnAction(e -> confirmDelete(rec, DeletionUtil.DeleteMode.TRASH,     row, group));
        permBtn.setOnAction( e -> confirmDelete(rec, DeletionUtil.DeleteMode.PERMANENT, row, group));

        actions.getChildren().addAll(trashBtn, permBtn);
        row.getChildren().addAll(thumb, info, spacer, actions);
        return row;
    }

    private void showLightbox(ImageRecord rec) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                return new Image(rec.getFile().toURI().toString());
            }
        };
        task.setOnSucceeded(e -> {
            lightboxImageView.setImage(task.getValue());
            lightboxOverlay.setVisible(true);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void openCompareDialog(List<ImageRecord> group) {
        if (group == null || group.isEmpty()) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(this.stage);
        dialog.setTitle("Split-Slider Visual Comparison Studio");
        dialog.setWidth(960);
        dialog.setHeight(740);

        BorderPane dialogLayout = new BorderPane();
        dialogLayout.setStyle("-fx-background-color: #0e0e11; -fx-padding: 20;");

        // Top Bar Workspace controls (Combo dropdown selections)
        HBox topToolbar = new HBox(16);
        topToolbar.setAlignment(Pos.CENTER);
        topToolbar.setStyle("-fx-padding: 0 0 16 0;");

        ObservableList<ImageRecord> recordOptions = FXCollections.observableArrayList(group);
        ComboBox<ImageRecord> leftCombo = new ComboBox<>(recordOptions);
        ComboBox<ImageRecord> rightCombo = new ComboBox<>(recordOptions);

        // Populate initial relative positions
        leftCombo.getSelectionModel().select(0);
        rightCombo.getSelectionModel().select(Math.min(1, group.size() - 1));

        // Styling ComboBoxes (Background & Outer Border)
        leftCombo.setStyle("-fx-background-color: #1a1a1e; -fx-border-color: #2d2d35; -fx-border-radius: 4; -fx-background-radius: 4;");
        rightCombo.setStyle("-fx-background-color: #1a1a1e; -fx-border-color: #2d2d35; -fx-border-radius: 4; -fx-background-radius: 4;");

        // FIX 2: Custom cell factory to override default grey text inside dropdown popup containers
        javafx.util.Callback<ListView<ImageRecord>, ListCell<ImageRecord>> cellFactoryCustom = lv -> new ListCell<>() {
            @Override
            protected void updateItem(ImageRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getFileName());
                    setTextFill(javafx.scene.paint.Color.WHITE); // Crisp white text inside popup row cells
                }
            }
        };
        leftCombo.setCellFactory(cellFactoryCustom);
        rightCombo.setCellFactory(cellFactoryCustom);

        // Custom list cells for the collapsed selection button display text
        javafx.util.Callback<ComboBox<ImageRecord>, ListCell<ImageRecord>> buttonCellFactory = cb -> new ListCell<>() {
            @Override
            protected void updateItem(ImageRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getFileName());
                    setTextFill(javafx.scene.paint.Color.WHITE); // Crisp white text inside selected display button
                }
            }
        };
        leftCombo.setButtonCell(buttonCellFactory.call(leftCombo));
        rightCombo.setButtonCell(buttonCellFactory.call(rightCombo));

        Label dividerIndicator = new Label("VS");
        dividerIndicator.setStyle("-fx-text-fill: #e8c547; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label labelLeft = new Label("Left View:");
        labelLeft.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold;");
        Label labelRight = new Label("Right View:");
        labelRight.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        topToolbar.getChildren().addAll(labelLeft, leftCombo, dividerIndicator, labelRight, rightCombo);
        dialogLayout.setTop(topToolbar);

        // Core Image View Comparison Workspace Setup
        Pane renderStack = new Pane();
        renderStack.setStyle("-fx-background-color: #141417; -fx-border-color: #26262b; -fx-border-width: 1;");

        ImageView baseRightView = new ImageView();
        ImageView layerLeftView = new ImageView();
        baseRightView.setPreserveRatio(true);
        layerLeftView.setPreserveRatio(true);

        // Keep unmanaged to eliminate the automatic loop scaling bug
        baseRightView.setManaged(false);
        layerLeftView.setManaged(false);

        // Linear Wipe Processing via Geometric Clipping Mask
        Rectangle clippingMask = new Rectangle();
        layerLeftView.setClip(clippingMask);

        Slider trackingSlider = new Slider(0, 100, 50);
        trackingSlider.setStyle("-fx-padding: 12 0 4 0;");

        // Industrial Gold Divider UI Line Integration
        Line visualDivider = new Line();
        visualDivider.setStroke(javafx.scene.paint.Color.web("#e8c547"));
        visualDivider.setStrokeWidth(1.5);

        renderStack.getChildren().addAll(baseRightView, layerLeftView, visualDivider);
        dialogLayout.setCenter(renderStack);
        dialogLayout.setBottom(trackingSlider);

        // FIX 1: Explicitly calculates layout geometry, centering the images inside the canvas wrapper
        Runnable updateLayoutWipe = () -> {
            double w = renderStack.getWidth();
            double h = renderStack.getHeight();
            if (w <= 0 || h <= 0) return;

            Image leftImg = layerLeftView.getImage();
            Image rightImg = baseRightView.getImage();
            Image masterImg = (leftImg != null && leftImg.getWidth() > 0) ? leftImg : rightImg;

            if (masterImg != null && masterImg.getWidth() > 0 && masterImg.getHeight() > 0) {
                double imgW = masterImg.getWidth();
                double imgH = masterImg.getHeight();

                // Standard uniform aspect-ratio scale constraint
                double scale = Math.min(w / imgW, h / imgH);
                double targetW = imgW * scale;
                double targetH = imgH * scale;

                // Derive absolute offsets to achieve true viewport centering
                double x = (w - targetW) / 2.0;
                double y = (h - targetH) / 2.0;

                // Position and resize viewports
                layerLeftView.setLayoutX(x);
                layerLeftView.setLayoutY(y);
                layerLeftView.setFitWidth(targetW);
                layerLeftView.setFitHeight(targetH);

                baseRightView.setLayoutX(x);
                baseRightView.setLayoutY(y);
                baseRightView.setFitWidth(targetW);
                baseRightView.setFitHeight(targetH);

                // Slider tracking calculations
                double percent = trackingSlider.getValue() / 100.0;
                double splitX = w * percent;

                // Adjust clip mask width relative to the image's layout offset
                double localSplitX = splitX - x;
                clippingMask.setWidth(Math.max(0, Math.min(targetW, localSplitX)));
                clippingMask.setHeight(targetH);

                // Bound visual indicator line directly inside the graphic envelope boundaries
                visualDivider.setStartX(splitX);
                visualDivider.setEndX(splitX);
                visualDivider.setStartY(y);
                visualDivider.setEndY(y + targetH);
            } else {
                // Instantly fallback to full workspace boundaries if images haven't initialized
                double percent = trackingSlider.getValue() / 100.0;
                double splitX = w * percent;

                layerLeftView.setLayoutX(0);
                layerLeftView.setLayoutY(0);
                layerLeftView.setFitWidth(w);
                layerLeftView.setFitHeight(h);
                baseRightView.setLayoutX(0);
                baseRightView.setLayoutY(0);
                baseRightView.setFitWidth(w);
                baseRightView.setFitHeight(h);

                clippingMask.setWidth(splitX);
                clippingMask.setHeight(h);

                visualDivider.setStartX(splitX);
                visualDivider.setEndX(splitX);
                visualDivider.setStartY(0);
                visualDivider.setEndY(h);
            }
        };

        // Attach layout triggers for real-time dimension shifts
        trackingSlider.valueProperty().addListener((obs, oldV, newV) -> updateLayoutWipe.run());
        renderStack.widthProperty().addListener((obs, oldV, newV) -> updateLayoutWipe.run());
        renderStack.heightProperty().addListener((obs, oldV, newV) -> updateLayoutWipe.run());

        // Recalculate whenever background async image load processes finish loading
        layerLeftView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                newImg.widthProperty().addListener((o, ov, nv) -> Platform.runLater(updateLayoutWipe));
                newImg.heightProperty().addListener((o, ov, nv) -> Platform.runLater(updateLayoutWipe));
            }
        });
        baseRightView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                newImg.widthProperty().addListener((o, ov, nv) -> Platform.runLater(updateLayoutWipe));
                newImg.heightProperty().addListener((o, ov, nv) -> Platform.runLater(updateLayoutWipe));
            }
        });

        // Image updating engine execution mapping logic
        Runnable processingEngine = () -> {
            ImageRecord targetLeft  = leftCombo.getValue();
            ImageRecord targetRight = rightCombo.getValue();
            if (targetLeft != null) {
                layerLeftView.setImage(new Image(targetLeft.getFile().toURI().toString(), 0, 1200, true, true, true));
            }
            if (targetRight != null) {
                baseRightView.setImage(new Image(targetRight.getFile().toURI().toString(), 0, 1200, true, true, true));
            }
            Platform.runLater(updateLayoutWipe);
        };

        leftCombo.setOnAction(e -> processingEngine.run());
        rightCombo.setOnAction(e -> processingEngine.run());

        processingEngine.run(); // Initialize baseline

        Scene dialogScene = new Scene(dialogLayout);
        if (getClass().getResource("/styles.css") != null) {
            dialogScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        }
        dialog.setScene(dialogScene);
        dialog.show();
    }

    private void confirmDelete(ImageRecord rec, DeletionUtil.DeleteMode mode, HBox row, List<ImageRecord> group) {
        String modeStr = mode == DeletionUtil.DeleteMode.TRASH ? "move to Trash" : "permanently delete";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("This will " + modeStr + ":\n" + rec.getFileName());
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("custom-dialog");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean ok = DeletionUtil.delete(rec.getFile(), mode);
            if (ok) {
                group.remove(rec);
                loadedRecords.remove(rec);
                // Animate removal
                row.setVisible(false);
                row.setManaged(false);
                setStatus("Deleted: " + rec.getFileName());
            } else {
                showError("Could not delete " + rec.getFileName());
            }
        }
    }

    private void showDeleteAllDialog() {
        // Collect all non-best (duplicate) records
        List<ImageRecord> toDelete = new ArrayList<>();
        for (List<ImageRecord> group : groups) {
            for (int i = 1; i < group.size(); i++) {
                toDelete.add(group.get(i));
            }
        }
        if (toDelete.isEmpty()) return;

        Alert mode = new Alert(Alert.AlertType.CONFIRMATION);
        mode.setTitle("Delete All Duplicates");
        mode.setHeaderText("Delete " + toDelete.size() + " duplicate file(s)?");
        mode.setContentText("Choose deletion method:");
        mode.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());

        ButtonType trashAll = new ButtonType("Move All to Trash");
        ButtonType permAll  = new ButtonType("Permanently Delete All");
        ButtonType cancel   = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        mode.getButtonTypes().setAll(trashAll, permAll, cancel);

        Optional<ButtonType> result = mode.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return;

        DeletionUtil.DeleteMode deleteMode = (result.get() == trashAll)
                ? DeletionUtil.DeleteMode.TRASH
                : DeletionUtil.DeleteMode.PERMANENT;

        int deleted = 0;
        for (ImageRecord rec : toDelete) {
            if (DeletionUtil.delete(rec.getFile(), deleteMode)) {
                loadedRecords.remove(rec);
                deleted++;
            }
        }

        int finalDeleted = deleted;
        setStatus("Deleted " + finalDeleted + " duplicate file(s)");
        // Refresh the groups list and re-scan
        groups.clear();
        showResults();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadThumbnail(File file, ImageView view) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                return new Image(file.toURI().toString(), 144, 144, true, true, false);
            }
        };
        task.setOnSucceeded(e -> view.setImage(task.getValue()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private String truncatePath(String path) {
        if (path.length() <= 60) return path;
        return "..." + path.substring(path.length() - 57);
    }

    private void clearAll() {
        loadedRecords.clear();
        groups.clear();
        groupsContainer.getChildren().clear();
        resultsScroll.setVisible(false);
        dropZonePane.setVisible(true);
        scanButton.setDisable(true);
        clearButton.setVisible(false);
        dropCountLabel.setText("");
        setStatus("Ready — drop images to begin");
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Error");
        a.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        a.showAndWait();
    }
}