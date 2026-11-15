package cn.tealc.asmr.di;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量依赖注入容器。
 * <p>
 * 提供 bind / get / inject 三项核心能力，基于惰性单例 + 字段注入：
 * <ul>
 *   <li>{@link #bind(Class, Supplier)}：显式绑定类型到创建工厂</li>
 *   <li>{@link #get(Class)}：获取单例，首次获取时创建并递归注入其 {@code @Inject} 字段</li>
 *   <li>{@link #inject(Object)}：对已有实例扫描 {@code @Inject} 字段并注入</li>
 * </ul>
 * <p>
 * 无绑定时，按类型反射无参构造实例化。容器线程安全，以类型为锁串行化单例创建。
 * 通过 ThreadLocal 创建栈检测循环依赖，发现即抛 {@link IllegalStateException}。
 *
 * @author Leck
 */
public final class DI {

    /** 类型 -> 实例工厂（显式绑定），优先于反射无参实例化 */
    private static final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();

    /** 类型 -> 单例实例，注入完成后才放入 */
    private static final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();

    /** 当前线程正在创建的类型栈，用于检测循环依赖 */
    private static final ThreadLocal<Set<Class<?>>> creating = ThreadLocal.withInitial(LinkedHashSet::new);

    private DI() {
    }

    /**
     * 清空所有绑定与缓存的实例。
     * <p>仅供测试使用，确保测试间 DI 状态隔离。
     */
    public static void reset() {
        factories.clear();
        instances.clear();
        creating.get().clear();
    }

    /**
     * 绑定类型到创建工厂。
     *
     * @param type   要绑定的类型（通常是接口）
     * @param factory 创建实例的工厂
     * @param <T>     类型
     */
    public static <T> void bind(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }

    /**
     * 获取类型的单例。首次获取时创建实例并递归注入其 {@code @Inject} 字段，
     * 之后直接返回缓存。无绑定时按类型反射无参构造实例化。
     *
     * @param type 要获取的类型
     * @param <T>  类型
     * @return 单例实例，不会为 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        Object existing = instances.get(type);
        if (existing != null) {
            return (T) existing;
        }
        synchronized (type) {
            existing = instances.get(type);
            if (existing != null) {
                return (T) existing;
            }
            Set<Class<?>> stack = creating.get();
            if (!stack.add(type)) {
                throw new IllegalStateException("检测到循环依赖: " + type.getName());
            }
            try {
                T instance = createInstance(type);
                inject(instance);
                instances.put(type, instance);
                return instance;
            } finally {
                stack.remove(type);
            }
        }
    }

    /**
     * 对已有实例扫描 {@code @Inject} 字段并注入。
     * 遍历整个类层级（含父类）。
     *
     * @param target 需要注入的目标实例
     */
    public static void inject(Object target) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    try {
                        field.setAccessible(true);
                        field.set(target, get(field.getType()));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("注入字段失败: " + field, e);
                    }
                }
            }
            cls = cls.getSuperclass();
        }
    }

    /**
     * 创建实例：有显式绑定则用工厂，否则反射无参构造。
     */
    @SuppressWarnings("unchecked")
    private static <T> T createInstance(Class<T> type) {
        Supplier<?> factory = factories.get(type);
        if (factory != null) {
            return (T) factory.get();
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("无法实例化 " + type.getName()
                    + "：无显式绑定且缺少无参构造", e);
        }
    }
}
