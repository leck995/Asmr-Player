package cn.tealc.asmr.utils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件与桌面集成操作工具：删除、回收站、系统打开、重命名、建目录。
 * <p>
 * 依赖 {@link Desktop}，在无头环境或某些 Linux 桌面下可能不支持。
 *
 * @author Leck
 */
public class FileUtils {
    /**
     * 递归删除文件或目录（含非空目录及其全部内容）。
     *
     * @param file 待删除的文件或目录
     * @throws IOException 删除过程中发生 I/O 错误
     */
    public static void delete(File file) throws IOException {
        Files.walkFileTree(file.toPath(),new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 将文件移入系统回收站。
     *
     * @param file 待移入回收站的文件或目录
     * @return 成功移入回收站时为 {@code true}，否则为 {@code false}
     */
    public static boolean moveToTrash(File file){
        return Desktop.getDesktop().moveToTrash(file);
    }

    /**
     * 用系统默认程序打开该路径：文件交给关联应用，目录则在文件管理器中打开。
     *
     * @param file 待打开的文件或目录
     * @throws IOException 系统无法打开时抛出
     */
    public static void openInExplore(File file) throws IOException {
        Desktop.getDesktop().open(file);
    }

    /**
     * 重命名或移动文件（同一文件系统内）。
     *
     * @param oldName 原文件完整路径
     * @param newName 目标文件完整路径
     * @return {@link File#renameTo} 的结果；跨文件系统或目标已存在时可能为 {@code false}
     * @throws IOException 当前未抛出，保留以兼容既有调用签名
     */
    public static boolean rename(String oldName,String newName) throws IOException {
        File old=new File(oldName);
        return old.renameTo(new File(newName));
    }




    /**
     * 目录不存在时创建（含父级目录）。
     *
     * @param dir 待创建的目录
     * @return 本次新建了目录时为 {@code true}；目录已存在而未创建时为 {@code false}
     */
    public static boolean mkdir(File dir){
        if (!dir.exists()) {
           return dir.mkdirs();
        }
        return false;
    }
}