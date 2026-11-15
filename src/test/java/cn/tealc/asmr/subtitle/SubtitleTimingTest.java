package cn.tealc.asmr.subtitle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 字幕同步时间计算测试。
 */
class SubtitleTimingTest {

    @Test
    void 查找时间叠加内置提前量与用户延迟() {
        assertEquals(1500, SubtitleTiming.lookupMillis(1.0, 0));
        assertEquals(1000, SubtitleTiming.lookupMillis(1.0, 500));
        assertEquals(2000, SubtitleTiming.lookupMillis(1.0, -500));
    }

    @Test
    void 延迟限制在允许范围内() {
        assertEquals(SubtitleTiming.MAX_DELAY_MS, SubtitleTiming.clampDelay(20_000));
        assertEquals(SubtitleTiming.MIN_DELAY_MS, SubtitleTiming.clampDelay(-20_000));
        assertEquals(350, SubtitleTiming.clampDelay(350));
    }

    @Test
    void 跳转时间补偿用户延迟且不早于零() {
        assertEquals(12_000, SubtitleTiming.seekTargetMillis(10_000, 2_000));
        assertEquals(8_000, SubtitleTiming.seekTargetMillis(10_000, -2_000));
        assertEquals(0, SubtitleTiming.seekTargetMillis(1_000, -2_000));
    }
}
