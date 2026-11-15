package cn.tealc.asmr.mvvm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知中心默认实现。
 * <p>
 * 核心数据结构：{@code ConcurrentHashMap<String, CopyOnWriteArrayList<NotificationObserver>>}
 * <ul>
 *   <li>读多写少场景使用 CopyOnWriteArrayList，遍历时无需加锁</li>
 *   <li>publish 同步调用所有观察者（同线程，无 Platform.runLater 包装）</li>
 *   <li>强引用持有 observer</li>
 * </ul>
 *
 * @author Leck
 */
public class DefaultNotificationCenter implements NotificationCenter {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNotificationCenter.class);

    /** 全局订阅：消息主题名 -> 观察者列表 */
    private final Map<String, List<NotificationObserver>> globalObservers = new ConcurrentHashMap<>();

    /** 对象级订阅：channel 对象 -> (消息主题名 -> 观察者列表) */
    private final Map<Object, Map<String, List<NotificationObserver>>> channelObserverMap = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String messageName, NotificationObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("The observer must not be null.");
        }
        globalObservers.computeIfAbsent(messageName, k -> new CopyOnWriteArrayList<>());
        List<NotificationObserver> list = globalObservers.get(messageName);
        if (list.contains(observer)) {
            LOG.warn("Observer already subscribed for message '{}', this will cause duplicate notifications", messageName);
        }
        list.add(observer);
    }

    @Override
    public void unsubscribe(String messageName, NotificationObserver observer) {
        List<NotificationObserver> list = globalObservers.get(messageName);
        if (list != null) {
            list.remove(observer);
        }
    }

    @Override
    public void unsubscribe(NotificationObserver observer) {
        globalObservers.values().forEach(list -> list.remove(observer));
    }

    @Override
    public void publish(String messageName, Object... payload) {
        List<NotificationObserver> list = globalObservers.get(messageName);
        if (list != null) {
            for (NotificationObserver observer : list) {
                observer.receivedNotification(messageName, payload);
            }
        }
    }

    @Override
    public void publish(Object channel, String messageName, Object[] payload) {
        Map<String, List<NotificationObserver>> channelMap = channelObserverMap.get(channel);
        if (channelMap != null) {
            List<NotificationObserver> list = channelMap.get(messageName);
            if (list != null) {
                for (NotificationObserver observer : list) {
                    observer.receivedNotification(messageName, payload);
                }
            }
        }
    }

    @Override
    public void subscribe(Object channel, String messageName, NotificationObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("The observer must not be null.");
        }
        channelObserverMap.computeIfAbsent(channel, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(messageName, k -> new CopyOnWriteArrayList<>())
                .add(observer);
    }

    @Override
    public void unsubscribe(Object channel, String messageName, NotificationObserver observer) {
        Map<String, List<NotificationObserver>> channelMap = channelObserverMap.get(channel);
        if (channelMap != null) {
            List<NotificationObserver> list = channelMap.get(messageName);
            if (list != null) {
                list.remove(observer);
            }
        }
    }

    @Override
    public void unsubscribe(Object channel, NotificationObserver observer) {
        Map<String, List<NotificationObserver>> channelMap = channelObserverMap.get(channel);
        if (channelMap != null) {
            channelMap.values().forEach(list -> list.remove(observer));
        }
    }

    @Override
    public void clear() {
        globalObservers.clear();
        channelObserverMap.clear();
    }
}
