package cn.tealc.asmr.config;

/**
 * {@link cn.tealc.asmr.eventbus.EventBus} 通知主题名登记表（字符串常量，非枚举）。
 * <p>
 * 这些常量作为发布/订阅时的主题字符串使用，覆盖消息提示（{@code MESSAGE}）、换肤
 * （{@code CHANGE_SKIN}）、播放与退出（{@code TO_PLAY}、{@code EXIT}）、对话框
 * （{@code DIALOG}）以及主界面网格刷新 / 作品增删改 / 专辑变更（{@code MAIN_GRID_*}、
 * {@code WORK_ALBUM_CHANGED}）等主题。UI 与服务层通过主题解耦：服务层发布，UI 订阅；
 * {@code EventBus.subscribe} 返回可取消的
 * {@link cn.tealc.asmr.eventbus.EventBus.Subscription} 句柄。
 *
 * @author Leck
 */
public class NotificationKey {
    public static final String MESSAGE = "MESSAGE";

    public static final String CHANGE_SKIN = "CHANGE_SKIN";
    public static final String TO_PLAY = "TO_PLAY";
    public static final String EXIT = "EXIT";
    public static final String DIALOG = "DIALOG";
    public static final String MAIN_ROOT_REMOVE = "MAIN_ROOT_REMOVE";
    public static final String MAIN_PLAY_LIST = "MAIN_PLAY_LIST";

    public static final String MAIN_GRID_REFRESH = "MAIN_GRID_REFRESH";
    public static final String MAIN_GRID_WORK_UPDATE = "MAIN_GRID_WORK_UPDATE";
    public static final String MAIN_GRID_WORK_REMOVE = "MAIN_GRID_WORK_REMOVE";
    public static final String WORK_ALBUM_CHANGED = "WORK_ALBUM_CHANGED";


    }
