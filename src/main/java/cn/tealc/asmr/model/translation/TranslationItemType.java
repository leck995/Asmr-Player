package cn.tealc.asmr.model.translation;

/**
 * 翻译对象类型。第一期仅写入 {@link #WORK_TITLE}，其余类型为后续安全重命名预留。
 */
public enum TranslationItemType {
    WORK_TITLE,
    WORK_DIRECTORY,
    FILE_NAME
}
