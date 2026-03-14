package org.fog.test.VEC.scheduler;

import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.config.SimConstants;
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

    private final InfrastructureManager infraManager;

    // Statistics
    private final AtomicInteger totalAssigned = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicInteger cloudAssigned = new AtomicInteger(0);
    private final AtomicInteger rsuAssigned = new AtomicInteger(0);
    private final AtomicInteger localAssigned = new AtomicInteger(0);
    private final List<Task> allCompletedTasks = Collections.synchronizedList(new ArrayList<>());

    // ── RSU load-distribution tracking ──────────────────────
    // Tracks consecutive assignments to the same RSU.
    // When an RSU receives MAX_CONSECUTIVE_RSU assignments in a row,
    // the scheduler redirects to the healthiest alternative RSU.
    // This implements a fairness-aware scheduling policy:
    //   "If RSU_k has been selected C consecutive times, pick the
    //    RSU with the highest health score H(n) among the remaining
    //    candidates with available capacity."
    //
    // Per-RSU total assignment counts are used as a secondary tie-breaker
    // to ensure long-term balance across all RSU units.
    private static final int MAX_CONSECUTIVE_RSU = SimConstants.RSU_MAX_CONSECUTIVE;
    private String lastAssignedRsuId = null;
    private int consecutiveRsuCount = 0;
    private final Map<String, AtomicInteger> perRsuAssignments = new HashMap<>();

    public MLScheduler(InfrastructureManager infraManager) {
        this.infraManager = infraManager;
    }

    // --- Delegated getters for infrastructure ---
    public CloudServer getCloud() { return infraManager.getCloud(); }
    public List<RSUServer> getRsus() { return infraManager.getActiveRsus(); }
    public List<Vehicle> getVehicles() { return infraManager.getVehicles(); }
    public InfrastructureManager getInfraManager() { return infraManager; }

    /**
     * Get the local vehicle for a task. Uses the vehicle ID stored in the task
     * to find the matching vehicle instance.
     */
    public Vehicle getLocalVehicle(String vehicleId) {
        return infraManager.getVehicleById(vehicleId);
    }

    /** Get a random vehicle for task generation */
    public Vehicle getRandomVehicle() {
        return infraManager.getRandomVehicle();
    }

    /**
     * Preview which RSU the scheduler would select for display purposes.
     * Does NOT allocate resources or update tracking counters.
     *
     * Used by the TaskCreationThread to show the actual balanced RSU
     * target in the task table, rather than the raw ML suggestion.
     */
    public synchronized String previewRsuTarget(MLOffloadPredictor.OffloadDecision decision) {
        List<RSUServer> rsus = infraManager.getActiveRsus();
        ComputeNode best = selectBestRsu(decision, rsus);
        return best != null ? best.getId() : (decision.targetNodeName != null ? decision.targetNodeName : "-");
    }

    /**
     * Assign a task based on the ML prediction decision.
     *
     * CRITICAL TASK OVERRIDE:
     *   If the task is marked critical (criticalTask == 1), the scheduler
     *   bypasses the ML decision and attempts to assign it directly to the
     *   originating vehicle (local OBU).  This ensures safety-critical
     *   tasks are processed with minimal latency on-board, avoiding
     *   network uncertainty.  Only if the local vehicle lacks sufficient
     *   resources does the scheduler fall back to the normal ML-driven
     *   assignment path.
     */
    public synchronized boolean assignTask(Task task, MLOffloadPredictor.OffloadDecision decision) {

        // Resource allocation per task — kept low so local/RSU rarely saturate:
        //   requiredMips ≈ instructions / 8   (range: 60–625 MIPS per task)
        //   requiredRam  ≈ taskSize × 2 MB    (range: 4–30 MB per task)
        int requiredMips = Math.max(60, task.getNumberOfInstructions() / 8);
        int requiredRam = Math.max(4, (int) Math.ceil(task.getTaskSizeMB() * 2));

        // ── Critical-task local-first override ──────────────────────
        // If the task is critical, try to keep it on the local vehicle
        // for lowest latency and highest reliability, regardless of
        // what the ML model decided.
        if (task.getCriticalTask() == 1) {
            Vehicle localVehicle = infraManager.getVehicleById(task.getVehicleId());
            if (localVehicle != null && localVehicle.allocate(requiredMips, requiredRam)) {
                task.setOffloadDecision(false, "local");
                task.assignTo(localVehicle, requiredMips, requiredRam);
                totalAssigned.incrementAndGet();
                localAssigned.incrementAndGet();
                resetRsuStreak();
                return true;
            }
            // Vehicle lacks capacity → fall through to normal ML path
        }

        // ── Normal ML-driven assignment ─────────────────────────────
        ComputeNode targetNode = resolveTargetNode(task, decision);

        if (targetNode == null) {
            return false;
        }

        if (targetNode.allocate(requiredMips, requiredRam)) {
            task.setOffloadDecision(
                    !"local".equals(decision.actnetDecision),
                    decision.actnetDecision
            );
            task.assignTo(targetNode, requiredMips, requiredRam);

            totalAssigned.incrementAndGet();
            incrementTargetCounter(targetNode);

            // Track consecutive RSU assignments
            if ("RSU".equals(targetNode.getNodeType())) {
                trackRsuAssignment(targetNode.getId());
            } else {
                resetRsuStreak();
            }

            return true;
        } else {
            // Fallback: if local or RSU has insufficient resources, allocate to cloud
            if (!"cloud".equals(decision.actnetDecision.toLowerCase())) {
                CloudServer cloud = infraManager.getCloud();
                if (cloud.allocate(requiredMips, requiredRam)) {
                    task.setOffloadDecision(true, "cloud");
                    task.assignTo(cloud, requiredMips, requiredRam);
                    totalAssigned.incrementAndGet();
                    cloudAssigned.incrementAndGet();
                    resetRsuStreak();
                    return true;
                }
            }
            return false;
        }
    }

    // ── RSU streak tracking helpers ─────────────────────────
    private void trackRsuAssignment(String rsuId) {
        if (rsuId.equals(lastAssignedRsuId)) {
            consecutiveRsuCount++;
        } else {
            lastAssignedRsuId = rsuId;
            consecutiveRsuCount = 1;
        }
        perRsuAssignments.computeIfAbsent(rsuId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void resetRsuStreak() {
        lastAssignedRsuId = null;
        consecutiveRsuCount = 0;
    }

    /**
     * Resolve the target compute node from the ML decision.
     *
     * For RSU decisions, applies a fairness-aware load-distribution policy:
     *
     *   1. If the ML-recommended RSU has been assigned MAX_CONSECUTIVE_RSU
     *      times in a row, redirect to the healthiest alternative RSU.
     *
     *   2. Among all active RSUs with available capacity, select the one
     *      with the highest composite health score:
     *        H(n) = 1 - (α · U_cpu + β · U_ram)
     *
     *   3. Tie-breaker: the RSU with fewer total assignments gets priority,
     *      ensuring long-term balance across all RSU units.
     *
     * This prevents a single high-capacity RSU from absorbing all tasks
     * while others sit idle, creating realistic edge-cloud contention.
     */
    private ComputeNode resolveTargetNode(Task task, MLOffloadPredictor.OffloadDecision decision) {
        List<RSUServer> rsus = infraManager.getActiveRsus();

        switch (decision.actnetDecision.toLowerCase()) {
            case "local":
                return infraManager.getVehicleById(task.getVehicleId());
            case "cloud":
                return infraManager.getCloud();
            case "rsu":
                return selectBestRsu(decision, rsus);
            default:
                return infraManager.getVehicleById(task.getVehicleId());
        }
    }

    /**
     * Select the best RSU using health-score-based load distribution.
     *
     * Algorithm:
     *   1. If the ML-recommended RSU has NOT exceeded the consecutive
     *      assignment limit and has capacity → use it.
     *   2. Otherwise, score all active RSUs:
     *        score(r) = H(r) × 0.7 + fairnessBonus(r) × 0.3
     *      where fairnessBonus = 1 - (assigns_r / max_assigns_any_rsu)
     *   3. Pick the RSU with the highest combined score that has capacity.
     */
    private ComputeNode selectBestRsu(MLOffloadPredictor.OffloadDecision decision,
                                      List<RSUServer> rsus) {
        if (rsus.isEmpty()) return null;

        // Step 1: Try ML-recommended RSU if under consecutive limit
        if (decision.targetNodeName != null && consecutiveRsuCount < MAX_CONSECUTIVE_RSU) {
            for (RSUServer rsu : rsus) {
                if (rsu.getId().equalsIgnoreCase(decision.targetNodeName)
                        && rsu.getAvailableMips() > 0 && rsu.getAvailableRamMB() > 0) {
                    return rsu;
                }
            }
        }

        // Step 2: Find the maximum assignment count across all RSUs (for fairness)
        int maxAssigns = 1;
        for (RSUServer rsu : rsus) {
            AtomicInteger cnt = perRsuAssignments.get(rsu.getId());
            if (cnt != null && cnt.get() > maxAssigns) {
                maxAssigns = cnt.get();
            }
        }

        // Step 3: Score each RSU and pick the best
        RSUServer bestRsu = null;
        double bestScore = -1;

        for (RSUServer rsu : rsus) {
            if (rsu.getAvailableMips() <= 0 || rsu.getAvailableRamMB() <= 0) continue;

            // Skip the RSU that just exceeded its consecutive streak
            if (rsu.getId().equals(lastAssignedRsuId) && consecutiveRsuCount >= MAX_CONSECUTIVE_RSU) {
                continue;
            }

            double health = rsu.getHealthScore();
            int assigns = perRsuAssignments.getOrDefault(rsu.getId(), new AtomicInteger(0)).get();
            double fairness = 1.0 - ((double) assigns / maxAssigns);

            // Composite: 70% health + 30% fairness
            double score = 0.7 * health + 0.3 * fairness;

            if (score > bestScore) {
                bestScore = score;
                bestRsu = rsu;
            }
        }

        // Step 4: If all alternatives were skipped (only the streak RSU has capacity),
        // allow it anyway rather than failing
        if (bestRsu == null) {
            for (RSUServer rsu : rsus) {
                if (rsu.getAvailableMips() > 0 && rsu.getAvailableRamMB() > 0) {
                    return rsu;
                }
            }
        }

        return bestRsu;
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

        // Print per-task execution & resource hold details
        printTaskExecutionDetails();

        printInfrastructureHealth();
    }

    /**
     * Print tabular per-task execution and resource-hold details.
     *
     * For each completed task shows:
     *   - T_exec  = (I / F) × 1000 ms
     *   - T_trans = (D × 8 / B) × 1000 ms
     *   - T_prop  = node propagation delay ms
     *   - Hold    = actual resource hold duration ms
     *   - Allocated MIPS and RAM
     */
    private void printTaskExecutionDetails() {
        if (allCompletedTasks.isEmpty()) return;

        ConsoleFormatter.printTaskExecutionHeader();

        for (Task t : allCompletedTasks) {
            double tExec = 0, tTrans = 0, tProp = 0;
            if (t.getAssignedNode() != null && t.getAllocatedMips() > 0) {
                tExec = ((double) t.getNumberOfInstructions() / t.getAllocatedMips()) * 1000.0;
                tTrans = (t.getTaskSizeMB() * 8.0 / t.getBandwidthMbps()) * 1000.0;
                tProp = t.getAssignedNode().getPropagationDelayMs();
            }

            ConsoleFormatter.printTaskExecutionRow(
                    t.getTaskId(),
                    t.getOffloadTarget() != null ? t.getOffloadTarget().toUpperCase() : "-",
                    tExec, tTrans, tProp,
                    t.getResourceHoldDurationMs(),
                    t.getAllocatedMips(),
                    t.getAllocatedRamMB(),
                    t.getState().toString()
            );
        }

        ConsoleFormatter.printTaskExecutionFooter();
    }

    /**
     * Print infrastructure health status for all nodes.
     */
    public void printInfrastructureHealth() {
        ConsoleFormatter.printInfrastructureHeader();

        // Cloud
        CloudServer cloud = infraManager.getCloud();
        ConsoleFormatter.printInfrastructureRow(
                cloud.getId(), cloud.getNodeType(),
                cloud.getTotalMips(), cloud.getAvailableMips(),
                cloud.getTotalRamMB(), cloud.getAvailableRamMB(),
                cloud.getCpuUtilization(), cloud.getRamUtilization(),
                cloud.getHealthScore(), cloud.getActiveTasks()
        );

        // RSUs
        for (RSUServer rsu : infraManager.getActiveRsus()) {
            ConsoleFormatter.printInfrastructureRow(
                    rsu.getId(), rsu.getNodeType(),
                    rsu.getTotalMips(), rsu.getAvailableMips(),
                    rsu.getTotalRamMB(), rsu.getAvailableRamMB(),
                    rsu.getCpuUtilization(), rsu.getRamUtilization(),
                    rsu.getHealthScore(), rsu.getActiveTasks()
            );
        }

        // All Vehicles
        for (Vehicle v : infraManager.getVehicles()) {
            ConsoleFormatter.printInfrastructureRow(
                    v.getId(), v.getNodeType(),
                    v.getTotalMips(), v.getAvailableMips(),
                    v.getTotalRamMB(), v.getAvailableRamMB(),
                    v.getCpuUtilization(), v.getRamUtilization(),
                    v.getHealthScore(), v.getActiveTasks()
            );
        }

        ConsoleFormatter.printInfrastructureFooter();
    }
}

