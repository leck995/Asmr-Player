package cn.tealc.asmr.ui.component;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Predicate;

/**
 * 应用内锁屏：覆盖整个主窗口，输入启动密码后解锁。
 */
public class AppLockOverlay extends StackPane {
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Predicate<String> passwordVerifier;
    private final Runnable unlockAction;
    private boolean locked;

    public AppLockOverlay(Predicate<String> passwordVerifier, Runnable unlockAction) {
        this.passwordVerifier = passwordVerifier;
        this.unlockAction = unlockAction;
        getStyleClass().add("app-lock-overlay");
        setPickOnBounds(true);
        setAlignment(Pos.CENTER);
        setVisible(false);
        setManaged(false);

        FontIcon icon = new FontIcon("mdoal-lock");
        icon.getStyleClass().add("app-lock-icon");

        Label title = new Label("应用已锁定");
        title.getStyleClass().add("app-lock-title");

        Label hint = new Label("请输入启动密码以继续");
        hint.getStyleClass().add("app-lock-hint");

        passwordField.getStyleClass().add("app-lock-field");
        passwordField.setPromptText("启动密码");
        passwordField.setPrefHeight(38);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setOnAction(event -> attemptUnlock());
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> clearError());

        errorLabel.getStyleClass().add("app-lock-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button unlockButton = new Button("解锁");
        unlockButton.getStyleClass().add("accent");
        unlockButton.setDefaultButton(true);
        unlockButton.setMaxWidth(Double.MAX_VALUE);
        unlockButton.setPrefHeight(38);
        unlockButton.setOnAction(event -> attemptUnlock());

        VBox card = new VBox(10, icon, title, hint, passwordField, errorLabel, unlockButton);
        card.getStyleClass().add("app-lock-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(380);
        card.setMaxSize(380, Region.USE_PREF_SIZE);
        getChildren().add(card);

        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
        });
    }

    public boolean isLocked() {
        return locked;
    }

    public void showLock() {
        if (locked) {
            return;
        }
        locked = true;
        passwordField.clear();
        clearError();
        setManaged(true);
        setVisible(true);
        toFront();
        Platform.runLater(passwordField::requestFocus);
    }

    public void hideLock() {
        locked = false;
        passwordField.clear();
        clearError();
        setVisible(false);
        setManaged(false);
    }

    private void attemptUnlock() {
        if (!locked) {
            return;
        }
        if (passwordVerifier.test(passwordField.getText())) {
            unlockAction.run();
        } else {
            errorLabel.setText("密码错误，请重试");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
