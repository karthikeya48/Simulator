package org.fog.test.VEC;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.task.*;
import org.fog.test.VEC.task.TaskCreationThread.TaskWithDecision;
import org.fog.test.VEC.scheduler.*;

import java.util.*;
import java.util.concurrent.*;

public class StartSimulation {

    public static void main(String[] args) {

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       Vehicular Edge Computing (VEC) Simulator v3.0           ║");
        System.out.println("║       ML-Driven Offloading + Federated Learning               ║");
        System.out.println("║       Endpoint: " + SimConstants.ML_PREDICT_URL + "                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ──────────────────────────────────────────────
        //  1. Create Infrastructure via Manager
        // ──────────────────────────────────────────────
        InfrastructureManager infraManager = new InfrastructureManager();

        // Print clean tabular infrastructure setup view
        infraManager.printInfrastructureSetupTable();

        System.out.println();
        System.out.println("  [INFO] Simulation duration : " + SimConstants.SIMULATION_DURATION_SEC + " seconds");
        System.out.println("  [INFO] ML Endpoint         : " + SimConstants.ML_PREDICT_URL);
        if (SimConstants.FL_ENABLED) {
            System.out.println("  [INFO] FL Local Update      : " + SimConstants.FL_LOCAL_UPDATE_URL);
            System.out.println("  [INFO] FL Aggregate         : " + SimConstants.FL_AGGREGATE_URL);
            System.out.println("  [INFO] FL Round Size (K)    : " + SimConstants.FEDERATED_ROUND_SIZE);
        } else {
            System.out.println("  [INFO] Federated Learning  : DISABLED");
        }
        System.out.println("  [INFO] Vehicles            : " + SimConstants.NUM_VEHICLES);
        System.out.println("  [INFO] Initial RSUs        : " + infraManager.getActiveRsus().size());
        System.out.println("  [INFO] RSU topology change  : every " + SimConstants.RSU_TOPOLOGY_CHANGE_INTERVAL_SEC + "s");
        System.out.println();

        // ──────────────────────────────────────────────
        //  2. Shared concurrent queues for task pipeline
        // ──────────────────────────────────────────────
        BlockingQueue<TaskWithDecision> createdTaskQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Task> runningTaskQueue  = new LinkedBlockingQueue<>();
        // Federated queue is only needed when FL is enabled
        BlockingQueue<Task> federatedQueue    = SimConstants.FL_ENABLED
                ? new LinkedBlockingQueue<>() : null;

        // ──────────────────────────────────────────────
        //  3. Build ML Scheduler
        // ──────────────────────────────────────────────
        MLScheduler scheduler = new MLScheduler(infraManager);

        // Print initial infrastructure health
        scheduler.printInfrastructureHealth();

        // ──────────────────────────────────────────────
        //  4. Launch threads (3 original + 1 FL thread)
        // ──────────────────────────────────────────────
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                 STARTING SIMULATION THREADS                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        Thread creationThread = new Thread(
                new TaskCreationThread(scheduler, createdTaskQueue, SimConstants.SIMULATION_DURATION_SEC),
                "TaskCreation-Thread");

        Thread assignmentThread = new Thread(
                new TaskAssignmentThread(createdTaskQueue, runningTaskQueue, scheduler, SimConstants.SIMULATION_DURATION_SEC),
                "TaskAssignment-Thread");

        Thread releaseThread = new Thread(
                new TaskReleaseThread(runningTaskQueue, federatedQueue, scheduler, SimConstants.SIMULATION_DURATION_SEC),
                "TaskRelease-Thread");

        Thread flThread = null;

        if (SimConstants.FL_ENABLED) {
            flThread = new Thread(
                    new FederatedLearningThread(federatedQueue, SimConstants.SIMULATION_DURATION_SEC),
                    "FederatedLearning-Thread");
        }

        creationThread.start();
        assignmentThread.start();
        releaseThread.start();
        if (flThread != null) {
            flThread.start();
        }

        try {
            creationThread.join();
            assignmentThread.join();
            releaseThread.join();
            if (flThread != null) {
                flThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // ──────────────────────────────────────────────
        //  5. Print final summary
        // ──────────────────────────────────────────────
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SIMULATION COMPLETE                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        scheduler.printStatistics();
    }
}

