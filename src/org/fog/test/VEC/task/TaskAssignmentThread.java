package org.fog.test.VEC.task;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.scheduler.MLScheduler;
import org.fog.test.VEC.task.TaskCreationThread.TaskWithDecision;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread 2: Task Assignment
 *
 * Dequeues tasks from createdTaskQueue. The ML decision has already been
 * obtained in the creation thread. This thread uses the MLScheduler to
 * allocate resources on the target node specified by the ML model.
 *
 * Computes simulated execution time using:
 *   T_exec   = (I / F) × 1000   [ms]     (Instructions / Allocated MIPS)
 *   T_trans  = (D × 8 / B) × 1000 [ms]   (Data MB × 8 / Bandwidth Mbps)
 *   T_prop   = node propagation delay     [ms]
 *   T_total  = T_exec + T_trans + T_prop
 */
public class TaskAssignmentThread implements Runnable {

    private final BlockingQueue<TaskWithDecision> createdTaskQueue;
    private final BlockingQueue<Task> runningTaskQueue;
    private final MLScheduler scheduler;
    private final int durationSec;

    public TaskAssignmentThread(BlockingQueue<TaskWithDecision> createdTaskQueue,
                                BlockingQueue<Task> runningTaskQueue,
                                MLScheduler scheduler, int durationSec) {
        this.createdTaskQueue = createdTaskQueue;
        this.runningTaskQueue = runningTaskQueue;
        this.scheduler = scheduler;
        this.durationSec = durationSec;
    }

    @Override
    public void run() {
        System.out.println("[ASSIGN] Thread started.");
        long endTime = System.currentTimeMillis() + (durationSec * 1000L) + SimConstants.ASSIGNMENT_GRACE_MS;

        while (System.currentTimeMillis() < endTime) {
            try {
                TaskWithDecision twd = createdTaskQueue.poll(2, TimeUnit.SECONDS);
                if (twd == null) continue;

                Task task = twd.task;
                MLOffloadPredictor.OffloadDecision decision = twd.decision;

                boolean assigned = scheduler.assignTask(task, decision);
                if (assigned) {
                    // Compute simulated execution time
                    double execTime = task.computeSimulatedExecTimeMs();
                    task.setSimulatedExecTimeMs(execTime);

                    runningTaskQueue.put(task);
                } else {
                    task.setState(Task.TaskState.FAILED);
                    scheduler.recordFailure();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[ASSIGN] Thread finished.");
    }
}

