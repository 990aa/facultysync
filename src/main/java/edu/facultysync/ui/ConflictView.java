package edu.facultysync.ui;

import edu.facultysync.model.ConflictResult;
import edu.facultysync.model.Location;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Conflict tab view responsible for listing conflict analysis results.
 */
public class ConflictView {

    private final VBox root;
    private final TableView<ConflictResult> conflictTable = new TableView<>();
    private final Label conflictSummaryLabel = new Label();

    /**
     * Creates a conflict view with a callback used for manual reassignment actions.
     *
     * @param onReassign callback invoked when a conflict row is double-clicked
     */
    public ConflictView(Consumer<ConflictResult> onReassign) {
        this.root = buildView(onReassign);
    }

    /**
     * Returns the root JavaFX node to be embedded in the Conflicts tab.
     */
    public VBox getView() {
        return root;
    }

    /**
     * Returns the conflict table for data refreshes and interaction wiring.
     */
    public TableView<ConflictResult> getConflictTable() {
        return conflictTable;
    }

    /**
     * Returns the summary label that reports conflict counts and status.
     */
    public Label getConflictSummaryLabel() {
        return conflictSummaryLabel;
    }

    @SuppressWarnings("unchecked")
    private VBox buildView(Consumer<ConflictResult> onReassign) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        conflictSummaryLabel.setId("conflictSummaryLabel");

        TableColumn<ConflictResult, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSeverity().name()));
        sevCol.setPrefWidth(130);
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    Rectangle indicator = new Rectangle(12, 12);
                    indicator.setArcWidth(3);
                    indicator.setArcHeight(3);
                    if ("HARD_OVERLAP".equals(item)) {
                        indicator.setFill(Color.web("#e74c3c"));
                    } else if ("PROFESSOR_OVERLAP".equals(item)) {
                        indicator.setFill(Color.web("#c0392b"));
                    } else {
                        indicator.setFill(Color.web("#f39c12"));
                    }
                    setGraphic(indicator);
                } else {
                    setGraphic(null);
                }
            }
        });

        TableColumn<ConflictResult, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDescription()));

        TableColumn<ConflictResult, String> altCol = new TableColumn<>("Alternatives");
        altCol.setCellValueFactory(cd -> {
            List<Location> alts = cd.getValue().getAvailableAlternatives();
            if (alts == null || alts.isEmpty()) return new SimpleStringProperty("None");
            StringBuilder sb = new StringBuilder();
            for (Location l : alts) sb.append(l.getDisplayName()).append("; ");
            return new SimpleStringProperty(sb.toString());
        });

        conflictTable.getColumns().addAll(sevCol, descCol, altCol);
        conflictTable.setId("conflictTable");
        conflictTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label emptyLabel = new Label("\u2705  No conflicts detected.\nClick 'Analyze Conflicts' to scan for scheduling issues.");
        emptyLabel.getStyleClass().add("empty-state-label");
        emptyLabel.setWrapText(true);
        conflictTable.setPlaceholder(emptyLabel);

        conflictTable.setRowFactory(tv -> {
            TableRow<ConflictResult> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onReassign.accept(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(conflictTable, Priority.ALWAYS);
        box.getChildren().addAll(conflictSummaryLabel, conflictTable);
        return box;
    }
}
