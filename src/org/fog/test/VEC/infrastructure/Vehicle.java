package org.fog.test.VEC.infrastructure;

/**
 * Vehicle On-Board Unit (OBU) – limited capacity, near-zero propagation delay (local).
 */
public class Vehicle extends ComputeNode {

    private static final double VEHICLE_LOCAL_DELAY_MS = 1.0;

    public Vehicle(String id, int totalMips, int totalRamMB) {
        super(id, totalMips, totalRamMB);
    }

    @Override
    public String getNodeType() { return "VEHICLE"; }

    @Override
    public double getPropagationDelayMs() { return VEHICLE_LOCAL_DELAY_MS; }

    @Override
    public String toString() {
        return String.format("Vehicle[%s | %d MIPS | %d MB RAM]", id, totalMips, totalRamMB);
    }
}

