package org.fog.test.VEC.config;


public final class SimConstants {

    private SimConstants() {} // prevent instantiation

    public static final int SIMULATION_DURATION_SEC = 120;
    public static final String ML_PREDICT_URL = "http://127.0.0.1:8000/predict";

    public static final int ML_TIMEOUT_MS = 5000;

    // ═══════════════════════════════════════════════════════════
    //  CLOUD SERVER CAPACITY  (near-infinite, data-center scale)
    // ═══════════════════════════════════════════════════════════
    public static final String CLOUD_ID = "CLOUD_DC";
    public static final int CLOUD_MIPS = 500_000;       // 500,000 MIPS
    public static final int CLOUD_RAM_MB = 512_000;     // 512 GB RAM
    public static final double CLOUD_PROPAGATION_DELAY_MS = 80.0; // WAN latency

    // ═══════════════════════════════════════════════════════════
    //  RSU SERVER CAPACITY  (high-capacity edge nodes)
    // ═══════════════════════════════════════════════════════════
    /** RSU configurations: {id, mips, ram_mb, bandwidth_mbps, sinr_dB} */
    public static final Object[][] INITIAL_RSU_CONFIGS = {
        { "RSU1", 25_000,  32_000, 100.0, 28.0 },
        { "RSU2", 20_000,  24_000,  80.0, 22.0 },
        { "RSU3", 30_000,  48_000, 150.0, 32.0 },
        { "RSU4", 22_000,  28_000,  90.0, 25.0 },
        { "RSU5", 28_000,  40_000, 120.0, 30.0 },
    };

    /** Additional RSUs that join the network dynamically */
    public static final Object[][] DYNAMIC_RSU_POOL = {
        { "RSU6",  18_000,  20_000,  70.0, 20.0 },
        { "RSU7",  35_000,  64_000, 200.0, 35.0 },
        { "RSU8",  24_000,  32_000, 110.0, 26.0 },
        { "RSU9",  26_000,  36_000, 130.0, 29.0 },
        { "RSU10", 20_000,  24_000,  85.0, 23.0 },
    };

    public static final double RSU_PROPAGATION_DELAY_MS = 10.0;

    /** Interval (seconds) between dynamic RSU topology changes */
    public static final int RSU_TOPOLOGY_CHANGE_INTERVAL_SEC = 10;

    /** Min / Max RSUs active at any time */
    public static final int RSU_MIN_ACTIVE = 3;
    public static final int RSU_MAX_ACTIVE = 8;

    public static final int RSU_MAX_CONSECUTIVE = 2;

    // ═══════════════════════════════════════════════════════════
    //  VEHICLE (LOCAL OBU) CAPACITY
    //  Boosted so local vehicles can absorb a significant share
    //  of tasks without immediately falling back to cloud.
    // ═══════════════════════════════════════════════════════════
    public static final int VEHICLE_MIPS = 15_000;      // 15,000 MIPS per vehicle
    public static final int VEHICLE_RAM_MB = 16_000;    // 16 GB RAM per vehicle
    public static final double VEHICLE_PROPAGATION_DELAY_MS = 1.0;

    public static final int NUM_VEHICLES = 6;

    public static final String VEHICLE_ID_PREFIX = "VEH";

    // ═══════════════════════════════════════════════════════════
    //  TASK GENERATION PARAMETERS
    // ═══════════════════════════════════════════════════════════

    public static final int SIGNAL_MIN_DBM = -80;
    public static final int SIGNAL_MAX_DBM = -30;

    public static final double CRITICAL_TASK_PROBABILITY = 0.10;

    public static final double BANDWIDTH_MIN_MBPS = 20.0;
    public static final double BANDWIDTH_MAX_MBPS = 100.0;

    public static final int INSTRUCTIONS_MIN = 300;
    public static final int INSTRUCTIONS_MAX = 5_000;

    public static final double TASK_SIZE_MIN_MB = 1.0;
    public static final double TASK_SIZE_MAX_MB = 15.0;

    public static final int TASK_INTERVAL_MIN_MS = 800;
    public static final int TASK_INTERVAL_MAX_MS = 2500;

    public static final double MOBILITY_LOW_PROB  = 0.50;
    public static final double MOBILITY_MED_PROB  = 0.85; // cumulative (LOW + MED)

    // ═══════════════════════════════════════════════════════════
    //  HEALTH SCORE WEIGHTS
    // ═══════════════════════════════════════════════════════════
    /** CPU weight in composite health score H(n) = 1 - (α·U_cpu + β·U_ram) */
    public static final double HEALTH_ALPHA_CPU = 0.6;
    public static final double HEALTH_BETA_RAM = 0.4;

    // ═══════════════════════════════════════════════════════════
    //  THREAD GRACE PERIODS (ms)
    // ═══════════════════════════════════════════════════════════
    /** Extra time for assignment thread after simulation window */
    public static final int ASSIGNMENT_GRACE_MS = 5000;
    /** Extra time for release thread after simulation window */
    public static final int RELEASE_GRACE_MS = 10000;
    /** Release thread safety exit margin (ms) */
    public static final int RELEASE_SAFETY_EXIT_MS = 15000;
    /** Release thread polling interval (ms) */
    public static final int RELEASE_POLL_INTERVAL_MS = 100;

    // ═══════════════════════════════════════════════════════════
    //  TASK EXECUTION HOLD TIME PARAMETERS
    //  Each task holds its allocated resources for a computed duration:
    //    holdTime = (T_exec + T_transfer + T_prop) * EXEC_TIME_SCALE_FACTOR
    //              + EXEC_TIME_BASE_HOLD_MS
    //
    // ═══════════════════════════════════════════════════════════
    /**
     * Scale factor applied to computed simulated execution time.
     * T_hold_scaled = T_sim * EXEC_TIME_SCALE_FACTOR
     * A value of 1.0 means real-time; higher values make tasks hold longer.
     */
    public static final double EXEC_TIME_SCALE_FACTOR = 3.5;

    /**
     * Minimum base hold time (ms) every task holds resources for,
     * regardless of how small the computed execution time is.
     */
    public static final long EXEC_TIME_BASE_HOLD_MS = 1500;

    // ═══════════════════════════════════════════════════════════
    //  FEDERATED LEARNING PARAMETERS
    //
    //  After each task completes, the originating vehicle sends a
    //  local training update to /local_update.  Once FEDERATED_ROUND_SIZE
    //  updates have been collected, the simulator triggers /aggregate
    //  to perform Federated Averaging (FedAvg) on the global model.
    //
    //  Reference: McMahan et al., "Communication-Efficient Learning
    //             of Deep Networks from Decentralized Data", AISTATS 2017
    //
    //  w_global = (1/K) Σ w_k   (FedAvg)
    // ═══════════════════════════════════════════════════════════

    public static final boolean FL_ENABLED = false;

    /** Base URL for the ML/FL server */
    public static final String FL_BASE_URL = "http://127.0.0.1:8000";

    /** POST /local_update endpoint */
    public static final String FL_LOCAL_UPDATE_URL = FL_BASE_URL + "/local_update";

    /** POST /aggregate endpoint */
    public static final String FL_AGGREGATE_URL = FL_BASE_URL + "/aggregate";

    /** Number of local updates to collect before triggering aggregation (one FL round) */
    public static final int FEDERATED_ROUND_SIZE = 5;

    /** HTTP timeout for FL calls (ms) */
    public static final int FL_TIMEOUT_MS = 8000;

    /** Grace period for the FL thread after simulation ends (ms) */
    public static final int FL_GRACE_MS = 12000;
}

