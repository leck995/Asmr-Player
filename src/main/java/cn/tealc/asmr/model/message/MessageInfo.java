package cn.tealc.asmr.model.message;

import javafx.util.Duration;

/**
 * 消息通知包装类，用于 {@code EventBus.message()} 传递消息内容。
 * <p>
 * 支持 title（可选）、message、autoClose、showTime 四个维度。
 *
 * @author Leck
 */
public class MessageInfo {
    private MessageType type;
    private String title;
    private String message;
    private Boolean autoClose; //自动关闭
    private Duration showTime;//显示时间
    public static final Duration DEFAULT=Duration.seconds(3.0);
    public static final Duration LONG=Duration.seconds(5.0);
    public static final Duration LONG_PLUS=Duration.seconds(7.0);

    public static final String SUCCESS="Success";
    public static final String WARNING="Warning";
    public static final String INFO="Info";
    public static final String ERROR="Error";


    public static MessageInfo warning(String message) {
        return new MessageInfo(MessageType.WARNING, WARNING, message);
    }
    public static MessageInfo error(String message) {
        return new MessageInfo(MessageType.ERROR, ERROR, message);
    }
    public static MessageInfo success(String message) {
        return new MessageInfo(MessageType.SUCCESS, SUCCESS, message);
    }
    public static MessageInfo info(String message) {
        return new MessageInfo(MessageType.INFO, INFO, message);
    }

    public static MessageInfo warning(String title, String message) {
        return new MessageInfo(MessageType.WARNING, title, message);
    }
    public static MessageInfo error(String title, String message) {
        return new MessageInfo(MessageType.ERROR, title, message);
    }
    public static MessageInfo success(String title, String message) {
        return new MessageInfo(MessageType.SUCCESS, title, message);
    }
    public static MessageInfo info(String title, String message) {
        return new MessageInfo(MessageType.INFO, title, message);
    }


    public MessageInfo(MessageType type, String message) {
        this.type = type;
        this.message = message;
        this.autoClose = true;
        this.showTime=DEFAULT;
    }
    public MessageInfo(MessageType type, String title, String message) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.autoClose = true;
        this.showTime=DEFAULT;
    }

    public MessageInfo(MessageType type, String message, Boolean autoClose) {
        this.type = type;
        this.message = message;
        this.autoClose = autoClose;
        this.showTime=DEFAULT;
    }
    public MessageInfo(MessageType type, String message, Duration showTime) {
        this.type = type;
        this.message = message;
        this.autoClose = true;
        this.showTime = showTime;
    }

    public MessageInfo(MessageType type, String message, Boolean autoClose, Duration showTime) {
        this.type = type;
        this.message = message;
        this.autoClose = autoClose;
        this.showTime = showTime;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }


    public Boolean getAutoClose() {
        return autoClose;
    }

    public void setAutoClose(Boolean autoClose) {
        this.autoClose = autoClose;
    }

    public Duration getShowTime() {
        return showTime;
    }

    public void setShowTime(Duration showTime) {
        this.showTime = showTime;
    }
}
