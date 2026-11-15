/**
 * AsmrPlayer 应用模块（JPMS 描述符）。
 * <p>
 * 声明 JavaFX（controls / fxml / swing）、vlcj 播放、SQLite、Jackson、
 * Logback、Ikonli、AtlantaFX 等依赖；以 {@code open module} 形式对整个模块开放
 * 反射访问（供 FXMLLoader、Jackson 等运行时使用），并导出应用入口、数据模型、各 UI 视图
 * 与 DLSite 同步任务等包。
 */
open module cn.tealc.asmr {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.jfoenix;
    requires java.desktop;
    requires javafx.swing;
    requires javafx.base;
    requires org.controlsfx.controls;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires org.xerial.sqlitejdbc;
    requires jdk.crypto.cryptoki;
    requires uk.co.caprica.vlcj;
    requires org.slf4j;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.material2;
    requires org.kordamp.ikonli.materialdesign2;
    requires cn.tealc.teafx;
    requires org.apache.commons.dbutils;
    requires ch.qos.logback.core;
    requires ch.qos.logback.classic;
    requires java.net.http;


    exports cn.tealc.asmr;
    exports cn.tealc.asmr.model;
    exports cn.tealc.asmr.model.dlsite;
    exports cn.tealc.asmr.model.galgame;
    exports cn.tealc.asmr.model.collection;

    exports cn.tealc.asmr.ui.library;
    exports cn.tealc.asmr.ui.anywheredoor;
    exports cn.tealc.asmr.ui;
    exports cn.tealc.asmr.ui.component;
    exports cn.tealc.asmr.ui.dialog;
    exports cn.tealc.asmr.ui.dialog.legacy;
    exports cn.tealc.asmr.ui.viewer;
    exports cn.tealc.asmr.ui.work;
    exports cn.tealc.asmr.ui.work.cells;
    exports cn.tealc.asmr.ui.work.list;
    exports cn.tealc.asmr.ui.galgame;
    exports cn.tealc.asmr.ui.main;
    exports cn.tealc.asmr.ui.player;
    exports cn.tealc.asmr.ui.setting;
    exports cn.tealc.asmr.ui.tools;
    exports cn.tealc.asmr.ui.guide;

    exports cn.tealc.asmr.task.dlsite;
    exports cn.tealc.asmr.player;
    exports cn.tealc.asmr.task;

}
