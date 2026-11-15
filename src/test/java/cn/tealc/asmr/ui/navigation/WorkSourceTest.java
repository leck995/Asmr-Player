package cn.tealc.asmr.ui.navigation;

import cn.tealc.asmr.model.AsmrKeyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSourceTest {

    @Test
    void exposesPageTitleAndDescription() {
        assertEquals("全部", WorkSource.all().title());
        assertEquals("媒体库中的全部内容", WorkSource.all().description());

        assertEquals("收藏", WorkSource.favorites().title());
        assertEquals("你收藏的内容", WorkSource.favorites().description());

        WorkSource search = WorkSource.of(AsmrKeyType.SEARCH, "RJ123");
        assertEquals("搜索：RJ123", search.title());
        assertTrue(search.description().contains("RJ123"));

        WorkSource category = WorkSource.of(AsmrKeyType.CATEGORY, "耳语");
        assertEquals("分类：耳语", category.title());
        assertEquals("分类 “耳语” 下的内容", category.description());
    }
}
