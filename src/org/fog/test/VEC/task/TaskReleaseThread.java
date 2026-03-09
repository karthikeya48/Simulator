package org.fog.test.VEC.task;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.scheduler.MLScheduler;

import java.util.concurrent.*;
import java.util.*;

/**
 * Thread 3: Task Release
 *
 * Monitors running tasks. After their simulated execution time elapses,
 * releases allocated resources from the compute node and pushes the
 * completed task to the federated learning queue.
 *
 * Uses manual time diff to track execution:
 *   elapsed = currentTime - assignmentTime
 *   if elapsed >= simulatedExecTime → release resources & mark completed
 *
 * Metrics reported per task:
 *   - Simulated execution time (computed from T_exec + T_trans + T_prop)
 *   - Wall-clock time (actual elapsed time)
 *   - Total latency (creation → completion)
 */
public class TaskReleaseThread implements Runnable {

    private final BlockingQueue<Task> runningTaskQueue;
    private final BlockingQueue<Task> federatedQueue;
    private final MLScheduler scheduler;
    private final int durationSec;

    public TaskReleaseThread(BlockingQueue<Task> runningTaskQueue,
                             BlockingQueue<Task> federatedQueue,
                             MLScheduler scheduler, int durationSec) {
        this.runningTaskQueue = runningTaskQueue;
        this.federatedQueue = federatedQueue;
        this.scheduler = scheduler;
        this.durationSec = durationSec;
    }

    @Override
    public void run() {
        System.out.println("[RELEASE] Thread started.");
        long endTime = System.currentTimeMillis() + (durationSec * 1000L) + SimConstants.RELEASE_GRACE_MS;

        // Internal list of tasks waiting for completion
        List<Task> waitingTasks = new ArrayList<>();

        while (System.currentTimeMillis() < endTime || !waitingTasks.isEmpty()) {
            // Drain new running tasks from queue
            Task newTask;
            while ((newTask = runningTaskQueue.poll()) != null) {
                waitingTasks.add(newTask);
            }

            // Check each task for simulated completion using manual time diff
            Iterator<Task> it = waitingTasks.iterator();
            long now = System.currentTimeMillis();
            while (it.hasNext()) {
                Task t = it.next();
                double elapsedSinceAssignment = now - t.getAssignmentTimeMs();

                if (elapsedSinceAssignment >= t.getSimulatedExecTimeMs()) {
                    // Release resources
                    t.getAssignedNode().release(t.getAllocatedMips(), t.getAllocatedRamMB());
                    t.markCompleted();

                    scheduler.recordCompletion(t);

                    // Push to federated learning queue
                    federatedQueue.offer(t);

                    it.remove();
                }
            }

            try {
                Thread.sleep(SimConstants.RELEASE_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Safety exit
            if (System.currentTimeMillis() > endTime + SimConstants.RELEASE_SAFETY_EXIT_MS) break;
        }
        System.out.println("[RELEASE] Thread finished.");
    }
}

