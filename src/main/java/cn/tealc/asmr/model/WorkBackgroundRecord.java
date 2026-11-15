package cn.tealc.asmr.model;

/**
 * 作品背景记录，对应 {@code asmr_work_background} 一行。
 *
 * @param workRecordId    作品记录 ID（{@code asmr_works.id}）
 * @param storageType     存储类型编码
 * @param storageSourceId 存储源 ID
 * @param sourcePath      当前背景路径；null 表示已扫描但没有可用背景
 * @param scannedAt       最近扫描或更新时间（毫秒时间戳）
 * @author Leck
 */
public record WorkBackgroundRecord(int workRecordId,
                                   int storageType,
                                   int storageSourceId,
                                   String sourcePath,
                                   long scannedAt) {
}
