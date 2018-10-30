package groupmembershipservice.gui;

import groupmembershipservice.MulticastPeer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import java.io.IOException;

public class MainController  {

    private String memberId;

    @FXML
    private TextField memberTxt;

    @FXML
    private Button enterBtn;

    @FXML
    private Button joinBtn;

    @FXML
    private TextField joinTxt;

    @FXML
    private TabPane groupTabs;

    @FXML
    private void setId(ActionEvent event) {
        if (!memberTxt.getText().trim().isEmpty()) {
            memberId = memberTxt.getText().trim();
            enterBtn.setDisable(true);
            memberTxt.setDisable(true);
            joinBtn.setDisable(false);
            joinTxt.setDisable(false);
        }
    }

    @FXML
    private void joinGroup(ActionEvent event) throws IOException {
        if (!joinTxt.getText().trim().isEmpty()) {

            // Load FMX component
            ClassLoader classLoader = getClass().getClassLoader();
            FXMLLoader fxmlLoader = new FXMLLoader(classLoader.getResource("GroupPane.fxml"));
            Pane pane = fxmlLoader.load();
            GroupPane controller = fxmlLoader.getController();

            String group = joinTxt.getText().trim();

            // Add new tab
            final Tab tab = new Tab(group);
            tab.setContent(pane);
            groupTabs.getTabs().add(tab);
            groupTabs.setDisable(false);
            groupTabs.getSelectionModel().select(tab);

            // Init Server
            final MulticastPeer server = new MulticastPeer(8000, memberId);
            controller.initServer(server, group);
            controller.setGroupTab0(tab);
        }
    }
}
