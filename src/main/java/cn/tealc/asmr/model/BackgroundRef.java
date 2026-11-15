package cn.tealc.asmr.model;

/**
 * 背景图片引用，描述一张背景所在的位置及读取它所需的存储来源。
 *
 * @param path            本地绝对路径或 OpenList 完整路径
 * @param online          是否为远程来源
 * @param storageSourceId 存储源 ID，在线时用于重建连接；未知时为 null
 * @author Leck
 */
public record BackgroundRef(String path, boolean online, Integer storageSourceId) {
}
