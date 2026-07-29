package ru.privatenull.cases.config;

import org.junit.jupiter.api.Test;
import ru.privatenull.cases.model.Reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RewardConfigParserTest {

    @Test
    void supportsCanonicalTypeNamesOnly() {
        assertEquals(Reward.Type.VAULT, RewardConfigParser.parseType("vault"));
        assertEquals(Reward.Type.PLAYERPOINTS, RewardConfigParser.parseType("playerpoints"));
        assertEquals(Reward.Type.LUCKPERMS, RewardConfigParser.parseType("luckperms"));
        assertNull(RewardConfigParser.parseType("money"));
        assertNull(RewardConfigParser.parseType("unknown"));
    }
}
