package org.fog.test.VEC.task;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.scheduler.MLScheduler;
import org.fog.test.VEC.utils.ConsoleFormatter;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread 1: Task Creation
 *
 * Generates tasks with randomized parameters. Before each task is generated,
 * the infrastructure state is refreshed. The task + infrastructure are then
 * sent to the ML predictor to obtain the offload decision.
 *
 * Each task is assigned to a random vehicle from the vehicle pool.
 * RSU topology is changed dynamically at configurable intervals.
 *
 * Task parameters generated:
 *   - mobility_status (low, medium, high)
 *   - signal_strength (dBm)
 *   - critical_task (0 or 1)
 *   - bandwidth_mbps
 *   - number_of_instructions (MIPS)
 *   - task_size_MB
 */
public class TaskCreationThread implements Runnable {

    private final MLScheduler scheduler;
    private final BlockingQueue<TaskWithDecision> createdTaskQueue;
    private final int durationSec;
    private static final AtomicInteger taskCounter = new AtomicInteger(0);

    public TaskCreationThread(MLScheduler scheduler,
                              BlockingQueue<TaskWithDecision> createdTaskQueue,
                              int durationSec) {
        this.scheduler = scheduler;
        this.createdTaskQueue = createdTaskQueue;
        this.durationSec = durationSec;
    }

    @Override
    public void run() {
        System.out.println("\n[CREATION] Thread started.");
        Random rand = new Random();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSec * 1000L);
        boolean headerPrinted = false;
        int taskNum = 0;
        long lastTopologyChange = startTime;

        while (System.currentTimeMillis() < endTime) {
            taskNum++;
            long now = System.currentTimeMillis();

            // ── Dynamic RSU topology change ──
            if (now - lastTopologyChange >= SimConstants.RSU_TOPOLOGY_CHANGE_INTERVAL_SEC * 1000L) {
                if (headerPrinted) {
                    ConsoleFormatter.printTaskTableFooter();
                    headerPrinted = false;
                }
                scheduler.getInfraManager().changeRsuTopology();
                scheduler.printInfrastructureHealth();
                lastTopologyChange = now;
            }

            // Pick a random vehicle as the task originator
            Vehicle sourceVehicle = scheduler.getRandomVehicle();

            // Generate task from this vehicle
            Task task = generateTask(rand, sourceVehicle);

            // Print table header once (or after topology change)
            if (!headerPrinted) {
                ConsoleFormatter.printHeader("TASK GENERATION & ML OFFLOADING");
                ConsoleFormatter.printTaskTableHeader();
                headerPrinted = true;
            }

            // Print infrastructure health every 5 tasks
            if (taskNum > 1 && taskNum % 5 == 1) {
                ConsoleFormatter.printTaskTableFooter();
                scheduler.printInfrastructureHealth();
                ConsoleFormatter.printTaskTableHeader();
            }

            // Call ML predictor with current infrastructure state
            MLOffloadPredictor.OffloadDecision decision = MLOffloadPredictor.predict(
                    task,
                    scheduler.getLocalVehicle(sourceVehicle.getId()),
                    scheduler.getCloud(),
                    scheduler.getRsus()
            );

            // ── Critical-task local-first override (display) ──────────
            // If the task is critical and the local vehicle has enough
            // resources, the scheduler will force-assign it locally.
            // Reflect this override in the displayed decision so the
            // table accurately shows the FINAL offloading target.
            String displayDecision = decision.actnetDecision;
            String displayTarget   = decision.targetNodeName;

            if (task.getCriticalTask() == 1) {
                Vehicle localVehicle = scheduler.getLocalVehicle(sourceVehicle.getId());
                if (localVehicle != null) {
                    int requiredMips = Math.max(60, task.getNumberOfInstructions() / 8);
                    int requiredRam  = Math.max(4, (int) Math.ceil(task.getTaskSizeMB() * 2));
                    if (localVehicle.getAvailableMips() >= requiredMips
                            && localVehicle.getAvailableRamMB() >= requiredRam) {
                        displayDecision = "local";
                        displayTarget   = sourceVehicle.getId();
                        // Also update the decision object so the assignment
                        // thread uses the same override consistently.
                        decision = new MLOffloadPredictor.OffloadDecision("local", sourceVehicle.getId(), null);
                    }
                }
            }

            // ── RSU load-balanced display ─────────────────────────────
            // When the decision is RSU, preview which RSU the scheduler's
            // fairness-aware algorithm will actually select, so the table
            // shows the real target instead of the raw ML suggestion.
            if ("rsu".equalsIgnoreCase(displayDecision)) {
                displayTarget = scheduler.previewRsuTarget(decision);
            }

            // Print task row with final decision
            ConsoleFormatter.printTaskRowWithDecision(
                    task.getTaskId(),
                    task.getVehicleId(),
                    task.getMobilityStatus().toString(),
                    task.getSignalStrength(),
                    task.getCriticalTask(),
                    task.getBandwidthMbps(),
                    task.getNumberOfInstructions(),
                    task.getTaskSizeMB(),
                    displayDecision,
                    displayTarget
            );

            task.setState(Task.TaskState.QUEUED);

            try {
                createdTaskQueue.put(new TaskWithDecision(task, decision));
                // Simulate inter-arrival time
                int interval = SimConstants.TASK_INTERVAL_MIN_MS
                        + rand.nextInt(SimConstants.TASK_INTERVAL_MAX_MS - SimConstants.TASK_INTERVAL_MIN_MS);
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (headerPrinted) {
            ConsoleFormatter.printTaskTableFooter();
        }
        System.out.println("[CREATION] Thread finished. Total tasks created: " + taskNum);
    }

    private Task generateTask(Random rand, Vehicle sourceVehicle) {
        String taskId = "TASK_" + taskCounter.incrementAndGet();
        String vehicleId = sourceVehicle.getId();

        // Weighted mobility: 50% LOW, 35% MEDIUM, 15% HIGH
        double mobilityRoll = rand.nextDouble();
        Task.MobilityStatus mobility;
        if (mobilityRoll < SimConstants.MOBILITY_LOW_PROB) {
            mobility = Task.MobilityStatus.LOW;
        } else if (mobilityRoll < SimConstants.MOBILITY_MED_PROB) {
            mobility = Task.MobilityStatus.MEDIUM;
        } else {
            mobility = Task.MobilityStatus.HIGH;
        }

        int signalStrength = SimConstants.SIGNAL_MIN_DBM
                + rand.nextInt(SimConstants.SIGNAL_MAX_DBM - SimConstants.SIGNAL_MIN_DBM + 1);
        int critical = rand.nextDouble() < SimConstants.CRITICAL_TASK_PROBABILITY ? 1 : 0;
        double bandwidth = SimConstants.BANDWIDTH_MIN_MBPS
                + rand.nextDouble() * (SimConstants.BANDWIDTH_MAX_MBPS - SimConstants.BANDWIDTH_MIN_MBPS);
        int instructions = SimConstants.INSTRUCTIONS_MIN
                + rand.nextInt(SimConstants.INSTRUCTIONS_MAX - SimConstants.INSTRUCTIONS_MIN);
        double taskSize = SimConstants.TASK_SIZE_MIN_MB
                + rand.nextDouble() * (SimConstants.TASK_SIZE_MAX_MB - SimConstants.TASK_SIZE_MIN_MB);

        return new Task(taskId, vehicleId, mobility, signalStrength,
                critical, bandwidth, instructions, taskSize);
    }

    /**
     * Wrapper to pass both Task and its ML decision through the queue.
     */
    public static class TaskWithDecision {
        public final Task task;
        public final MLOffloadPredictor.OffloadDecision decision;

        public TaskWithDecision(Task task, MLOffloadPredictor.OffloadDecision decision) {
            this.task = task;
            this.decision = decision;
        }
    }
}

