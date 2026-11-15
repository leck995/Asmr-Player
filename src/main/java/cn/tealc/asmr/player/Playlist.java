package cn.tealc.asmr.player;

import cn.tealc.asmr.model.AudioTrack;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 播放列表管理器，维护曲目列表、当前索引与乱序模式。
 * <p>
 * 从 {@link VlcPlayer} 中抽取的播放列表管理职责，不依赖 vlcj native 层，
 * 可独立单元测试。{@code disorder} 属性直接驱动列表 shuffle/sort。
 * </p>
 *
 * @author Leck
 */
class Playlist {

    private final ObservableList<AudioTrack> musics = FXCollections.observableArrayList();
    private final SimpleIntegerProperty musicIndex = new SimpleIntegerProperty(-1);
    private final BooleanProperty disorder = new SimpleBooleanProperty(false);

    Playlist() {
        // 乱序模式切换 → 重新排列播放列表，同时迁移当前播放索引
        disorder.addListener((observable, aBoolean, isDisorder) -> {
            AudioTrack current = (musicIndex.get() >= 0 && !musics.isEmpty())
                    ? musics.get(musicIndex.get()) : null;
            if (isDisorder) {
                Collections.shuffle(musics);
            } else {
                musics.sort(Comparator.comparing(AudioTrack::getName));
            }
            // shuffle/sort 后重新定位当前曲目，避免索引失效
            if (current != null) {
                int newIndex = musics.indexOf(current);
                if (newIndex >= 0) {
                    setMusicIndex(newIndex);
                }
            }
        });
    }

    // ==================== 列表操作 ====================

    /** 初始化播放列表（不加载播放） */
    void init(List<AudioTrack> musics) {
        this.musics.setAll(musics);
    }

    /** 添加单首曲目（去重） */
    void add(AudioTrack music) {
        add(List.of(music));
    }

    /** 批量添加曲目（去重） */
    void add(List<AudioTrack> musics) {
        List<AudioTrack> list = musics.stream().filter(music -> !getMusics().contains(music)).toList();
        getMusics().addAll(list);
    }

    /**
     * 移除指定索引的曲目，并完整修正当前播放索引。
     * <p>
     * 索引修正逻辑完全内聚于此，调用方无需额外修正。
     * </p>
     *
     * @param index 要移除的索引
     * @return true 如果移除后列表为空（调用方应做清理）
     */
    boolean removeMusic(int index) {
        if (musics.size() == 1) {
            clear();
            return true;
        }
        musics.remove(index);
        if (index < musicIndex.get()) {
            // 移除在当前曲目之前，索引前移
            setMusicIndex(musicIndex.get() - 1);
        } else if (index == musicIndex.get() && musicIndex.get() >= musics.size()) {
            // 移除的是最后一首且是当前播放，索引回退到最后
            setMusicIndex(musics.size() - 1);
        }
        return false;
    }

    /** 清空播放列表 */
    void clear() {
        musics.clear();
        setMusicIndex(-1);
    }

    // ==================== 索引导航 ====================

    /**
     * 计算下一曲索引并更新 musicIndex，循环到列表开头。
     *
     * @return 新的索引，-1 表示列表为空
     */
    int moveToNext() {
        if (getMusics().isEmpty()) return -1;
        if (getMusicIndex() == getMusics().size() - 1) {
            setMusicIndex(0);
        } else {
            setMusicIndex(getMusicIndex() + 1);
        }
        return getMusicIndex();
    }

    /**
     * 计算上一曲索引并更新 musicIndex，循环到列表末尾。
     *
     * @return 新的索引，-1 表示列表为空
     */
    int moveToPrev() {
        if (getMusics().isEmpty()) return -1;
        if (getMusicIndex() == 0) {
            setMusicIndex(getMusics().size() - 1);
        } else {
            setMusicIndex(getMusicIndex() - 1);
        }
        return getMusicIndex();
    }

    // ==================== 访问器 ====================

    ObservableList<AudioTrack> getMusics() {
        return musics;
    }

    int getMusicIndex() {
        return musicIndex.get();
    }

    void setMusicIndex(int index) {
        this.musicIndex.set(index);
    }

    SimpleIntegerProperty musicIndexProperty() {
        return musicIndex;
    }

    boolean isDisorder() {
        return disorder.get();
    }

    BooleanProperty disorderProperty() {
        return disorder;
    }

    void setDisorder(boolean disorder) {
        this.disorder.set(disorder);
    }
}
