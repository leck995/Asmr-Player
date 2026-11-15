package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import javafx.beans.property.SimpleStringProperty;


/**
 * 主界面 ViewModel。
 * <p>
 * 承担主搜索框状态和搜索跳转的编排职责。
 * View 层不直接持有 Service，所有数据操作通过本类委派。
 *
 * @author Leck
 */
public class MainViewModel implements ViewModel {
    @Inject
    private NavigationService navigationService;
    private SimpleStringProperty searchValue;

    public MainViewModel() {
        searchValue = new SimpleStringProperty();

        // 搜索框清空时自动回到全部列表
        searchValue.addListener((observableValue, s, t1) -> {
            if (t1.length() == 0) {
                search("", AsmrKeyType.ALL);
            }
        });
    }

    /**
     * 触发搜索：打开关键字对应的作品列表。
     * 清空搜索框回到“全部”时作为根页面切换，避免后退栈里留下空搜索页。
     */
    public void search(String key, AsmrKeyType type) {
        WorksRoute route = new WorksRoute(WorkSource.of(type, key));
        if (type == AsmrKeyType.ALL) {
            navigationService.openRoot(route);
        } else {
            navigationService.push(route);
        }
    }

    public String getSearchValue() {
        return searchValue.get();
    }

    public SimpleStringProperty searchValueProperty() {
        return searchValue;
    }

    public void setSearchValue(String searchValue) {
        this.searchValue.set(searchValue);
    }
}
