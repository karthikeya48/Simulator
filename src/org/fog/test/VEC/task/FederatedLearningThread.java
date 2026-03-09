package org.fog.test.VEC.task;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.utils.ConsoleFormatter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread 4: Federated Learning
 *
 * Consumes completed tasks from the federated queue and drives the
 * Federated Learning loop:
 *
 *   for each completed task:
 *       1. POST /local_update  — vehicle sends task outcome to the server,
 *          which clones the global ACTNet model, performs one SGD step on
 *          the (task features, actual_target) pair, and stores the updated
 *          local weights.
 *
 *   every FEDERATED_ROUND_SIZE tasks:
 *       2. POST /aggregate — triggers Federated Averaging (FedAvg):
 *
 *              w_global = (1/K) Σ_k w_k
 *
 *          The server replaces the global model with the averaged weights
 *          and clears the buffer for the next round.
 *
 * This is a non-blocking, best-effort thread. If the FL server is
 * unreachable, the simulation continues normally — predictions fall
 * back to the existing global model.
 *
 * Reference:
 *   McMahan et al., "Communication-Efficient Learning of Deep Networks
 *   from Decentralized Data", AISTATS 2017.
 */
public class FederatedLearningThread implements Runnable {

    private final BlockingQueue<Task> federatedQueue;
    private final int durationSec;

    // ── Per-round counters ──
    private final AtomicInteger totalUpdates = new AtomicInteger(0);
    private final AtomicInteger totalRounds  = new AtomicInteger(0);
    private final AtomicInteger failedUpdates = new AtomicInteger(0);
    private int updatesInCurrentRound = 0;
    private double cumulativeLoss = 0.0;

    public FederatedLearningThread(BlockingQueue<Task> federatedQueue, int durationSec) {
        this.federatedQueue = federatedQueue;
        this.durationSec = durationSec;
    }

    @Override
    public void run() {
        System.out.println("[FL] Federated Learning thread started.");
        long endTime = System.currentTimeMillis() + (durationSec * 1000L) + SimConstants.FL_GRACE_MS;

        while (System.currentTimeMillis() < endTime) {
            try {
                Task task = federatedQueue.poll(2, TimeUnit.SECONDS);
                if (task == null) continue;

                // Determine the actual target that was used
                String actualTarget = resolveActualTarget(task);

                // ── Step 1: Send local update ──
                double loss = FederatedClient.sendLocalUpdate(task, actualTarget);

                if (loss >= 0) {
                    totalUpdates.incrementAndGet();
                    updatesInCurrentRound++;
                    cumulativeLoss += loss;
                } else {
                    failedUpdates.incrementAndGet();
                }

                // ── Step 2: Trigger aggregation when round is full ──
                if (updatesInCurrentRound >= SimConstants.FEDERATED_ROUND_SIZE) {
                    String result = FederatedClient.triggerAggregation();
                    int roundNum = totalRounds.incrementAndGet();
                    double avgLoss = updatesInCurrentRound > 0
                            ? cumulativeLoss / updatesInCurrentRound : 0.0;

                    ConsoleFormatter.printFederatedRoundSummary(
                            roundNum,
                            updatesInCurrentRound,
                            avgLoss,
                            result != null
                    );

                    // Reset for next round
                    updatesInCurrentRound = 0;
                    cumulativeLoss = 0.0;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // ── Final partial round (if any updates remain) ──
        if (updatesInCurrentRound > 0) {
            String result = FederatedClient.triggerAggregation();
            int roundNum = totalRounds.incrementAndGet();
            double avgLoss = cumulativeLoss / updatesInCurrentRound;
            ConsoleFormatter.printFederatedRoundSummary(
                    roundNum, updatesInCurrentRound, avgLoss, result != null);
        }

        // ── Print FL summary ──
        ConsoleFormatter.printFederatedLearningSummary(
                totalRounds.get(),
                totalUpdates.get(),
                failedUpdates.get(),
                SimConstants.FEDERATED_ROUND_SIZE
        );

        System.out.println("[FL] Federated Learning thread finished.");
    }

    /**
     * Determine the actual offload target for a completed task.
     * Maps node types to the labels expected by the ML server.
     */
    private String resolveActualTarget(Task task) {
        if (task.getAssignedNode() == null) {
            return task.getOffloadTarget() != null ? task.getOffloadTarget() : "local";
        }
        switch (task.getAssignedNode().getNodeType()) {
            case "CLOUD":   return "cloud";
            case "RSU":     return "rsu";
            case "VEHICLE": return "local";
            default:        return "local";
        }
    }
}

