package org.fog.test.VEC.infrastructure;

/**
 * Roadside Unit (RSU) Edge Server – moderate capacity, low latency.
 * Includes bandwidth and SINR (Signal-to-Interference-plus-Noise Ratio) for ML prediction.
 */
public class RSUServer extends ComputeNode {

    private static final double RSU_PROPAGATION_DELAY_MS = 10.0;

    private final double bandwidthMbps;  // RSU link bandwidth
    private final double sinr;           // Signal-to-Interference-plus-Noise Ratio (dB)

    public RSUServer(String id, int totalMips, int totalRamMB, double bandwidthMbps, double sinr) {
        super(id, totalMips, totalRamMB);
        this.bandwidthMbps = bandwidthMbps;
        this.sinr = sinr;
    }

    public double getBandwidthMbps() { return bandwidthMbps; }
    public double getSinr() { return sinr; }

    @Override
    public String getNodeType() { return "RSU"; }

    @Override
    public double getPropagationDelayMs() { return RSU_PROPAGATION_DELAY_MS; }

    @Override
    public String toString() {
        return String.format("RSUServer[%s | %d MIPS | %d MB RAM | %.0f Mbps | SINR=%.1f dB]",
                id, totalMips, totalRamMB, bandwidthMbps, sinr);
    }
}

