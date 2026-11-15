package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.SaveDataDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.GalgameSaveData;

import java.util.List;

/**
 * Galgame 存档服务。
 * <p>
 * 封装存档的增删查操作，使 Galgame 模块的 ViewModel/Controller
 * 不再直接实例化 {@link SaveDataDao}。
 *
 * @author Leck
 */
public class SaveDataService {

    @Inject
    private SaveDataDao saveDataDao;

    /**
     * 新增存档记录。
     *
     * @param saveData 存档数据
     * @return 是否成功
     */
    public boolean addSave(GalgameSaveData saveData) {
        return saveDataDao.addSave(saveData);
    }

    /**
     * 按 ID 删除存档记录。
     *
     * @param id 存档 ID
     * @return 是否成功
     */
    public boolean deleteSaveById(int id) {
        return saveDataDao.deleteSaveById(id);
    }

    /**
     * 分页查询存档列表。
     *
     * @param index 页码（从 1 开始）
     * @param size  每页条数
     * @return 存档列表
     */
    public List<GalgameSaveData> getSaves(int index, int size) {
        return saveDataDao.getAllSaves(index, size);
    }

    /**
     * 查询存档总数。
     *
     * @return 存档总数
     */
    public int getSaveCount() {
        return saveDataDao.getAllSavesCount();
    }
}
