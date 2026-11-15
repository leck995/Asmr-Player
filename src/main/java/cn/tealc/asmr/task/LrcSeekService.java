package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.utils.FileNameUtils;
import cn.tealc.asmr.subtitle.parser.SubtitleParser;
import cn.tealc.asmr.subtitle.parser.SubtitleParserFactory;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字幕查找服务。
 * <p>
 * 通过 {@link MediaSource} 查找与当前播放音频同名的字幕文件（.lrc / .vtt / .srt）。
 * <p>
 * 采用三级递进查找策略，命中即停，最小化网络请求：
 * <ol>
 *     <li>同目录列举（1 次请求）：列出音频所在目录的全部文件，内存中按 lrc &gt; vtt &gt; srt 优先级匹配</li>
 *     <li>递归作品目录（D 次请求）：同目录未命中时，一次递归同时收集 lrc/vtt/srt，跳过已查目录</li>
 *     <li>仍未命中 → 返回 null</li>
 * </ol>
 * 匹配两种命名：{@code audio.vtt}（去音频后缀）和 {@code audio.mp3.vtt}（保留音频后缀）。
 *
 * @author Leck
 */
public class LrcSeekService extends Service<List<SubtitleCue>> {

    private static final Logger logger = LoggerFactory.getLogger(LrcSeekService.class);

    /** 字幕扩展名查找优先级：LRC 优先，VTT 次之，SRT 最后 */
    private static final String[] SUBTITLE_EXTENSIONS = {"lrc", "vtt", "srt"};

    private AsmrWork work;
    private String musicPath;
    private MediaSource source;

    /**
     * 初始化字幕查找上下文。
     *
     * @param work      当前播放的作品
     * @param musicPath 当前播放音频的路径（来自 {@code AudioTrack.getUrl()}）
     */
    public void init(AsmrWork work, String musicPath) {
        this.work = work;
        this.musicPath = musicPath;
        this.source = MediaSourceFactory.forWork(work);
        logger.info("init: work={}, musicPath={}", work != null ? work.getWorkId() : "null", musicPath);
    }

    @Override
    protected Task<List<SubtitleCue>> createTask() {
        return new Task<>() {
            @Override
            protected List<SubtitleCue> call() throws Exception {
                logger.info("call: 开始查找字幕, musicPath={}", musicPath);
                List<SubtitleCue> cues = seek();
                logger.info("call: seek() 返回 {} 条字幕", cues != null ? cues.size() : 0);
                List<SubtitleCue> filtered = filterBlacklist(cues);
                logger.info("call: 黑名单过滤后 {} 条字幕", filtered != null ? filtered.size() : 0);
                return filtered;
            }
        };
    }

    private List<SubtitleCue> filterBlacklist(List<SubtitleCue> cues) {
        if (cues == null) {
            return null;
        }
        Set<String> blacklist = new HashSet<>(Config.textBlacklist);
        return cues.stream()
                .filter(cue -> !blacklist.contains(cue.getText()))
                .toList();
    }

    /**
     * 三级递进查找字幕文件，命中即停。
     *
     * @return 解析后的字幕列表，无字幕返回 null
     */
    private List<SubtitleCue> seek() {
        String audioDir = parentDirPath();
        String targetName = filenameWithoutExt();
        String targetWithAudioExt = musicPath.substring(
                Math.max(musicPath.lastIndexOf('/'), musicPath.lastIndexOf('\\')) + 1);
        logger.info("seek: audioDir={}, targetName={}, targetWithAudioExt={}",
                audioDir, targetName, targetWithAudioExt);

        // 第 1 级：同目录列举（1 次请求），内存匹配
        logger.info("seek: 第 1 级 - 同目录列举 {}", audioDir);
        List<AsmrFile> sameDirFiles = source.listFiles(audioDir);
        logger.info("seek: 同目录找到 {} 个文件", sameDirFiles != null ? sameDirFiles.size() : 0);
        List<SubtitleCue> found = matchAndParse(sameDirFiles, targetName, targetWithAudioExt);
        if (found != null) {
            logger.info("seek: 第 1 级命中, {} 条字幕", found.size());
            return found;
        }

        // 第 2 级：递归作品目录，跳过已查的同目录
        String workPath = work.getFilepath();
        if (audioDir.equals(workPath)) {
            // 音频在作品根目录，第 1 级已列举根目录，递归只需进入子目录
            // skipDir = audioDir 可跳过根目录自身的重复列举
            logger.info("seek: 第 2 级 - 递归作品目录(跳过根目录) {}", workPath);
        } else {
            logger.info("seek: 第 2 级 - 递归作品目录(跳过同目录 {}) {}", audioDir, workPath);
        }
        List<AsmrFile> recursiveFiles = source.listFilesRecursive(
                workPath, SUBTITLE_EXTENSIONS, audioDir);
        logger.info("seek: 递归找到 {} 个字幕文件", recursiveFiles != null ? recursiveFiles.size() : 0);
        found = matchAndParse(recursiveFiles, targetName, targetWithAudioExt);
        if (found != null) {
            logger.info("seek: 第 2 级命中, {} 条字幕", found.size());
            return found;
        }

        logger.warn("seek: 所有级别均未找到字幕文件");
        return null;
    }

    /**
     * 在文件列表中按优先级匹配音频名，命中则读取并解析。
     * <p>
     * 匹配两种命名：
     * <ul>
     *   <li>{@code audio.vtt} — nameWithoutExt == targetName</li>
     *   <li>{@code audio.mp3.vtt} — nameWithoutExt == targetWithAudioExt</li>
     * </ul>
     *
     * @param files              文件列表
     * @param targetName         音频文件名（不含后缀）
     * @param targetWithAudioExt 音频完整文件名（含后缀）
     * @return 解析后的字幕列表，无匹配返回 null
     */
    private List<SubtitleCue> matchAndParse(List<AsmrFile> files, String targetName, String targetWithAudioExt) {
        if (files == null || files.isEmpty()) return null;
        for (String ext : SUBTITLE_EXTENSIONS) {
            for (AsmrFile file : files) {
                if (!ext.equalsIgnoreCase(file.getType())) continue;
                String nameWithoutExt = FileNameUtils.getNameWithSuffix(file.getFilename());
                boolean matched = nameWithoutExt.equals(targetName) || nameWithoutExt.equals(targetWithAudioExt);
                if (matched) {
                    logger.info("matchAndParse: 命中 file={}, ext={}", file.getFilename(), ext);
                    String text = source.readTextFile(file.getFilepath());
                    SubtitleParser parser = SubtitleParserFactory.forExtension(ext);
                    if (parser != null) {
                        return parser.parse(text);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取音频文件所在的父目录路径。
     * 兼容 {@code /} 和 {@code \} 路径分隔符。
     */
    private String parentDirPath() {
        int slash = Math.max(musicPath.lastIndexOf('/'), musicPath.lastIndexOf('\\'));
        return slash == -1 ? musicPath : musicPath.substring(0, slash);
    }

    /**
     * 获取音频文件名（去后缀），用于匹配。
     */
    private String filenameWithoutExt() {
        int dot = musicPath.lastIndexOf(".");
        int slash = Math.max(musicPath.lastIndexOf('/'), musicPath.lastIndexOf('\\'));
        int start = slash + 1;
        int end = dot == -1 ? musicPath.length() : dot;
        return musicPath.substring(start, end);
    }
}
