package org.fog.test.VEC.infrastructure;

/**
 * Cloud Server – high capacity, high latency.
 */
public class CloudServer extends ComputeNode {

    private static final double CLOUD_PROPAGATION_DELAY_MS = 80.0; // WAN latency

    public CloudServer(String id, int totalMips, int totalRamMB) {
        super(id, totalMips, totalRamMB);
    }

    @Override
    public String getNodeType() { return "CLOUD"; }

    @Override
    public double getPropagationDelayMs() { return CLOUD_PROPAGATION_DELAY_MS; }

    @Override
    public String toString() {
        return String.format("CloudServer[%s | %d MIPS | %d MB RAM]", id, totalMips, totalRamMB);
    }
}

