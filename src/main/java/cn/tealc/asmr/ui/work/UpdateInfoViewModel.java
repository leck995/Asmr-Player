package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * {@link UpdateInfoView} 的视图模型，持有作品各字段的 JavaFX 属性并在 {@link #save()} 时回写。
 * <p>
 * 构造时按主键重新查询作品再填充属性，避免直接复用界面列表中可能已过期的对象。
 *
 * @author Leck
 */
public class UpdateInfoViewModel implements ViewModel {
    private final WorkService workService = DI.get(WorkService.class);
    private AsmrWork asmrWork;
    private SimpleStringProperty workId;
    private SimpleBooleanProperty subtext;
    private SimpleBooleanProperty nsfw;
    private SimpleStringProperty title;
    private SimpleStringProperty titleCN;
    private SimpleStringProperty filepath;
    private SimpleStringProperty circle;
    private SimpleStringProperty releaseDate;
    private SimpleStringProperty vasText=new SimpleStringProperty();
    private SimpleStringProperty categoryText=new SimpleStringProperty();

    /**
     * 以数据库中的最新版本填充各字段属性。
     *
     * @param work 触发编辑的作品，仅用其主键重新查询，不直接复用其字段值
     */
    public UpdateInfoViewModel(AsmrWork work) {
        workId=new SimpleStringProperty();
        subtext=new SimpleBooleanProperty();
        nsfw=new SimpleBooleanProperty();
        title=new SimpleStringProperty();
        titleCN=new SimpleStringProperty();
        filepath=new SimpleStringProperty();
        circle=new SimpleStringProperty();
        releaseDate=new SimpleStringProperty();


        asmrWork = workService.getById(work.getId());
        workId.set(asmrWork.getWorkId());
        subtext.set(asmrWork.getSubtitle());
        nsfw.set(asmrWork.getNsfw());
        title.set(asmrWork.getTitle());
        titleCN.set(asmrWork.getTitleCN());
        filepath.set(asmrWork.getFilepath());
        circle.set(asmrWork.getCircle());
        releaseDate.set(asmrWork.getRelease());

        vasText.set(asmrWork.getVas());
        categoryText.set(asmrWork.getCategories());


    }


    /**
     * 将界面上的各字段写回作品对象并持久化：非空的社团/分类/声优会顺带登记进字典，
     * 空白的分类或声优清空对应字段；保存后广播 {@code MAIN_GRID_WORK_UPDATE} 让卡片刷新。
     */
    public void save(){
        DictionaryService dictService = DI.get(DictionaryService.class);

        asmrWork.setWorkId(getWorkId());
        asmrWork.setTitle(getTitle());
        asmrWork.setTitleCN(getTitleCN());
        asmrWork.setFilepath(getFilepath());
        asmrWork.setCircle(getCircle());
        if (asmrWork.getCircle() != null && asmrWork.getCircle() .length() > 0){
            dictService.addCircle(getCircle());
        }

        asmrWork.setRelease(getReleaseDate());
        asmrWork.setSubtitle(isSubtext());
        asmrWork.setNsfw(isNsfw());

        if (categoryText.get() != null && !categoryText.get().trim().isEmpty()){
            String[] split = categoryText.get().split(",");
            for (String string : split) {
                dictService.addCategory(string);
            }
            asmrWork.setCategories(categoryText.get());
        }else {
            asmrWork.setCategories("");
        }

        if (vasText.get() != null && !vasText.get().trim().isEmpty()){
            String[] split = vasText.get().split(",");
            for (String va : split) {
                dictService.addVa(va);
            }
            asmrWork.setVas(vasText.get());
        }else {
            asmrWork.setVas("");
        }
        workService.updateWork(asmrWork);
        EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE,asmrWork);
    }




    public String getWorkId() {
        return workId.get();
    }

    public SimpleStringProperty workIdProperty() {
        return workId;
    }

    public void setWorkId(String workId) {
        this.workId.set(workId);
    }

    public boolean isSubtext() {
        return subtext.get();
    }

    public SimpleBooleanProperty subtextProperty() {
        return subtext;
    }

    public void setSubtext(boolean subtext) {
        this.subtext.set(subtext);
    }

    public boolean isNsfw() {
        return nsfw.get();
    }

    public SimpleBooleanProperty nsfwProperty() {
        return nsfw;
    }

    public void setNsfw(boolean nsfw) {
        this.nsfw.set(nsfw);
    }

    public String getTitle() {
        return title.get();
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getTitleCN() {
        return titleCN.get();
    }

    public SimpleStringProperty titleCNProperty() {
        return titleCN;
    }

    public void setTitleCN(String titleCN) {
        this.titleCN.set(titleCN);
    }

    public String getFilepath() {
        return filepath.get();
    }

    public SimpleStringProperty filepathProperty() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath.set(filepath);
    }

    public String getCircle() {
        return circle.get();
    }

    public SimpleStringProperty circleProperty() {
        return circle;
    }

    public void setCircle(String circle) {
        this.circle.set(circle);
    }

    public String getReleaseDate() {
        return releaseDate.get();
    }

    public SimpleStringProperty releaseDateProperty() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate.set(releaseDate);
    }

    public AsmrWork getAsmrWork() {
        return asmrWork;
    }

    public String getVasText() {
        return vasText.get();
    }

    public SimpleStringProperty vasTextProperty() {
        return vasText;
    }

    public String getCategoryText() {
        return categoryText.get();
    }

    public SimpleStringProperty categoryTextProperty() {
        return categoryText;
    }
}
