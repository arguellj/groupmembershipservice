package groupmembershipservice.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class ConsoleManager extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        Parent root = FXMLLoader.load(Objects.requireNonNull(classLoader.getResource("console.fxml")));
        primaryStage.setResizable(false);
        primaryStage.setTitle("Group Membership Service Console");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> Platform.runLater(() -> System.exit(0)));
    }
}
