package org.zlab.upfuzz.hbase;

import org.junit.jupiter.api.Test;
import org.zlab.upfuzz.AbstractTest;
import org.zlab.upfuzz.fuzzingengine.Config;
import org.zlab.upfuzz.fuzzingengine.server.Seed;

public class CommandSequenceTest extends AbstractTest {
    public static HBaseCommandPool hbaseCommandPool = new HBaseCommandPool();

    @Test
    public void testSequenceGeneration() {
        Config.getConf().system = "hbase";
        Seed seed = generateSeed(hbaseCommandPool, HBaseState.class, -1);
        assert seed != null;
        printSeed(seed);
        boolean status = seed.mutate(hbaseCommandPool, HBaseState.class);
        System.out.println("mutate status = " + status);
        printSeed(seed);
    }
}
