package cn.tealc.asmr.service;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.LastPlayingRecord;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.FileUtils;
import cn.tealc.asmr.utils.URLUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 播放历史服务。
 * <p>
 * 承担退出时保存播放进度、启动时恢复上次播放的职责，从 {@code Config} 配置层剥离，
 * 使配置层回归纯数据持有者。历史记录文件位于数据目录下的 {@code history}。
 *
 * @author Leck
 */
public class PlayHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(PlayHistoryService.class);

    private static final File HISTORY_FILE = AppPaths.historyFile();

    @Inject
    private WorkDao workDao;
    @Inject
    private PlaylistService playlistService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 恢复上次播放。读取历史文件，根据记录定位作品、构建播放列表并跳转到上次进度。
     * 文件不存在或记录无效时静默跳过；作品或音声不存在时发 {@link NotificationKey#MESSAGE} 通知。
     */
    public void resumeLastPlay() {
        if (!HISTORY_FILE.exists()) {
            return;
        }
        try {
            LastPlayingRecord record = mapper.readValue(HISTORY_FILE, LastPlayingRecord.class);
            if (record != null) {
                playHistory(record);
            }
        } catch (IOException e) {
            LOG.error("读取播放历史失败", e);
        }
    }

    /**
     * 保存当前播放进度到历史文件。无正在播放的作品时删除历史文件。
     */
    public void saveHistory() {
        LastPlayingRecord record = collectPlayingHistory();
        try {
            FileUtils.mkdir(HISTORY_FILE.getParentFile());
            if (record != null) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(HISTORY_FILE, record);
            } else {
                if (HISTORY_FILE.exists()) {
                    HISTORY_FILE.delete();
                }
            }
        } catch (IOException e) {
            LOG.error("保存播放历史失败", e);
        }
    }

    /**
     * 根据历史记录恢复播放：查作品 → 构建播放列表 → 定位曲目 → 跳转进度。
     */
    private void playHistory(LastPlayingRecord record) {
        AsmrWork work = record.getAsmrId() == null ? null : workDao.getWorkById(record.getAsmrId());
        if (work == null) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "播放记录中音声不存在，可能已经被删除了"));
            return;
        }

        ObservableList<AudioTrack> musics = playlistService.buildPlaylist(work, record.getFilepathDir());
        if (musics == null || musics.isEmpty()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "播放记录中音声不存在，可能已经被删除了"));
            return;
        }
        int index = -1;
        for (int i = 0; i < musics.size(); i++) {
            if (musics.get(i).getUrl().equals(record.getFilepath())) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            VlcPlayer.getInstance().initAndPlay(
                    musics, index, false, Duration.millis(record.getTime()));
        } else {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "播放记录中音声不存在，可能已经被删除了"));
        }
    }

    /**
     * 采集当前播放器状态为历史记录。无正在播放的作品时返回 null。
     */
    private LastPlayingRecord collectPlayingHistory() {
        VlcPlayer player = VlcPlayer.getInstance();
        if (player.getPlayingWork() == null) {
            return null;
        }
        String path = player.getPlayingWork().getFilepath();
        if (path == null) {
            return null;
        }
        Date date = new Date();
        SimpleDateFormat sf = new SimpleDateFormat("yyyy/MM/dd");
        LastPlayingRecord record = new LastPlayingRecord();
        record.setTitle(URLUtils.getFileName(path));
        record.setChapter(URLUtils.getFileName(player.getPlayingMusic().getName()));
        record.setFilepath(player.getPlayingMusic().getUrl());
        record.setText(player.getLrcSelectedText());
        record.setSaveDate(sf.format(date));
        record.setTime(player.getCurrentTime() * 1000);
        AsmrWork work = workDao.getWorkById(player.getPlayingWork().getId());
        if (work != null) {
            record.setAsmrId(work.getId());
            return record;
        }
        return null;
    }
}
