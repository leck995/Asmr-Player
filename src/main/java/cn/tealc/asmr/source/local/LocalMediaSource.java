package cn.tealc.asmr.source.local;

import cn.tealc.asmr.filter.AudioFilter;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.utils.FileUtils;
import cn.tealc.asmr.utils.StrmUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地磁盘文件来源实现。
 * <p>
 * 封装项目中原有散落在各处的 {@code java.io.File} 操作，为 {@link MediaSource} 接口提供
 * 本地磁盘的统一实现。逻辑与现有代码保持一致，不改变行为：
 * <ul>
 *     <li>目录列举 —— 复用 {@code MediaListUtil} / {@code FolderTableView} 的 {@code listFiles} 模式</li>
 *     <li>音频过滤 —— 复用 {@link AudioFilter}</li>
 *     <li>递归查找 —— 复用 {@code LrcSeekService.traverseLrc/traverseVtt} 的递归逻辑</li>
 *     <li>文本读取 —— 使用 FileInputStream 读取 UTF-8 文本</li>
 *     <li>strm 解析 —— 复用 {@link StrmUtils#readUrl(File)}</li>
 *     <li>删除 —— 复用 {@link FileUtils#delete(File)}</li>
 * </ul>
 * 该类为无状态单例，通过 {@code MediaSourceFactory} 获取实例。
 *
 * @author Leck
 */
public class LocalMediaSource implements MediaSource {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMediaSource.class);

    /** 单例实例，供 {@code MediaSourceFactory} 跨包访问 */
    public static final LocalMediaSource INSTANCE = new LocalMediaSource();

    protected LocalMediaSource() {
    }

    // ===================== 读取 =====================

    @Override
    public List<AsmrFile> listFiles(String dirPath) {
        List<AsmrFile> result = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            result.add(toAsmrFile(file, file.getName()));
        }
        return result;
    }

    @Override
    public List<AsmrFile> listAudioFiles(String dirPath) {
        List<AsmrFile> result = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = dir.listFiles(new AudioFilter());
        if (files == null) {
            return result;
        }
        for (File file : files) {
            result.add(toAsmrFile(file, file.getName()));
        }
        return result;
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String extension) {
        List<AsmrFile> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }
        traverseFiles(dir, extension.toLowerCase(), result);
        return result;
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions) {
        return listFilesRecursive(dirPath, extensions, null);
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions, String skipDir) {
        Set<String> extSet = Arrays.stream(extensions)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        List<AsmrFile> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }
        traverseFilesMulti(dir, extSet, skipDir, result);
        return result;
    }

    @Override
    public String readTextFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            LOG.error("读取文本文件失败: {}", filePath, e);
            return null;
        }
        return sb.toString();
    }

    @Override
    public byte[] readBinaryFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            LOG.error("读取二进制文件失败: {}", filePath, e);
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    public boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    @Override
    public String resolvePlayUrl(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return filePath;
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".strm")) {
            String strmUrl = StrmUtils.readUrl(file);
            if (strmUrl != null) {
                return strmUrl;
            }
        }
        return filePath;
    }

    // ===================== 写入 =====================

    @Override
    public boolean delete(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return false;
        }
        try {
            FileUtils.delete(file);
            return true;
        } catch (IOException e) {
            LOG.error("永久删除本地文件失败: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean rename(String path, String newName) {
        File source = new File(path);
        if (!source.exists()) {
            return false;
        }
        File dest = new File(source.getParent(), newName);
        return source.renameTo(dest);
    }

    @Override
    public boolean move(String srcPath, String destDirPath) {
        File source = new File(srcPath);
        if (!source.exists()) {
            return false;
        }
        File destDir = new File(destDirPath);
        if (!destDir.exists() && !destDir.mkdirs()) {
            return false;
        }
        if (!destDir.isDirectory()) {
            return false;
        }
        File dest = new File(destDirPath, source.getName());
        if (dest.exists()) {
            return false;
        }
        try {
            movePath(source.toPath(), dest.toPath());
            return true;
        } catch (DirectoryNotEmptyException e) {
            if (source.isDirectory() && !dest.exists()) {
                return copyDirectoryThenDeleteSource(source.toPath(), dest.toPath());
            }
            LOG.warn("移动目标已存在同名且非空的目录: {}", dest);
            return false;
        } catch (IOException e) {
            LOG.error("移动文件失败: {} -> {}", srcPath, destDirPath, e);
            if (source.isDirectory() && !dest.exists()) {
                return copyDirectoryThenDeleteSource(source.toPath(), dest.toPath());
            }
            return source.renameTo(dest);
        }
    }

    @Override
    public boolean createDirectory(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            return true;
        }
        return dir.mkdirs();
    }

    @Override
    public boolean writeTextFile(String filePath, String content) {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            LOG.error("写入文本文件失败: {}", filePath, e);
            return false;
        }
    }

    @Override
    public boolean openInSystem(String path) {
        try {
            FileUtils.openInExplore(new File(path));
            return true;
        } catch (IOException e) {
            LOG.error("系统打开文件失败: {}", path, e);
            return false;
        }
    }

    // ===================== 来源标识 =====================

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    // ===================== 内部工具 =====================

    /**
     * 递归遍历目录，收集指定后缀的文件。
     * 复用 {@code LrcSeekService.traverseLrc/traverseVtt} 的递归逻辑。
     */
    private void traverseFiles(File dir, String lowerExt, List<AsmrFile> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                traverseFiles(file, lowerExt, result);
            } else {
                String name = file.getName();
                int dot = name.lastIndexOf(".");
                if (dot != -1) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    if (ext.equals(lowerExt)) {
                        result.add(toAsmrFile(file, name));
                    }
                }
            }
        }
    }

    /**
     * 跨文件系统移动目录的回退方案：完整复制成功后再删除源目录。
     */
    private boolean copyDirectoryThenDeleteSource(Path source, Path destination) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Files.createDirectories(destination.resolve(source.relativize(directory)));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) {
                        throw error;
                    }
                    Files.delete(directory);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            LOG.error("跨文件系统移动目录失败: {} -> {}", source, destination, e);
            return false;
        }
    }

    /**
     * 执行单次路径移动，保留测试对跨文件系统失败场景的覆盖能力。
     */
    protected void movePath(Path source, Path destination) throws IOException {
        Files.move(source, destination);
    }

    /**
     * 递归遍历目录，一次同时收集多种扩展名的文件，跳过指定子目录。
     *
     * @param dir     当前目录
     * @param extSet  目标扩展名集合（小写）
     * @param skipDir 要跳过的子目录路径，为 null 不跳过
     * @param result  收集结果
     */
    private void traverseFilesMulti(File dir, Set<String> extSet,
                                     String skipDir, List<AsmrFile> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (skipDir != null && file.getAbsolutePath().equals(skipDir)) continue;
                traverseFilesMulti(file, extSet, skipDir, result);
            } else {
                String name = file.getName();
                int dot = name.lastIndexOf(".");
                if (dot != -1) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    if (extSet.contains(ext)) {
                        result.add(toAsmrFile(file, name));
                    }
                }
            }
        }
    }

    /**
     * 将 {@code java.io.File} 转换为来源无关的 {@link AsmrFile}。
     */
    private AsmrFile toAsmrFile(File file, String hideName) {
        String type;
        if (file.isDirectory()) {
            type = "dir";
        } else {
            String name = file.getName();
            int dot = name.lastIndexOf(".");
            type = dot == -1 ? "" : name.substring(dot + 1);
        }
        long size = file.isFile() ? file.length() : 0;
        String modified = String.valueOf(file.lastModified());
        return new AsmrFile(file.getName(), file.getAbsolutePath(), type, file.isDirectory(), hideName, size, modified);
    }
}
