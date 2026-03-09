package org.fog.test.VEC.scheduler;

import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.task.Task;
import org.fog.test.VEC.task.MLOffloadPredictor;
import org.fog.test.VEC.utils.ConsoleFormatter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ML-Driven Scheduler
 *
 * All offloading decisions are made by the external ML service at
 * http://127.0.0.1:8000/predict. This scheduler:
 *
 *   1. Provides infrastructure state snapshots for ML requests
 *   2. Allocates resources on the target node chosen by ML
 *   3. Tracks statistics and infrastructure health
 *
 * === Research Formulas Used ===
 *
 * 1) Execution Time Model (Shannon, 1948; adapted):
 *    T_exec = I / F  (Instructions / Processing speed in MIPS)
 *
 * 2) Transfer Time (Shannon Capacity):
 *    T_trans = (D × 8) / B  (Data size in MB × 8 bits / Bandwidth in Mbps)
 *
 * 3) Total Task Delay:
 *    T_total = T_exec + T_trans + T_prop
 *    where T_prop = propagation delay (network latency to reach node)
 *
 * 4) Health Score (Composite Utilization Metric):
 *    H(n) = 1 - (α · U_cpu + β · U_ram)
 *    where α = 0.6, β = 0.4 (CPU-weighted)
 *    U_cpu = usedMips / totalMips
 *    U_ram = usedRam / totalRam
 *
 * 5) Energy Consumption Model (simplified CMOS dynamic power):
 *    E = κ · f² · I
 *    where κ = capacitance coefficient, f = frequency proxy (MIPS), I = instructions
 */
public class MLScheduler {

    private final CloudServer cloud;
    private final List<RSUServer> rsus;
    private final Vehicle localVehicle;

    // Statistics
    private final AtomicInteger totalAssigned = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicInteger cloudAssigned = new AtomicInteger(0);
    private final AtomicInteger rsuAssigned = new AtomicInteger(0);
    private final AtomicInteger localAssigned = new AtomicInteger(0);
    private final List<Task> allCompletedTasks = Collections.synchronizedList(new ArrayList<>());

    public MLScheduler(CloudServer cloud, List<RSUServer> rsus, Vehicle localVehicle) {
        this.cloud = cloud;
        this.rsus = rsus;
        this.localVehicle = localVehicle;
    }

    // --- Getters for infrastructure ---
    public CloudServer getCloud() { return cloud; }
    public List<RSUServer> getRsus() { return rsus; }
    public Vehicle getLocalVehicle() { return localVehicle; }

    /**
     * Assign a task based on the ML prediction decision.
     *
     * @param task     The task to assign
     * @param decision The ML offloading decision
     * @return true if task was successfully assigned, false otherwise
     */
    public synchronized boolean assignTask(Task task, MLOffloadPredictor.OffloadDecision decision) {
        ComputeNode targetNode = resolveTargetNode(decision);

        if (targetNode == null) {
            System.out.printf("  [SCHEDULER] No valid target node for %s (decision=%s)%n",
                    task.getTaskId(), decision.actnetDecision);
            return false;
        }

        int requiredMips = Math.max(100, task.getNumberOfInstructions() / 10);
        int requiredRam = (int) Math.ceil(task.getTaskSizeMB());

        if (targetNode.allocate(requiredMips, requiredRam)) {
            task.setOffloadDecision(
                    !"local".equals(decision.actnetDecision),
                    decision.actnetDecision
            );
            task.assignTo(targetNode, requiredMips, requiredRam);

            totalAssigned.incrementAndGet();
            incrementTargetCounter(targetNode);
            return true;
        } else {
            // Fallback: if local or RSU has insufficient resources, try cloud
            if (!"cloud".equals(decision.actnetDecision.toLowerCase())) {
                if (cloud.allocate(requiredMips, requiredRam)) {
                    task.setOffloadDecision(true, "cloud");
                    task.assignTo(cloud, requiredMips, requiredRam);
                    totalAssigned.incrementAndGet();
                    cloudAssigned.incrementAndGet();
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Resolve the target compute node from the ML decision.
     */
    private ComputeNode resolveTargetNode(MLOffloadPredictor.OffloadDecision decision) {
        switch (decision.actnetDecision.toLowerCase()) {
            case "local":
                return localVehicle;
            case "cloud":
                return cloud;
            case "rsu":
                if (decision.targetNodeName != null) {
                    for (RSUServer rsu : rsus) {
                        if (rsu.getId().equalsIgnoreCase(decision.targetNodeName)) {
                            return rsu;
                        }
                    }
                    System.out.printf("  [SCHEDULER] Target RSU '%s' not found, using first available RSU%n",
                            decision.targetNodeName);
                }
                // Fallback: use first RSU with capacity
                for (RSUServer rsu : rsus) {
                    if (rsu.getAvailableMips() > 0 && rsu.getAvailableRamMB() > 0) {
                        return rsu;
                    }
                }
                return rsus.isEmpty() ? null : rsus.get(0);
            default:
                return localVehicle;
        }
    }

    private void incrementTargetCounter(ComputeNode node) {
        switch (node.getNodeType()) {
            case "CLOUD":   cloudAssigned.incrementAndGet(); break;
            case "RSU":     rsuAssigned.incrementAndGet(); break;
            case "VEHICLE": localAssigned.incrementAndGet(); break;
        }
    }

    public void recordCompletion(Task task) {
        allCompletedTasks.add(task);
        completedTasks.incrementAndGet();
        long latency = task.getCompletionTimeMs() - task.getCreationTimeMs();
        totalLatencyMs.addAndGet(latency);
    }

    public void recordFailure() {
        totalFailed.incrementAndGet();
    }

    /**
     * Print simulation statistics and infrastructure health.
     */
    public void printStatistics() {
        int completed = completedTasks.get();
        double avgLatency = completed > 0 ? (double) totalLatencyMs.get() / completed : 0;

        ConsoleFormatter.printStatisticsTable(
                totalAssigned.get(),
                completed,
                totalFailed.get(),
                avgLatency,
                cloudAssigned.get(),
                rsuAssigned.get(),
                localAssigned.get()
        );

        printInfrastructureHealth();
    }

    /**
     * Print infrastructure health status for all nodes.
     */
    public void printInfrastructureHealth() {
        ConsoleFormatter.printInfrastructureHeader();

        // Cloud
        ConsoleFormatter.printInfrastructureRow(
                cloud.getId(), cloud.getNodeType(),
                cloud.getTotalMips(), cloud.getAvailableMips(),
                cloud.getTotalRamMB(), cloud.getAvailableRamMB(),
                cloud.getCpuUtilization(), cloud.getRamUtilization(),
                cloud.getHealthScore(), cloud.getActiveTasks()
        );

        // RSUs
        for (RSUServer rsu : rsus) {
            ConsoleFormatter.printInfrastructureRow(
                    rsu.getId(), rsu.getNodeType(),
                    rsu.getTotalMips(), rsu.getAvailableMips(),
                    rsu.getTotalRamMB(), rsu.getAvailableRamMB(),
                    rsu.getCpuUtilization(), rsu.getRamUtilization(),
                    rsu.getHealthScore(), rsu.getActiveTasks()
            );
        }

        // Local Vehicle
        ConsoleFormatter.printInfrastructureRow(
                localVehicle.getId() + " (Local)", localVehicle.getNodeType(),
                localVehicle.getTotalMips(), localVehicle.getAvailableMips(),
                localVehicle.getTotalRamMB(), localVehicle.getAvailableRamMB(),
                localVehicle.getCpuUtilization(), localVehicle.getRamUtilization(),
                localVehicle.getHealthScore(), localVehicle.getActiveTasks()
        );

        ConsoleFormatter.printInfrastructureFooter();
    }
}

