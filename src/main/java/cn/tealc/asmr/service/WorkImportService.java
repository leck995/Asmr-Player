package cn.tealc.asmr.service;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.task.WorkScanTask;


/**
 * 作品导入服务，负责媒体库批量扫描任务创建。
 * <p>
 * 扫描任务通过媒体来源接口操作文件，屏蔽本地磁盘与远程来源的差异。
 * <p>
 * 批量扫描逻辑封装在 {@link WorkScanTask} 中，本服务只负责创建和返回任务。
 *
 * @author Leck
 */
public class WorkImportService {

    /**
     * 创建批量扫描任务。
     * <p>
     * 返回的 {@link WorkScanTask} 继承 {@link cn.tealc.asmr.task.background.BackgroundTask}，
     * 支持暂停/恢复、取消、去重和打开日志。上层应通过 {@code TaskManager.submit(task)} 提交。
     *
     * @param rootPath    媒体库根目录路径
     * @param level       目录层级（0 起，0 表示根目录的直接子目录）
     * @param titleCN     是否设置文件夹名称为中文标题
     * @param forceUpdate 是否强制更新已存在的作品
     * @param storageSource   存储源
     * @param mediaLibraryId 所属媒体库 ID
     * @return 扫描任务
     */
    public WorkScanTask scanLibrary(String rootPath, int level, boolean titleCN, boolean forceUpdate,
                                    StorageSource storageSource, int mediaLibraryId) {
        return new WorkScanTask(rootPath, level, titleCN, forceUpdate, storageSource, mediaLibraryId, DI.get(WorkService.class));
    }


}
