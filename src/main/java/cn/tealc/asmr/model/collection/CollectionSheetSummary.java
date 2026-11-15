package cn.tealc.asmr.model.collection;

import cn.tealc.asmr.model.AsmrWork;

/**
 * 歌单主页展示摘要。
 */
public class CollectionSheetSummary {
    private SheetData sheet;
    private int itemCount;
    private AsmrWork latestWork;

    public SheetData getSheet() {
        return sheet;
    }

    public void setSheet(SheetData sheet) {
        this.sheet = sheet;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public AsmrWork getLatestWork() {
        return latestWork;
    }

    public void setLatestWork(AsmrWork latestWork) {
        this.latestWork = latestWork;
    }
}
