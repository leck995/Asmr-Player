package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.service.CollectionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * 歌单详情弹窗的数据模型。
 */
public class CollectionDetailViewModel implements ViewModel {
    private final CollectionService collectionService = DI.get(CollectionService.class);
    private final SheetData sheet;
    private final ObservableList<ItemDataEx> itemList = FXCollections.observableArrayList();

    public CollectionDetailViewModel(SheetData sheet) {
        this.sheet = sheet;
        refreshItems();
    }

    public void refreshItems() {
        itemList.setAll(collectionService.getItemsWithWorkInfoBySheet(sheet.getId()));
    }

    public void playAll() {
        if (!itemList.isEmpty()) {
            List<AudioTrack> playlist = itemList.stream().map(AudioTrack::new).toList();
            VlcPlayer.getInstance().initAndPlay(playlist, 0);
        }
    }

    public void playSelected(int index) {
        if (index >= 0 && index < itemList.size()) {
            VlcPlayer.getInstance().addAndPlay(new AudioTrack(itemList.get(index)));
        }
    }

    public void addSelected(int index) {
        if (index >= 0 && index < itemList.size()) {
            VlcPlayer.getInstance().add(new AudioTrack(itemList.get(index)));
        }
    }

    public boolean deleteItem(int index) {
        if (index < 0 || index >= itemList.size()) {
            return false;
        }
        ItemDataEx item = itemList.get(index);
        boolean status = collectionService.deleteItem(item.getId());
        if (status) {
            itemList.remove(item);
        }
        return status;
    }

    public boolean updateSheet(String name, String description) {
        boolean status = collectionService.updateSheetDetails(sheet.getId(), name, description);
        if (status) {
            SheetData refreshed = collectionService.getSheetById(sheet.getId());
            if (refreshed != null) {
                sheet.setSheetName(refreshed.getSheetName());
                sheet.setDescription(refreshed.getDescription());
                sheet.setUpdateDateTime(refreshed.getUpdateDateTime());
            }
        }
        return status;
    }

    public boolean deleteSheet() {
        return collectionService.deleteSheet(sheet.getId());
    }

    public SheetData getSheet() {
        return sheet;
    }

    public ObservableList<ItemDataEx> getItemList() {
        return itemList;
    }
}
