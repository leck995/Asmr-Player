package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolViewModelTest extends BaseDatabaseTest {
    @BeforeEach
    void bindDao() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
    }

    @Test
    void repeatedDetectionReplacesResultsInsteadOfAccumulatingGroups() {
        WorkService service = DI.get(WorkService.class);
        service.addWork(work(1, "RJ100000"));
        service.addWork(work(2, "rj100000"));
        ToolViewModel vm = new ToolViewModel();
        DI.inject(vm);
        vm.checkDuplicate();
        vm.checkDuplicate();
        assertEquals(1, vm.getDuplicatedWorks().size());
        assertEquals(2, vm.getDuplicatedWorks().getFirst().getDuplicatedList().size());
        assertEquals("检测到1组重复作品", vm.getDuplicatedTip());
    }

    @Test
    void removingVersionDropsSingletonGroupsAndUpdatesCount() {
        ToolViewModel vm = new ToolViewModel();
        vm.getDuplicatedWorks().setAll(ToolViewModel.groupDuplicates(List.of(
                work(1, "RJ100000"), work(2, "RJ100000"), work(3, "RJ100000"),
                work(4, "BJ200000"), work(5, "BJ200000"))));
        vm.removeDuplicateWorks(Set.of(1, 4));
        assertEquals(1, vm.getDuplicatedWorks().size());
        assertEquals(List.of(2, 3), vm.getDuplicatedWorks().getFirst().getDuplicatedList().stream()
                .map(AsmrWork::getId).toList());
        assertEquals("检测到1组重复作品", vm.getDuplicatedTip());
    }

    @Test
    void missingOriginIdsDoNotBecomeADuplicateGroup() {
        assertTrue(ToolViewModel.groupDuplicates(List.of(work(1, null), work(2, null),
                work(3, ""), work(4, ""))).isEmpty());
    }

    private AsmrWork work(int id, String origin) {
        AsmrWork work = new AsmrWork();
        work.setId(id);
        work.setWorkId("RJ" + (100000 + id));
        work.setOriginWorkId(origin);
        work.setFilepath("/library/" + id);
        work.setTitle("测试作品" + id);
        work.setStorageSourceId(1);
        work.setStorageType(0);
        return work;
    }
}
