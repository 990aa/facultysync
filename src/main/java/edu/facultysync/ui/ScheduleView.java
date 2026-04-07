package edu.facultysync.ui;

import edu.facultysync.model.ScheduledEvent;
import edu.facultysync.util.TimePolicy;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Schedule tab view responsible for presenting and editing scheduled events.
 */
public class ScheduleView {

    private final VBox root;
    private final TableView<ScheduledEvent> eventTable = new TableView<>();

    /**
     * Creates a schedule view with callbacks for row-level edit/delete actions.
     *
     * @param onEditEvent callback invoked when the user requests event editing
     * @param onDeleteEvent callback invoked when the user requests event deletion
     */
    public ScheduleView(Consumer<ScheduledEvent> onEditEvent, Consumer<ScheduledEvent> onDeleteEvent) {
        this.root = buildView(onEditEvent, onDeleteEvent);
    }

    /**
     * Returns the root JavaFX node to be embedded in the Schedule tab.
     */
    public VBox getView() {
        return root;
    }

    /**
     * Returns the backing event table for data refresh and selection operations.
     */
    public TableView<ScheduledEvent> getEventTable() {
        return eventTable;
    }

    @SuppressWarnings("unchecked")
    private VBox buildView(Consumer<ScheduledEvent> onEditEvent, Consumer<ScheduledEvent> onDeleteEvent) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        TableColumn<ScheduledEvent, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getCourseCode() != null
                        ? cd.getValue().getCourseCode() : ""));

        TableColumn<ScheduledEvent, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEventType() != null
                        ? cd.getValue().getEventType().getDisplay() : ""));

        TableColumn<ScheduledEvent, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getLocationName() != null
                        ? cd.getValue().getLocationName() : "Online"));

        TableColumn<ScheduledEvent, String> profCol = new TableColumn<>("Professor");
        profCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getProfessorName() != null
                        ? cd.getValue().getProfessorName() : ""));

        TableColumn<ScheduledEvent, String> startCol = new TableColumn<>("Start (UTC)");
        startCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStartEpoch() != null
                        ? TimePolicy.formatEpoch(cd.getValue().getStartEpoch()) : ""));

        TableColumn<ScheduledEvent, String> endCol = new TableColumn<>("End (UTC)");
        endCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEndEpoch() != null
                        ? TimePolicy.formatEpoch(cd.getValue().getEndEpoch()) : ""));

        TableColumn<ScheduledEvent, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDurationMinutes() + " min"));

        eventTable.getColumns().addAll(courseCol, typeCol, locCol, profCol, startCol, endCol, durCol);
        eventTable.setId("eventTable");
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label emptyLabel = new Label("\uD83D\uDCED  No events scheduled.\nImport a CSV or add events manually to get started!");
        emptyLabel.getStyleClass().add("empty-state-label");
        emptyLabel.setWrapText(true);
        eventTable.setPlaceholder(emptyLabel);

        eventTable.setRowFactory(tv -> {
            TableRow<ScheduledEvent> row = new TableRow<>();

            MenuItem editItem = new MenuItem("Edit Event");
            editItem.setOnAction(e -> onEditEvent.accept(row.getItem()));

            MenuItem deleteItem = new MenuItem("Delete Event");
            deleteItem.setOnAction(e -> onDeleteEvent.accept(row.getItem()));

            ContextMenu menu = new ContextMenu(editItem, deleteItem);
            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> row.setContextMenu(isEmpty ? null : menu));
            row.setContextMenu(row.isEmpty() ? null : menu);

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onEditEvent.accept(row.getItem());
                }
            });

            return row;
        });

        VBox.setVgrow(eventTable, Priority.ALWAYS);
        box.getChildren().add(eventTable);
        return box;
    }
}
