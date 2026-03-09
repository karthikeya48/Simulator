package org.fog.test.VEC.task;

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
 * Task parameters generated:
 *   - mobility_status (LOW, MEDIUM, HIGH)
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
        long endTime = System.currentTimeMillis() + (durationSec * 1000L);
        boolean headerPrinted = false;
        int taskNum = 0;

        while (System.currentTimeMillis() < endTime) {
            taskNum++;

            // Generate task
            Task task = generateTask(rand);

            // Print table header once
            if (!headerPrinted) {
                ConsoleFormatter.printHeader("TASK GENERATION & ML OFFLOADING");
                ConsoleFormatter.printTaskTableHeader();
                headerPrinted = true;
            }

            // Print infrastructure health every 5 tasks (not on the first)
            if (taskNum > 1 && taskNum % 5 == 1) {
                ConsoleFormatter.printTaskTableFooter();
                scheduler.printInfrastructureHealth();
                ConsoleFormatter.printTaskTableHeader();
            }

            // Call ML predictor with infrastructure state
            MLOffloadPredictor.OffloadDecision decision = MLOffloadPredictor.predict(
                    task,
                    scheduler.getLocalVehicle(),
                    scheduler.getCloud(),
                    scheduler.getRsus()
            );

            // Print task row with ML decision
            ConsoleFormatter.printTaskRowWithDecision(
                    task.getTaskId(),
                    task.getVehicleId(),
                    task.getMobilityStatus().toString(),
                    task.getSignalStrength(),
                    task.getCriticalTask(),
                    task.getBandwidthMbps(),
                    task.getNumberOfInstructions(),
                    task.getTaskSizeMB(),
                    decision.actnetDecision,
                    decision.targetNodeName
            );

            task.setState(Task.TaskState.QUEUED);

            try {
                createdTaskQueue.put(new TaskWithDecision(task, decision));
                // Simulate inter-arrival time: Poisson-like with mean ~2 seconds
                Thread.sleep(1000 + rand.nextInt(2000));
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

    private Task generateTask(Random rand) {
        String taskId = "TASK_" + taskCounter.incrementAndGet();
        String vehicleId = scheduler.getLocalVehicle().getId();

        Task.MobilityStatus[] statuses = Task.MobilityStatus.values();
        Task.MobilityStatus mobility = statuses[rand.nextInt(statuses.length)];

        int signalStrength = -(30 + rand.nextInt(61));          // -30 to -90 dBm
        int critical = rand.nextDouble() < 0.2 ? 1 : 0;        // 20% critical
        double bandwidth = 5.0 + rand.nextDouble() * 45.0;      // 5 - 50 Mbps
        int instructions = 1000 + rand.nextInt(9000);            // 1000 - 10000 MI
        double taskSize = 1.0 + rand.nextDouble() * 49.0;       // 1 - 50 MB

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

