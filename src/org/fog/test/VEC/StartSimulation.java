package org.fog.test.VEC;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.task.*;
import org.fog.test.VEC.task.TaskCreationThread.TaskWithDecision;
import org.fog.test.VEC.scheduler.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * VEC Simulator Entry Point
 *
 * Launches three concurrent threads:
 *   1. TaskCreationThread  – generates tasks from multiple vehicles,
 *                            queries ML endpoint for offload decision,
 *                            triggers dynamic RSU topology changes
 *   2. TaskAssignmentThread – allocates resources on the ML-chosen target node
 *   3. TaskReleaseThread    – monitors completion and releases resources
 *
 * Infrastructure (configurable via SimConstants):
 *   - 1 Cloud Server   : near-infinite capacity (500K MIPS, 512 GB RAM)
 *   - N RSU Servers     : high-capacity edge nodes, dynamically added/removed
 *   - M Vehicles (local): each with its own OBU compute resources
 *
 * ML Endpoint: http://127.0.0.1:8000/predict
 */
public class StartSimulation {

    public static void main(String[] args) {

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       Vehicular Edge Computing (VEC) Simulator v3.0           ║");
        System.out.println("║       ML-Driven Offloading Decision System                    ║");
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
        System.out.println("  [INFO] Vehicles            : " + SimConstants.NUM_VEHICLES);
        System.out.println("  [INFO] Initial RSUs        : " + infraManager.getActiveRsus().size());
        System.out.println("  [INFO] RSU topology change  : every " + SimConstants.RSU_TOPOLOGY_CHANGE_INTERVAL_SEC + "s");
        System.out.println();

        // ──────────────────────────────────────────────
        //  2. Shared concurrent queues for task pipeline
        // ──────────────────────────────────────────────
        BlockingQueue<TaskWithDecision> createdTaskQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Task> runningTaskQueue = new LinkedBlockingQueue<>();

        // ──────────────────────────────────────────────
        //  3. Build ML Scheduler
        // ──────────────────────────────────────────────
        MLScheduler scheduler = new MLScheduler(infraManager);

        // Print initial infrastructure health
        scheduler.printInfrastructureHealth();

        // ──────────────────────────────────────────────
        //  4. Launch threads
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
                new TaskReleaseThread(runningTaskQueue, scheduler, SimConstants.SIMULATION_DURATION_SEC),
                "TaskRelease-Thread");

        creationThread.start();
        assignmentThread.start();
        releaseThread.start();

        try {
            creationThread.join();
            assignmentThread.join();
            releaseThread.join();
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

