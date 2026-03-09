package org.fog.test.VEC.infrastructure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for all compute infrastructure.
 * Models MIPS capacity, RAM, and current utilization.
 */
public abstract class ComputeNode {

    protected final String id;
    protected final int totalMips;
    protected final int totalRamMB;
    protected AtomicInteger usedMips;
    protected AtomicInteger usedRamMB;
    protected AtomicInteger activeTasks;

    public ComputeNode(String id, int totalMips, int totalRamMB) {
        this.id = id;
        this.totalMips = totalMips;
        this.totalRamMB = totalRamMB;
        this.usedMips = new AtomicInteger(0);
        this.usedRamMB = new AtomicInteger(0);
        this.activeTasks = new AtomicInteger(0);
    }

    public String getId() { return id; }
    public int getTotalMips() { return totalMips; }
    public int getTotalRamMB() { return totalRamMB; }

    public int getAvailableMips() { return totalMips - usedMips.get(); }
    public int getAvailableRamMB() { return totalRamMB - usedRamMB.get(); }
    public int getActiveTasks() { return activeTasks.get(); }

    /**
     * CPU utilization ratio ∈ [0, 1]
     * U_cpu = usedMips / totalMips
     */
    public double getCpuUtilization() {
        return (double) usedMips.get() / totalMips;
    }

    /**
     * RAM utilization ratio ∈ [0, 1]
     */
    public double getRamUtilization() {
        return (double) usedRamMB.get() / totalRamMB;
    }

    /**
     * Composite health score ∈ [0, 1], higher = healthier
     * H(n) = 1 - (α · U_cpu + β · U_ram)
     * where α = 0.6, β = 0.4  (CPU-weighted)
     */
    public double getHealthScore() {
        double alpha = 0.6;
        double beta = 0.4;
        return 1.0 - (alpha * getCpuUtilization() + beta * getRamUtilization());
    }

    public synchronized boolean allocate(int mips, int ramMB) {
        if (getAvailableMips() >= mips && getAvailableRamMB() >= ramMB) {
            usedMips.addAndGet(mips);
            usedRamMB.addAndGet(ramMB);
            activeTasks.incrementAndGet();
            return true;
        }
        return false;
    }

    public synchronized void release(int mips, int ramMB) {
        usedMips.addAndGet(-mips);
        usedRamMB.addAndGet(-ramMB);
        activeTasks.decrementAndGet();
        if (usedMips.get() < 0) usedMips.set(0);
        if (usedRamMB.get() < 0) usedRamMB.set(0);
        if (activeTasks.get() < 0) activeTasks.set(0);
    }

    /** Node type for scheduling decisions */
    public abstract String getNodeType(); // "CLOUD", "RSU", "VEHICLE"

    /** Propagation delay in ms (network latency to reach this node) */
    public abstract double getPropagationDelayMs();
}

