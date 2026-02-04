module com.recsync.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires com.recsync.core;
    requires org.slf4j;
    requires java.desktop;
    requires org.bytedeco.javacv;
    requires org.bytedeco.javacpp;
    requires org.bytedeco.opencv;
    requires org.bytedeco.ffmpeg;
    requires org.bytedeco.openblas;

    exports com.recsync.client;

    // 允许JavaCPP访问必要的包以加载原生库
    opens com.recsync.client to javafx.fxml;
    opens com.recsync.client.camera;
}