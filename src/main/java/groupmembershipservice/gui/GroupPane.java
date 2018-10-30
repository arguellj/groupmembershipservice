package groupmembershipservice.gui;

import groupmembershipservice.GroupListener;
import groupmembershipservice.MulticastPeer;
import groupmembershipservice.View;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class GroupPane implements Initializable, GroupListener {

    private String group;

    private MulticastPeer server;

    private ObservableList<View> viewList = FXCollections.observableArrayList();

    private Tab groupTab0;

    @FXML
    private ListView<View> groupView0;

    @FXML
    private Button leaveBtn0;

    @FXML
    private Button killBtn0;

    @FXML
    private Button partitionBtn;

    @FXML
    private TextArea partitionTxt;

    @FXML
    private Button duplicateViewBtn;

    @FXML
    private Button lostViewBtn;

    @FXML
    private Label pendingViewDuplicateLabel;

    @FXML
    private Label pendingViewLostLabel;

    @FXML
    private Button duplicateConsensusBtn;

    @FXML
    private Button lostConsensusBtn;

    @FXML
    private Label pendingConsensusDuplicateLabel;

    @FXML
    private Label pendingConsensusLostLabel;

    public GroupPane() {
    }

    public void initServer(MulticastPeer server, String group) throws IOException {
        this.group = group;
        this.server = server;
        this.server.addListener(this);
        this.server.joinGroup(group);
    }

    public void setGroupTab0(Tab groupTab0) {
        this.groupTab0 = groupTab0;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        groupView0.setItems(viewList);
    }

    @FXML
    private void leaveGroup(ActionEvent event) throws IOException {
        server.leaveGroup();
        groupTab0.setText(groupTab0.getText() + " - Left");
        leaveBtn0.setDisable(true);
        killBtn0.setDisable(true);
        lostViewBtn.setDisable(true);
        duplicateViewBtn.setDisable(true);
        lostConsensusBtn.setDisable(true);
        duplicateConsensusBtn.setDisable(true);
        partitionTxt.setDisable(true);
        partitionBtn.setDisable(true);
        groupView0.setDisable(true);
    }

    @FXML
    private void killProcess(ActionEvent event) {
        final String originalTitle = groupTab0.getText();
        groupTab0.setText(originalTitle + " - Killing");
        leaveBtn0.setDisable(true);
        killBtn0.setDisable(true);
        lostViewBtn.setDisable(true);
        duplicateViewBtn.setDisable(true);
        lostConsensusBtn.setDisable(true);
        duplicateConsensusBtn.setDisable(true);
        partitionTxt.setDisable(true);
        partitionBtn.setDisable(true);
        server.kill();
        groupTab0.setText(originalTitle + " - Restoring");
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                //Ignore
            }

            Platform.runLater(() -> {
                try {
                    server = new MulticastPeer(8000, this.server.getMemberId());
                    server.addListener(this);
                    server.joinGroup(group);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                groupTab0.setText(originalTitle);
                leaveBtn0.setDisable(false);
                killBtn0.setDisable(false);
                lostViewBtn.setDisable(false);
                duplicateViewBtn.setDisable(false);
                lostConsensusBtn.setDisable(false);
                duplicateConsensusBtn.setDisable(false);
                partitionTxt.setDisable(false);
                partitionBtn.setDisable(false);
            });
        });
    }

    @FXML
    private void createPartition(ActionEvent event) throws IOException {
        String[] membersToBlock = partitionTxt.getText().split(";");
        server.blockMembers(membersToBlock);
    }

    @FXML
    private void duplicateViewMessage(ActionEvent event) {
        this.server.duplicateViewMessage();
    }

    @FXML
    private void loseViewMessage(ActionEvent event) {
        this.server.loseViewMessage();
    }

    @FXML
    private void duplicateConsensusMessage(ActionEvent event) {
        this.server.duplicateConsensusMessage();
    }

    @FXML
    private void loseConsensusMessage(ActionEvent event) {
        this.server.loseConsensusMessage();
    }

    @Override
    public void updatedView(View view) {
        Platform.runLater(() -> {
            try {
                viewList.add(view);
            } catch (NullPointerException e) {
                // Ignore
            }
        });
    }

    @Override
    public void updateDuplicateViewMessages() {
        Platform.runLater(() ->
                this.pendingViewDuplicateLabel.setText("Pending: " + this.server.getPendingDuplicatedViewMessages().get())
        );
    }

    @Override
    public void updateLostViewMessages() {
        Platform.runLater(() ->
                this.pendingViewLostLabel.setText("Pending: " + this.server.getPendingLostViewMessages().get())
        );
    }

    @Override
    public void updateDuplicateConsensusMessages() {
        Platform.runLater(() ->
                this.pendingConsensusDuplicateLabel.setText("Pending: " + this.server.getPendingDuplicatedConsensusMessages().get())
        );
    }

    @Override
    public void updateLostConsensusMessages() {
        Platform.runLater(() ->
                this.pendingConsensusLostLabel.setText("Pending: " + this.server.getPendingLostConsensusMessages().get())
        );
    }
}
