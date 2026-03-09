package org.fog.test.VEC;

import org.fog.test.VEC.infrastructure.*;
import org.fog.test.VEC.task.*;
import org.fog.test.VEC.task.TaskCreationThread.TaskWithDecision;
import org.fog.test.VEC.scheduler.*;
import org.fog.test.VEC.utils.SimulationLogger;

import java.util.*;
import java.util.concurrent.*;

/**
 * VEC Simulator Entry Point
 *
 * Launches three concurrent threads:
 *   1. TaskCreationThread  – generates tasks, queries ML endpoint for offload decision
 *   2. TaskAssignmentThread – allocates resources on the ML-chosen target node
 *   3. TaskReleaseThread    – monitors completion and releases resources
 *
 * Infrastructure (fixed):
 *   - 1 Cloud Server  : 10000 MIPS, 16000 MB RAM
 *   - 3 RSU Servers   : RSU1 (2500/4000/50Mbps/25dB SINR)
 *                        RSU2 (1800/2000/30Mbps/15dB SINR)
 *                        RSU3 (3000/8000/100Mbps/30dB SINR)
 *   - 1 Vehicle (local): 100 MIPS, 1000 MB RAM
 *
 * ML Endpoint: http://127.0.0.1:8000/predict
 */
public class StartSimulation {

    private static final int SIMULATION_DURATION_SEC = 15;

    public static void main(String[] args) {

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       Vehicular Edge Computing (VEC) Simulator v2.0           ║");
        System.out.println("║       ML-Driven Offloading Decision System                    ║");
        System.out.println("║       Endpoint: http://127.0.0.1:8000/predict                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ──────────────────────────────────────────────
        //  1. Create Infrastructure
        // ──────────────────────────────────────────────
        System.out.println("=============================================");
        System.out.println("  INFRASTRUCTURE SETUP");
        System.out.println("=============================================");

        // Cloud: 10000 MIPS, 16 GB RAM
        CloudServer cloud = new CloudServer("CLOUD_1", 10000, 16000);
        System.out.println("[INFRA] Created: " + cloud);

        // RSUs with bandwidth and SINR
        List<RSUServer> rsus = new ArrayList<>();
        rsus.add(new RSUServer("RSU1", 2500, 4000, 50.0, 25.0));
        rsus.add(new RSUServer("RSU2", 1800, 2000, 30.0, 15.0));
        rsus.add(new RSUServer("RSU3", 3000, 8000, 100.0, 30.0));

        for (RSUServer rsu : rsus) {
            System.out.println("[INFRA] Created: " + rsu);
        }

        // Local Vehicle (task originator)
        Vehicle localVehicle = new Vehicle("VEH_LOCAL", 100, 1000);
        System.out.println("[INFRA] Created: " + localVehicle);

        System.out.println();
        System.out.println("[INFO] Simulation duration: " + SIMULATION_DURATION_SEC + " seconds");
        System.out.println("[INFO] ML Endpoint: http://127.0.0.1:8000/predict");
        System.out.println();

        // ──────────────────────────────────────────────
        //  2. Shared concurrent queues for task pipeline
        // ──────────────────────────────────────────────
        BlockingQueue<TaskWithDecision> createdTaskQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Task> runningTaskQueue = new LinkedBlockingQueue<>();

        // ──────────────────────────────────────────────
        //  3. Build ML Scheduler
        // ──────────────────────────────────────────────
        MLScheduler scheduler = new MLScheduler(cloud, rsus, localVehicle);

        // Initialize simulation logger
        SimulationLogger logger = SimulationLogger.getInstance();
        logger.initialize(cloud, rsus, localVehicle, SIMULATION_DURATION_SEC);
        logger.logEvent("Simulation started");
        logger.logEvent("Infrastructure initialized: 1 Cloud, " + rsus.size() + " RSUs, 1 Vehicle");

        // Print initial infrastructure health
        scheduler.printInfrastructureHealth();
        logger.captureInfrastructureSnapshot("Initial State");

        // ──────────────────────────────────────────────
        //  4. Launch threads
        // ──────────────────────────────────────────────
        System.out.println("\n=============================================");
        System.out.println("  STARTING SIMULATION THREADS");
        System.out.println("=============================================\n");

        Thread creationThread = new Thread(
                new TaskCreationThread(scheduler, createdTaskQueue, SIMULATION_DURATION_SEC),
                "TaskCreation-Thread");

        Thread assignmentThread = new Thread(
                new TaskAssignmentThread(createdTaskQueue, runningTaskQueue, scheduler, SIMULATION_DURATION_SEC),
                "TaskAssignment-Thread");

        Thread releaseThread = new Thread(
                new TaskReleaseThread(runningTaskQueue, scheduler, SIMULATION_DURATION_SEC),
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

