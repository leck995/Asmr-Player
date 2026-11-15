package cn.tealc.asmr.player;

import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.task.LrcSeekService;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleTiming;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 歌词同步器，负责歌词查询、列表维护与时间同步选中。
 * <p>
 * 从 {@link VlcPlayer} 中抽取的歌词同步职责。监听 {@code playingMusic} 和
 * {@code currentTime} 属性变化，自动触发歌词查询并更新选中索引/文本。
 * 不依赖 vlcj native 层。
 * </p>
 * <p>
 * 时间匹配使用二分查找（O(log n)），并加 {@link SubtitleTiming#BASE_LEAD_MS} 提前量
 * 使歌词显示更自然；用户可在播放时通过 {@code subtitleDelayMs} 继续整体延后或提前。
 * </p>
 *
 * @author Leck
 */
class LrcSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(LrcSynchronizer.class);

    private final LrcSeekService lrcSeekService = new LrcSeekService();
    private final SimpleDoubleProperty currentTime;

    private final ObjectProperty<ObservableList<SubtitleCue>> lrcBeans =
            new SimpleObjectProperty<>(FXCollections.observableArrayList());
    private final SimpleIntegerProperty lrcSelectedIndex = new SimpleIntegerProperty(0);
    private final SimpleStringProperty lrcSelectedText = new SimpleStringProperty("无台词");

    /**
     * @param playingMusic VlcPlayer 的 playingMusic 属性引用，变化时触发歌词查询
     * @param currentTime  VlcPlayer 的 currentTime 属性引用，变化时触发歌词选中更新
     * @param playingWork  VlcPlayer 的 playingWork 属性引用，用于获取 work.getFilepath()
     */
    LrcSynchronizer(ObjectProperty<AudioTrack> playingMusic,
                    SimpleDoubleProperty currentTime,
                    ObjectProperty<AsmrWork> playingWork) {
        this.currentTime = currentTime;

        // 歌词查询服务结果 → 更新歌词列表
        lrcSeekService.valueProperty().addListener((observable, lrcBeans1, t1) -> {
            if (t1 != null) {
                logger.info("歌词查询结果: {} 条", t1.size());
                lrcBeans.set(FXCollections.observableArrayList(t1));
            } else {
                logger.warn("歌词查询结果: null, 清空列表");
                lrcBeans.get().clear();
                lrcSelectedText.set("无台词");
            }
        });

        // 播放曲目变更 → 启动歌词查询服务
        playingMusic.addListener((observable, media1, t1) -> {
            if (t1 != null) {
                logger.info("播放曲目变更 → 启动歌词查询, music={}", t1.getUrl());
                lrcSeekService.init(playingWork.get(), t1.getUrl());
                Platform.runLater(() -> lrcSeekService.restart());
            }
        });

        // 当前播放时间变更 → 更新歌词选中索引与文本
        currentTime.addListener((observable, number, t1) -> updateLrcSelection(t1));

        // 延迟调整后立即用当前时间重算，暂停状态也能看到效果。
        Config.setting.subtitleDelayMs.addListener((observable, oldValue, newValue) -> {
            if (isValidTime(currentTime.get())) {
                updateLrcSelection(currentTime.get());
            }
        });

        // 显示文字体系切换后，立即刷新当前播放字幕。
        SubtitleDisplayService.modeProperty().addListener((observable, oldValue, newValue) -> refreshCurrentLrcText());
    }

    // ==================== 歌词同步逻辑 ====================

    /**
     * 根据当前播放时间，用二分查找匹配当前歌词索引。
     * <p>
     * 查找时间由 {@link SubtitleTiming#lookupMillis(double, int)} 叠加内置提前量与
     * 用户延迟后，在字幕列表中找到最后一个 {@code startTime <= millis} 的条目。
     * </p>
     *
     * @param t1 当前播放时间（秒）
     */
    private void updateLrcSelection(Number t1) {
        if (!isValidTime(t1) || lrcBeans.get().isEmpty()) return;
        long millis = SubtitleTiming.lookupMillis(t1.doubleValue(), Config.setting.subtitleDelayMs.get());
        ObservableList<SubtitleCue> beans = lrcBeans.get();

        int index = binarySearchIndex(beans, millis);
        if (index >= 0) {
            lrcSelectedIndex.set(index);
            setLrcText(beans.get(index));
        }
    }

    /**
     * 未就绪时播放时间为 {@link Double#MAX_VALUE}，不能参与字幕匹配。
     */
    private static boolean isValidTime(Number time) {
        return time != null && Double.isFinite(time.doubleValue()) && time.doubleValue() >= 0;
    }

    /**
     * 二分查找：返回列表中最后一个 startTime <= millis 的条目索引。
     *
     * @param beans 字幕列表（已按时间升序排列）
     * @param millis 目标时间（毫秒）
     * @return 匹配的条目索引，-1 表示无匹配（列表为空或 millis 小于第一个条目时间）
     */
    private int binarySearchIndex(ObservableList<SubtitleCue> beans, long millis) {
        int size = beans.size();
        if (size == 0) return -1;
        if (millis < beans.get(0).getStartTime()) {
            return 0; // 还没到第一句，选第 0 句
        }
        int lo = 0, hi = size - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (beans.get(mid).getStartTime() <= millis) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * 设置当前歌词文本（含翻译行）。
     */
    private void setLrcText(SubtitleCue cue) {
        String text = SubtitleDisplayService.convert(cue.getText());
        String translation = SubtitleDisplayService.convert(cue.getTranslation());
        if (translation == null) {
            lrcSelectedText.set(text);
        } else {
            lrcSelectedText.set(text + "\n" + translation);
        }
    }

    /**
     * 根据当前选中字幕重新生成显示文本。
     */
    private void refreshCurrentLrcText() {
        ObservableList<SubtitleCue> beans = lrcBeans.get();
        int index = lrcSelectedIndex.get();
        if (index >= 0 && index < beans.size()) {
            setLrcText(beans.get(index));
        }
    }

    /** 清空歌词状态（切歌/释放时调用） */
    void clear() {
        lrcBeans.get().clear();
        lrcSelectedIndex.set(0);
        lrcSelectedText.set("无台词");
    }

    // ==================== 访问器 ====================

    ObservableList<SubtitleCue> getLrcBeans() {
        return lrcBeans.get();
    }

    ObjectProperty<ObservableList<SubtitleCue>> lrcBeansProperty() {
        return lrcBeans;
    }

    int getLrcSelectedIndex() {
        return lrcSelectedIndex.get();
    }

    SimpleIntegerProperty lrcSelectedIndexProperty() {
        return lrcSelectedIndex;
    }

    String getLrcSelectedText() {
        return lrcSelectedText.get();
    }

    SimpleStringProperty lrcSelectedTextProperty() {
        return lrcSelectedText;
    }
}
