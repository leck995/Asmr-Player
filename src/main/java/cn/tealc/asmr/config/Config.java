package cn.tealc.asmr.config;

import cn.tealc.asmr.dao.BlacklistTextDao;
import cn.tealc.asmr.model.*;
import cn.tealc.asmr.utils.AppPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 全局配置持有者。
 * <p>
 * 负责加载和保存应用设置：类加载时用 Jackson 从 {@code AppPaths.settingsFile()}
 * 读入 {@link AppSettings}（文件不存在则取默认值；检测到旧版明文 API Key 时立即
 * 加密重写），退出时经 {@link #saveProperties(boolean)} 写回同一份 JSON。
 * 媒体库列表保存在内存，字幕黑名单存入数据库。
 *
 * @author Leck
 */
public class Config {
    public static String version = "1.5.1";
    public static AppSettings setting;
    public static ObservableList<MediaLibrary> mediaLibraries= FXCollections.observableArrayList();//媒体库
    public static ObservableList<String> textBlacklist= FXCollections.observableArrayList();//字幕文本黑名单

    static {
        ObjectMapper mapper = new ObjectMapper();
        File settingFile = AppPaths.settingsFile();
        try {
            boolean migrateApiKey = false;
            if (settingFile.exists()) {
                JsonNode root = mapper.readTree(settingFile);
                setting = mapper.treeToValue(root, AppSettings.class);
                migrateApiKey = hasLegacyPlaintextApiKey(root);
            }else {
                setting = new AppSettings();
            }
            if (setting.aiTranslation == null) {
                setting.aiTranslation = new AiTranslationSettings();
            }
            setting.normalizeGalgameSkin();
            // 旧版本以明文保存 API Key，读取后立即重写为密文
            if (migrateApiKey) {
                saveProperties(false);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasLegacyPlaintextApiKey(JsonNode root) {
        JsonNode apiKey = root.path("aiTranslation").path("apiKey");
        return apiKey.isTextual() && !apiKey.asText().isBlank() && !EncryptedSecret.isEncrypted(apiKey.asText());
    }

    /**
     * 从数据库读取字幕黑名单，填充内存缓存。
     * <p>
     * 必须在 {@code JdbcUtils.init()} 建表之后调用，否则表尚不存在。
     */
    public static void loadTextBlacklist() {
        textBlacklist.setAll(new BlacklistTextDao().getAll());
    }

    /**
     * 更新字幕黑名单：先写数据库，成功后再刷新内存缓存。
     * <p>
     * 黑名单是「读多写极少」的数据，{@link #textBlacklist} 作为内存缓存供字幕过滤直接读取，
     * 避免每首歌检索字幕时都查一次库。
     *
     * @param rows 新的黑名单条目，空列表表示清空
     * @throws RuntimeException 数据库写入失败时抛出，此时内存缓存保持不变
     */
    public static void updateTextBlacklist(List<String> rows) {
        List<String> normalized = rows == null ? List.of() : rows.stream()
                .filter(row -> row != null && !row.isBlank())
                .distinct()
                .toList();
        new BlacklistTextDao().replaceAll(normalized);
        textBlacklist.setAll(normalized);
    }

    /**
     * 保存应用设置到 JSON 文件。
     * <p>
     * 字幕黑名单已由 {@link #updateTextBlacklist(List)} 独立落库，这里不再涉及。
     *
     * @param close 是否为退出时保存，保留参数以兼容既有调用
     */
    public static void saveProperties(boolean close){
        ObjectMapper mapper=new ObjectMapper();

        File settingFile = AppPaths.settingsFile();

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(settingFile,setting);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
