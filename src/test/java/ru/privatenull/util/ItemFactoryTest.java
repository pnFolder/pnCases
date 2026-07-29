package ru.privatenull.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemFactoryTest {

    @Test
    void humanizesUnknownMaterialNames() {
        assertEquals("Nether quartz ore", ItemFactory.humanizeMaterial("NETHER_QUARTZ_ORE"));
    }
}
