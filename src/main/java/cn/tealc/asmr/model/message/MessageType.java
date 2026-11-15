package cn.tealc.asmr.model.message;

/**
 * 全局消息通知的级别取值（SUCCESS/WARNING/ERROR/INFO）。
 * <p>
 * 由 {@link MessageInfo} 携带，经 {@code EventBus.publish(NotificationKey.MESSAGE)} 发出；
 * {@code OverlayLayer} 按其分派对应的图标与样式渲染通知弹层。
 *
 * @author Leck
 */
public enum MessageType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO;
}
