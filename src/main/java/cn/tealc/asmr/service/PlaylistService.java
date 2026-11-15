package cn.tealc.asmr.service;

import cn.tealc.asmr.model.SupportedAudioFormat;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 播放列表构造服务。
 * <p>
 * 负责从 {@link MediaSource} 读取作品目录并过滤音频文件，
 * 构造 {@link AudioTrack} 播放列表，供作品卡片和详情页使用。
 * <p>
 * 只负责构造列表，播放控制由 UI 直接调用 {@code VlcPlayer}。
 *
 * @author Leck
 */
public class PlaylistService {

    /**
     * 构造作品的播放列表。
     * <p>
     * 优先列出作品根目录的音频，根目录无音频时递归查找子目录。
     *
     * @param work 媒体作品
     * @return 播放列表，无音频文件时返回 null
     */
    public ObservableList<AudioTrack> buildPlaylist(AsmrWork work) {
        return buildPlaylist(work, work.getFilepath());
    }

    /**
     * 构造作品子目录的播放列表。
     * <p>
     * 优先使用当前目录的音频；没有音频时递归查找子目录。
     *
     * @param work       媒体作品
     * @param subDirPath 子目录路径（音频文件从此目录列出）
     * @return 播放列表，无音频文件时返回 null
     */
    public ObservableList<AudioTrack> buildPlaylist(AsmrWork work, String subDirPath) {
        MediaSource source = MediaSourceFactory.forWork(work);
        List<AsmrFile> audioFiles = source.listAudioFiles(subDirPath);

        ObservableList<AudioTrack> direct = buildPlaylistFromFiles(work, audioFiles);
        if (direct != null) return direct;

        String[] extensions = Arrays.stream(SupportedAudioFormat.values())
                .map(SupportedAudioFormat::getFilesuffix).toArray(String[]::new);
        List<AsmrFile> nested = source.listFilesRecursive(subDirPath, extensions);
        if (nested == null || nested.isEmpty()) return null;
        return buildPlaylistFromFiles(work, nested.stream()
                .sorted(Comparator.comparing(AsmrFile::getFilepath, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    /**
     * 从给定文件列表过滤音频并构造播放列表。
     * <p>
     * 直接从 {@code files} 中过滤音频文件构造 {@link AudioTrack}。
     *
     * @param work  媒体作品
     * @param files 文件列表
     * @return 播放列表，无音频文件时返回 null
     */
    public ObservableList<AudioTrack> buildPlaylistFromFiles(AsmrWork work, List<AsmrFile> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        List<AsmrFile> audioFiles = files.stream()
                .filter(f -> !f.isDir() && SupportedAudioFormat.contains(f.getType()))
                .toList();
        if (audioFiles.isEmpty()) {
            return null;
        }
        return toMusicList(work, audioFiles);
    }

    // ===================== 内部工具 =====================

    /**
     * 将 AsmrFile 列表转换为 AudioTrack 播放列表。
     *
     * @param work  所属作品
     * @param items 文件列表
     * @return AudioTrack 播放列表
     */
    private ObservableList<AudioTrack> toMusicList(AsmrWork work, List<AsmrFile> items) {
        ObservableList<AudioTrack> list = FXCollections.observableArrayList();
        for (AsmrFile item : items) {
            list.add(new AudioTrack(work, item));
        }
        return list;
    }
}
