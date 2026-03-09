package org.fog.test.VEC.infrastructure;

import org.fog.test.VEC.config.SimConstants;
import org.fog.test.VEC.utils.ConsoleFormatter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages infrastructure lifecycle including dynamic RSU topology changes.
 *
 * Simulates real-world scenarios where RSUs come online/offline as vehicles
 * move through different road segments. At configurable intervals the active
 * RSU set is mutated (some removed, some added from the dynamic pool).
 *
 * Thread-safe: uses CopyOnWriteArrayList for the active RSU list so readers
 * (task creation, scheduler) never see a corrupted view.
 */
public class InfrastructureManager {

    private final CloudServer cloud;
    private final CopyOnWriteArrayList<RSUServer> activeRsus;
    private final List<Vehicle> vehicles;

    /** Pool of RSUs that can be activated dynamically */
    private final List<RSUServer> rsuPool;

    /** All RSU instances ever created (for lookup) */
    private final Map<String, RSUServer> allRsus;

    private final Random rand = new Random();
    private int topologyChangeCount = 0;

    public InfrastructureManager() {
        // ── Cloud ──
        this.cloud = new CloudServer(
                SimConstants.CLOUD_ID,
                SimConstants.CLOUD_MIPS,
                SimConstants.CLOUD_RAM_MB
        );

        // ── Initial RSUs ──
        this.activeRsus = new CopyOnWriteArrayList<>();
        this.allRsus = new LinkedHashMap<>();
        for (Object[] cfg : SimConstants.INITIAL_RSU_CONFIGS) {
            RSUServer rsu = createRsu(cfg);
            activeRsus.add(rsu);
            allRsus.put(rsu.getId(), rsu);
        }

        // ── Dynamic RSU Pool ──
        this.rsuPool = new ArrayList<>();
        for (Object[] cfg : SimConstants.DYNAMIC_RSU_POOL) {
            RSUServer rsu = createRsu(cfg);
            rsuPool.add(rsu);
            allRsus.put(rsu.getId(), rsu);
        }

        // ── Vehicles ──
        this.vehicles = new ArrayList<>();
        for (int i = 1; i <= SimConstants.NUM_VEHICLES; i++) {
            String vehId = SimConstants.VEHICLE_ID_PREFIX + "_" + i;
            vehicles.add(new Vehicle(vehId, SimConstants.VEHICLE_MIPS, SimConstants.VEHICLE_RAM_MB));
        }
    }

    private RSUServer createRsu(Object[] cfg) {
        return new RSUServer(
                (String) cfg[0],
                (int) cfg[1],
                (int) cfg[2],
                (double) cfg[3],
                (double) cfg[4]
        );
    }

    // ─── Getters ───────────────────────────────────────────

    public CloudServer getCloud() { return cloud; }

    /** Returns a live, thread-safe view of active RSUs */
    public List<RSUServer> getActiveRsus() { return activeRsus; }

    public List<Vehicle> getVehicles() { return vehicles; }

    /** Pick a random vehicle (task originator) */
    public Vehicle getRandomVehicle() {
        return vehicles.get(rand.nextInt(vehicles.size()));
    }

    /** Get the "local" vehicle for a specific task — round-robin or random */
    public Vehicle getVehicleById(String id) {
        for (Vehicle v : vehicles) {
            if (v.getId().equals(id)) return v;
        }
        return vehicles.get(0);
    }

    // ─── Dynamic RSU Topology ──────────────────────────────

    /**
     * Mutate the active RSU set to simulate vehicular mobility across
     * different road segments. Called periodically by the simulation.
     *
     * Strategy:
     *   1. Randomly remove 1-2 RSUs (if above minimum)
     *   2. Randomly add 1-3 RSUs from the pool (if below maximum)
     *   3. Only remove RSUs with zero active tasks
     */
    public synchronized void changeRsuTopology() {
        topologyChangeCount++;
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();

        // ── Remove some RSUs (only those with 0 active tasks) ──
        if (activeRsus.size() > SimConstants.RSU_MIN_ACTIVE) {
            int removeCount = 1 + rand.nextInt(2); // remove 1-2
            List<RSUServer> candidates = new ArrayList<>();
            for (RSUServer rsu : activeRsus) {
                if (rsu.getActiveTasks() == 0) {
                    candidates.add(rsu);
                }
            }
            Collections.shuffle(candidates, rand);
            for (int i = 0; i < Math.min(removeCount, candidates.size()); i++) {
                if (activeRsus.size() <= SimConstants.RSU_MIN_ACTIVE) break;
                RSUServer toRemove = candidates.get(i);
                activeRsus.remove(toRemove);
                if (!rsuPool.contains(toRemove)) {
                    rsuPool.add(toRemove);
                }
                removed.add(toRemove.getId());
            }
        }

        // ── Add some RSUs from pool ──
        if (activeRsus.size() < SimConstants.RSU_MAX_ACTIVE && !rsuPool.isEmpty()) {
            int addCount = 1 + rand.nextInt(3); // add 1-3
            Collections.shuffle(rsuPool, rand);
            Iterator<RSUServer> it = rsuPool.iterator();
            int addedSoFar = 0;
            while (it.hasNext() && addedSoFar < addCount
                    && activeRsus.size() < SimConstants.RSU_MAX_ACTIVE) {
                RSUServer toAdd = it.next();
                // Check it's not already active
                boolean alreadyActive = false;
                for (RSUServer r : activeRsus) {
                    if (r.getId().equals(toAdd.getId())) { alreadyActive = true; break; }
                }
                if (!alreadyActive) {
                    activeRsus.add(toAdd);
                    it.remove();
                    added.add(toAdd.getId());
                    addedSoFar++;
                }
            }
        }

        // ── Log the change ──
        System.out.println();
        ConsoleFormatter.printHeader("RSU TOPOLOGY CHANGE #" + topologyChangeCount);
        if (!removed.isEmpty()) {
            System.out.println("  [TOPO] RSUs REMOVED : " + String.join(", ", removed));
        }
        if (!added.isEmpty()) {
            System.out.println("  [TOPO] RSUs ADDED   : " + String.join(", ", added));
        }
        System.out.println("  [TOPO] Active RSUs  : " + activeRsus.size());
        StringBuilder sb = new StringBuilder("  [TOPO] RSU IDs      : ");
        for (int i = 0; i < activeRsus.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(activeRsus.get(i).getId());
        }
        System.out.println(sb.toString());
    }

    // ─── Tabular Infrastructure Display ────────────────────

    /**
     * Print a clean tabular view of the entire infrastructure setup.
     */
    public void printInfrastructureSetupTable() {
        ConsoleFormatter.printHeader("INFRASTRUCTURE SETUP");

        // Cloud Section
        System.out.println();
        System.out.printf("  %-20s %s%n", "Component", "Details");
        System.out.println("  " + "─".repeat(70));
        System.out.printf("  %-20s %-12s │ MIPS: %-10s │ RAM: %-10s │ Latency: %.0f ms%n",
                "☁  Cloud Server", cloud.getId(),
                formatNumber(cloud.getTotalMips()),
                formatMemory(cloud.getTotalRamMB()),
                cloud.getPropagationDelayMs());

        // RSU Section
        System.out.println();
        ConsoleFormatter.printRsuSetupTable(activeRsus);

        // Vehicles Section
        System.out.println();
        ConsoleFormatter.printVehicleSetupTable(vehicles);

        // Summary
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.printf("  │  Total: 1 Cloud │ %d RSUs │ %d Vehicles %s│%n",
                activeRsus.size(), vehicles.size(),
                " ".repeat(Math.max(0, 25 - String.valueOf(activeRsus.size()).length()
                        - String.valueOf(vehicles.size()).length())));
        System.out.printf("  │  Dynamic RSU Pool: %d additional RSUs available %s│%n",
                rsuPool.size(),
                " ".repeat(Math.max(0, 19 - String.valueOf(rsuPool.size()).length())));
        System.out.printf("  │  Topology changes every %d seconds %s│%n",
                SimConstants.RSU_TOPOLOGY_CHANGE_INTERVAL_SEC,
                " ".repeat(Math.max(0, 24 - String.valueOf(SimConstants.RSU_TOPOLOGY_CHANGE_INTERVAL_SEC).length())));
        System.out.println("  └─────────────────────────────────────────────────────────┘");
    }

    private String formatNumber(int num) {
        if (num >= 1_000_000) return String.format("%,.1fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%,dK", num / 1000);
        return String.valueOf(num);
    }

    private String formatMemory(int mb) {
        if (mb >= 1024) return String.format("%.0f GB", mb / 1024.0);
        return mb + " MB";
    }
}

