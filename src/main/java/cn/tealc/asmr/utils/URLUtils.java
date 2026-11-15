package cn.tealc.asmr.utils;

import java.io.File;
import java.nio.file.Paths;

/**
 * 路径/URI 的字符串拆分工具（父目录、文件名、后缀、相对路径等）。
 * <p>
 * 多为对 {@link String} 的直接 {@code substring} 截取：假定入参为合法路径且分隔符存在，
 * 缺少对应分隔符或 {@code .} 时会抛出 {@link StringIndexOutOfBoundsException}，调用方需自行保证前提。
 *
 * @author Leck
 */
public class URLUtils {
    private static  String SEPARATOR= File.separator;


    /**
     * 返回子路径去掉父目录前缀后的剩余部分。
     *
     * @param parent 父目录路径，须为 {@code url} 的严格前缀
     * @param url    子路径
     * @return 跳过父目录及其后一个分隔符后的剩余字符串
     */
    public static String getFolderRelativePath(String parent,String url){
        return url.substring(parent.length()+1);
    }

    /**
     * 返回路径的父目录，按当前平台的 {@link File#separator} 截断。
     *
     * @param url 完整路径，须包含平台分隔符
     * @return 最后一个平台分隔符之前的父目录
     */
    public static String getFolderParentPath(String url){
        return url.substring(0,url.lastIndexOf(File.separator));
    }


    /**
     * 返回父目录路径，分隔符可显式指定或自动检测（反斜杠与正斜杠取较靠后者）。
     *
     * @param url       完整路径
     * @param separator 显式分隔符；为 null 时自动检测
     * @return 最后一个分隔符之前的父目录；未找到分隔符（或位于根）时原样返回 {@code url}
     */
    public static String getParent(String url, String separator){
        if (separator == null) {
            // 自动检测：同时查找 \ 和 /，取较靠后的那个
            int lastBackslash = url.lastIndexOf('\\');
            int lastSlash = url.lastIndexOf('/');
            int idx = Math.max(lastBackslash, lastSlash);
            if (idx <= 0) return url;
            return url.substring(0, idx);
        }
        int idx = url.lastIndexOf(separator);
        if (idx <= 0) return url;
        return url.substring(0, idx);
    }

    /**
     * 返回去掉扩展名的绝对路径，截断到最后一个 {@code .}。
     *
     * @param url 完整路径，须包含扩展名分隔符 {@code .}
     * @return 不含扩展名的路径
     */
    public static String getPathWithoutSuffix(String url){
       return url.substring(0,url.lastIndexOf("."));
    }

    /**
     * 返回 {@code url} 相对 {@code parent} 且不含扩展名的子路径。
     * <p>
     * 起始下标取 {@code parent.length()-1}，因此结果会保留父目录后的那个分隔符。
     *
     * @param parent 父目录路径，须为 {@code url} 的前缀
     * @param url    完整路径，须包含扩展名分隔符 {@code .}
     * @return 相对父目录且去掉扩展名的路径
     */
    public static String getRelativePathWithoutSuffix(String parent,String url){
        return url.substring(parent.length()-1,url.lastIndexOf("."));
    }

    /**
     * 用 {@code name} 替换路径中的文件名部分，保留其所在目录与前导分隔符。
     *
     * @param url  当前文件完整路径
     * @param name 新的文件名
     * @return 目录部分加上 {@code name} 组成的新路径
     */
    public static String replaceFile(String url,String name){
        return   url.substring(0,url.lastIndexOf(SEPARATOR)+1)+name;
    }


    /**
     * 将文件系统路径转换为 {@code file:} 形式的 URI 字符串。
     *
     * @param url 本地路径
     * @return 该路径对应的 URI 字符串；含非法路径字符时抛出 {@link java.nio.file.InvalidPathException}
     */
    public static String getPathToURI(String url){
        return Paths.get(url).toUri().toString();
    }

    /**
     * 粗略地把 URI 字符串还原为路径：直接去掉开头的 6 个字符。
     * <p>
     * 不解析 URI 语法，仅按固定长度截断，仅适用于前缀恰为 6 字符（如 {@code file:/}）的输入。
     *
     * @param url URI 字符串，长度须大于 6
     * @return 去掉前 6 个字符后的剩余字符串
     */
    public static String getPathFromURI(String url){
        return url.substring(6);
    }



    /**
     * 返回不带扩展名的文件名，起始截取自最后一个平台分隔符（含该分隔符）。
     * <p>
     * 因此结果会带一个前导分隔符，如 {@code \a}；须同时存在分隔符与 {@code .}，否则抛异常。
     *
     * @param url 完整路径，须包含平台分隔符与扩展名分隔符 {@code .}
     * @return 含前导分隔符、不含扩展名的文件名片段
     */
    public static String getFileNameWithoutSuffix(String url){
        System.out.println(url);
        return url.substring(url.lastIndexOf(SEPARATOR),url.lastIndexOf("."));
    }


    /**
     * 返回最后一个分隔符之后的文件名（含扩展名），自动识别反斜杠与正斜杠。
     *
     * @param url 完整路径
     * @return 文件名；不含任何分隔符时原样返回整个入参
     */
    public static String getFileName(String url){
        int lastBackslash = url.lastIndexOf('\\');
        int lastSlash = url.lastIndexOf('/');
        int idx = Math.max(lastBackslash, lastSlash);
        return url.substring(idx + 1);
    }


    /**
     * 返回最后一个 {@code .} 之后的扩展名（保留原始大小写）。
     *
     * @param url 路径或文件名
     * @return 扩展名；不含 {@code .} 时原样返回整个入参
     */
    public static String getSuffix(String url){
        return url.substring(url.lastIndexOf(".")+1);
    }

}