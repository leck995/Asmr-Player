package cn.tealc.asmr.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AsmrWorkTest {
    @ParameterizedTest
    @CsvSource({"rj1234567,RJ01234567", "bj1234567,BJ1234567", "vj1234567,VJ1234567"})
    void normalizesSupportedWorkIds(String input, String expected) {
        AsmrWork work = new AsmrWork();
        work.setWorkId(input);
        assertEquals(expected, work.getWorkId());
    }

    @Test
    void serializesAndReadsGenericIdentityProperties() throws Exception {
        AsmrWork work = new AsmrWork();
        work.setId(42);
        work.setWorkId("VJ123456");
        work.setRelatedWorkIds("BJ123456#中文");
        work.setOriginWorkId("VJ654321");
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(work));
        assertEquals(42, json.get("id").asInt());
        assertEquals("VJ123456", json.get("workId").asText());
        assertFalse(json.has("rjId"));
        assertFalse(json.has("relatedRJ"));
        assertFalse(json.has("originRJ"));
        AsmrWork loaded = mapper.treeToValue(json, AsmrWork.class);
        assertEquals(work.getId(), loaded.getId());
        assertEquals(work.getWorkId(), loaded.getWorkId());
        assertEquals(work.getRelatedWorkIds(), loaded.getRelatedWorkIds());
        assertEquals(work.getOriginWorkId(), loaded.getOriginWorkId());
    }

    @Test
    void acceptsAbsentWorkIdAndDisplaysDatabaseId() {
        AsmrWork work = new AsmrWork();
        work.setId(42);
        work.setTitle("无编号作品");
        work.setWorkId(null);
        assertNull(work.getWorkId());
        assertEquals("无编号作品【ID42】", work.toString());
    }
}
