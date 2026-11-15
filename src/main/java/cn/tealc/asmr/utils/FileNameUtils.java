package cn.tealc.asmr.utils;

/**
 * 文件名与扩展名的拆分工具。
 *
 * @author Leck
 */
public class FileNameUtils {
    /**
     * 去除文件名中的扩展名，返回最后一个 {@code .} 之前的部分。
     * <p>
     * 注意：方法名 {@code getNameWithSuffix} 具有误导性，实际返回的是<strong>不含</strong>后缀的名称。
     *
     * @param name 原始文件名，可为 null 或空白
     * @return 去掉扩展名后的名称；无 {@code .} 时返回原值，入参为 null/空白时返回空串
     */
    public static String getNameWithSuffix(String name){
        if (name == null || name.isBlank()) {
            return "";
        }
        int i = name.lastIndexOf(".");
        return i==-1 ? name : name.substring(0,i);
    }


    /**
     * 提取 URL/文件名最后一个 {@code .} 之后的扩展名，并转为小写。
     *
     * @param url 待解析的路径或文件名
     * @return 小写扩展名；不含 {@code .} 时原样返回整个入参
     */
    public static String getSuffix(String url){
        int i=url.lastIndexOf(".");
        return i==-1 ? url : url.substring(i+1).toLowerCase();
    }
}
