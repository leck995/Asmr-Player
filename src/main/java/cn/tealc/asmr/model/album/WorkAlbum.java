package cn.tealc.asmr.model.album;

import java.time.LocalDateTime;

/** 作品集信息，最后添加时间不随成员移除而回退。 */
public record WorkAlbum(int id, String name, String description,
                        LocalDateTime createdAt, LocalDateTime lastAddedAt) {
}
