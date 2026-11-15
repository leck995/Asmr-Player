package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrWork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RenameServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"RJ123456", "BJ123456", "VJ123456"})
    void usesWorkIdInDirectoryTemplate(String workId) {
        AsmrWork work = new AsmrWork();
        work.setWorkId(workId);
        work.setTitle("作品");
        var plan = RenameService.buildRenamePlan(work, "{【workId】}{title}", false);
        assertTrue(plan.executable());
        assertEquals("【" + workId + "】作品", plan.name());
    }

    @Test
    void skipsWorkWithoutIdentifierWhenTemplateRequiresIt() {
        var plan = RenameService.buildRenamePlan(new AsmrWork(), "{workId}", false);
        assertFalse(plan.executable());
        assertTrue(plan.skipped());
    }
}
