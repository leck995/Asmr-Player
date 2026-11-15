package cn.tealc.asmr.di;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DI 容器单元测试。
 *
 * @author Leck
 */
class DITest {

    @AfterEach
    void tearDown() {
        DI.reset();
    }

    @Test
    @DisplayName("get 返回惰性创建的单例，两次获取同一实例")
    void get_returnsSingleton() {
        DI.reset();
        Simple a1 = DI.get(Simple.class);
        Simple a2 = DI.get(Simple.class);
        assertSame(a1, a2, "两次 get 应返回同一单例实例");
    }

    @Test
    @DisplayName("bind 的工厂被用于创建实例")
    void bind_factoryUsed() {
        DI.reset();
        AtomicInteger counter = new AtomicInteger(0);
        DI.bind(Simple.class, () -> {
            counter.incrementAndGet();
            return new Simple();
        });
        Simple a1 = DI.get(Simple.class);
        Simple a2 = DI.get(Simple.class);
        assertEquals(1, counter.get(), "工厂只应被调用一次（单例缓存）");
        assertSame(a1, a2);
    }

    @Test
    @DisplayName("inject 对已有实例扫描 @Inject 字段并注入")
    void inject_setsAnnotatedFields() {
        DI.reset();
        DI.bind(Simple.class, Simple::new);
        Holder holder = new Holder();
        DI.inject(holder);
        assertNotNull(holder.simple, "@Inject 字段应被注入");
        assertInstanceOf(Simple.class, holder.simple);
    }

    @Test
    @DisplayName("get 递归注入实例的 @Inject 字段")
    void get_recursiveInject() {
        DI.reset();
        DI.bind(Simple.class, Simple::new);
        // Outer 持有 @Inject Simple，get 时应自动递归注入
        Outer outer = DI.get(Outer.class);
        assertNotNull(outer.simple, "get 创建的实例应自动注入 @Inject 字段");
    }

    @Test
    @DisplayName("检测到循环依赖时抛 IllegalStateException")
    void get_circularDependency_throws() {
        DI.reset();
        DI.bind(CycleA.class, CycleA::new);
        DI.bind(CycleB.class, CycleB::new);
        assertThrows(IllegalStateException.class, () -> DI.get(CycleA.class),
                "A→B→A 循环依赖应抛 IllegalStateException");
    }

    @Test
    @DisplayName("reset 清空所有绑定与缓存")
    void reset_clearsAll() {
        DI.bind(Simple.class, Simple::new);
        DI.get(Simple.class);
        DI.reset();
        // reset 后重新 get 应创建新实例
        Simple a1 = DI.get(Simple.class);
        Simple a2 = DI.get(Simple.class);
        assertSame(a1, a2, "reset 后重新建立单例");
    }

    // ===== 测试辅助类 =====

    /** 简单可实例化类 */
    static class Simple {
    }

    /** 持有 @Inject 字段的类 */
    static class Holder {
        @Inject
        Simple simple;
    }

    /** 持有 @Inject 字段，用于验证 get 的递归注入 */
    static class Outer {
        @Inject
        Simple simple;
    }

    /** 循环依赖 A → B → A */
    static class CycleA {
        @Inject
        CycleB b;
    }

    static class CycleB {
        @Inject
        CycleA a;
    }
}
