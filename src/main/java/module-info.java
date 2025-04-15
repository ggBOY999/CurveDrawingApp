module com.example.curvedrawing {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.curvedrawing to javafx.fxml;
    exports com.example.curvedrawing;
}