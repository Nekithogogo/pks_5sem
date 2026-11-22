package ru.mirea.autosalon.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import ru.mirea.autosalon.AppContext;
import ru.mirea.autosalon.exception.AutosalonException;
import ru.mirea.autosalon.ui.Alerts;
import ru.mirea.autosalon.util.DatabaseManager;

/**
 * Главное окно (main-view.fxml): заголовок и вкладки.
 * <p>
 * Каждая вкладка подключена через {@code <fx:include fx:id="requests" .../>} и имеет свой контроллер.
 * FXMLLoader внедряет контроллер вложенного файла в поле с именем {@code fx:id + "Controller"}.
 * Главный контроллер только связывает вкладки между собой и не содержит логики работы с данными.
 */
public class MainController {

    private final AppContext context;

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab statisticsTab;

    @FXML
    private RequestsController requestsController;
    @FXML
    private ClientsController clientsController;
    @FXML
    private StatisticsController statisticsController;

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        // Изменили заявки — обновляем заявки выбранного клиента; изменили клиента — обновляем таблицу заявок
        requestsController.setOnDataChanged(clientsController::reload);
        clientsController.setOnDataChanged(requestsController::reload);
        // Статистика пересчитывается каждый раз, когда пользователь открывает её вкладку
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == statisticsTab) {
                statisticsController.reload();
            }
        });
    }

    /** Проверяет подключение к PostgreSQL и загружает данные на вкладки. */
    public void connectAndLoad() {
        DatabaseManager databaseManager = context.databaseManager();
        try {
            databaseManager.checkConnection();
        } catch (AutosalonException e) {
            Alerts.showError(tabPane.getScene().getWindow(), "Подключение к базе данных", e);
            return;
        }
        requestsController.reload();
        clientsController.reload();
        if (tabPane.getSelectionModel().getSelectedItem() == statisticsTab) {
            statisticsController.reload();
        }
    }

    @FXML
    private void handleReconnect() {
        connectAndLoad();
    }

    @FXML
    private void handleShowStatistics() {
        tabPane.getSelectionModel().select(statisticsTab);
    }
}
