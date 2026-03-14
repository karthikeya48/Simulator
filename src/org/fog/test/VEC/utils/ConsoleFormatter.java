package org.fog.test.VEC.utils;

import org.fog.test.VEC.infrastructure.RSUServer;
import org.fog.test.VEC.infrastructure.Vehicle;

import java.util.List;

/**
 * Utility class for formatted console output.
 * Provides tabular display for tasks, infrastructure health, and statistics.
 */
public class ConsoleFormatter {

    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String BLUE   = "\u001B[34m";
    private static final String WHITE  = "\u001B[37m";
    private static final String BOLD   = "\u001B[1m";
    private static final String MAGENTA = "\u001B[35m";

    // ─────────────────────────────────────────────────────────
    //  HEADER
    // ─────────────────────────────────────────────────────────
    public static void printHeader(String title) {
        System.out.println();
        System.out.println(CYAN + "╔" + "═".repeat(120) + "╗" + RESET);
        System.out.println(CYAN + "║  " + BOLD + padRight(title, 116) + RESET + CYAN + "  ║" + RESET);
        System.out.println(CYAN + "╚" + "═".repeat(120) + "╝" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  TASK TABLE (with ML Decision)
    // ─────────────────────────────────────────────────────────
    public static void printTaskTableHeader() {
        System.out.println();
        System.out.println(BLUE + "┌──────────┬────────────┬───────┬──────────┬──────────┬───────────┬──────────┬──────────┬──────────┬──────────────┐" + RESET);
        System.out.printf(BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-10s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-5s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-9s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + RESET + "%n",
                "Task ID", "Vehicle", "Mob.", "Signal", "Critical", "BW (Mbps)", "MI", "Size MB", "Decision", "Target Node");
        System.out.println(BLUE + "├──────────┼────────────┼───────┼──────────┼──────────┼───────────┼──────────┼──────────┼──────────┼──────────────┤" + RESET);
    }

    public static void printTaskRowWithDecision(String taskId, String vehicleId, String mobility,
                                                int signal, int critical, double bandwidth,
                                                int instructions, double taskSize,
                                                String decision, String targetNode) {
        String critStr = critical == 1 ? RED + "YES" + RESET : GREEN + "NO " + RESET;
        String mobStr = mobility.substring(0, 3);
        String decColor = getDecisionColor(decision);
        String targetStr = targetNode != null ? targetNode : "-";

        System.out.printf(BLUE + "│" + RESET + " %-8s " +
                        BLUE + "│" + RESET + " %-10s " +
                        BLUE + "│" + RESET + " %-5s " +
                        BLUE + "│" + RESET + " %4d dBm " +
                        BLUE + "│" + RESET + " %s      " +
                        BLUE + "│" + RESET + " %7.1f   " +
                        BLUE + "│" + RESET + " %6d   " +
                        BLUE + "│" + RESET + " %6.2f   " +
                        BLUE + "│" + RESET + " %s%-8s" + RESET + " " +
                        BLUE + "│" + RESET + " %-12s " +
                        BLUE + "│" + RESET + "%n",
                taskId, vehicleId, mobStr, signal, critStr, bandwidth,
                instructions, taskSize, decColor, decision.toUpperCase(), targetStr);
    }

    /** Legacy method for backward compatibility */
    public static void printTaskRow(String taskId, String vehicleId, String mobility,
                                    int signal, int critical, double bandwidth,
                                    int instructions, double taskSize) {
        printTaskRowWithDecision(taskId, vehicleId, mobility, signal, critical,
                bandwidth, instructions, taskSize, "-", null);
    }

    public static void printTaskTableFooter() {
        System.out.println(BLUE + "└──────────┴────────────┴───────┴──────────┴──────────┴───────────┴──────────┴──────────┴──────────┴──────────────┘" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  INFRASTRUCTURE HEALTH TABLE (Enhanced)
    // ─────────────────────────────────────────────────────────
    public static void printInfrastructureHeader() {
        System.out.println();
        System.out.println(GREEN + "╔" + "═".repeat(130) + "╗" + RESET);
        System.out.println(GREEN + "║  " + BOLD + WHITE + padRight("INFRASTRUCTURE HEALTH STATUS", 126) + RESET + GREEN + "  ║" + RESET);
        System.out.println(GREEN + "╚" + "═".repeat(130) + "╝" + RESET);
        System.out.println();
        System.out.println(GREEN + "┌──────────────────┬──────────┬────────────┬────────────┬────────────┬────────────┬────────────┬──────────────┬──────────┬─────────────┐" + RESET);
        System.out.printf(GREEN + "│" + BOLD + WHITE + " %-16s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-8s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-12s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-8s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-11s " + RESET +
                        GREEN + "│" + RESET + "%n",
                "Node", "Type", "Total MIPS", "Avail MIPS", "Total RAM", "Avail RAM", "CPU Usage", "RAM Usage", "Health", "Status");
        System.out.println(GREEN + "├──────────────────┼──────────┼────────────┼────────────┼────────────┼────────────┼────────────┼──────────────┼──────────┼─────────────┤" + RESET);
    }

    public static void printInfrastructureRow(String nodeId, String nodeType,
                                              int totalMips, int availMips,
                                              int totalRam, int availRam,
                                              double cpuUsage, double ramUsage,
                                              double health, int activeTasks) {
        String healthBar = getHealthBar(health);
        String statusColor = getHealthColor(health);
        String status = getHealthStatus(health);

        String cpuStr = String.format("%.1f%%", cpuUsage * 100);
        String ramStr = String.format("%.1f%%", ramUsage * 100);
        String healthStr = String.format("%.3f", health);

        System.out.printf(GREEN + "│ " + RESET + "%-16s " +
                        GREEN + "│ " + RESET + "%-8s " +
                        GREEN + "│ " + RESET + "%10d " +
                        GREEN + "│ " + RESET + "%10d " +
                        GREEN + "│ " + RESET + "%8d MB " +
                        GREEN + "│ " + RESET + "%8d MB " +
                        GREEN + "│ " + RESET + "%10s " +
                        GREEN + "│ " + RESET + "%12s " +
                        GREEN + "│ " + RESET + "%s " +
                        GREEN + "│ " + RESET + "%s%-11s" + RESET + " " +
                        GREEN + "│" + RESET + "%n",
                nodeId, nodeType, totalMips, availMips, totalRam, availRam,
                cpuStr, ramStr, healthBar, statusColor, status);
    }

    /** Legacy overload for backward compat */
    public static void printInfrastructureRow(String nodeId, String nodeType,
                                              double cpuUsage, double ramUsage,
                                              double health) {
        printInfrastructureRow(nodeId, nodeType, 0, 0, 0, 0, cpuUsage, ramUsage, health, 0);
    }

    public static void printInfrastructureFooter() {
        System.out.println(GREEN + "└──────────────────┴──────────┴────────────┴────────────┴────────────┴────────────┴────────────┴──────────────┴──────────┴─────────────┘" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  STATISTICS TABLE
    // ─────────────────────────────────────────────────────────
    public static void printStatisticsTable(int totalAssigned, int totalCompleted,
                                            int totalFailed, double avgLatency,
                                            int cloudCount, int rsuCount, int localCount) {
        System.out.println();
        System.out.println(YELLOW + "╔" + "═".repeat(60) + "╗" + RESET);
        System.out.println(YELLOW + "║  " + BOLD + WHITE + padRight("SIMULATION STATISTICS", 56) + RESET + YELLOW + "  ║" + RESET);
        System.out.println(YELLOW + "╚" + "═".repeat(60) + "╝" + RESET);
        System.out.println();
        System.out.println(YELLOW + "┌────────────────────────────┬─────────────────────────────┐" + RESET);
        System.out.printf(YELLOW + "│ " + BOLD + WHITE + "%-26s" + RESET + YELLOW + " │ " + BOLD + WHITE + "%-27s" + RESET + YELLOW + " │" + RESET + "%n", "Metric", "Value");
        System.out.println(YELLOW + "├────────────────────────────┼─────────────────────────────┤" + RESET);

        printStatRow("Total Tasks Assigned", String.valueOf(totalAssigned));
//        printStatRow("Total Tasks Completed", String.valueOf(totalCompleted));
        printStatRow("Total Tasks Failed", String.valueOf(totalFailed));
//        printStatRow("Average Latency", String.format("%.2f ms", avgLatency));

        System.out.println(YELLOW + "├────────────────────────────┼─────────────────────────────┤" + RESET);
        printStatRow("Cloud Assignments", String.valueOf(cloudCount));
        printStatRow("RSU Assignments", String.valueOf(rsuCount));
        printStatRow("Local Assignments", String.valueOf(localCount));

        int total = cloudCount + rsuCount + localCount;
        if (total > 0) {
            System.out.println(YELLOW + "├────────────────────────────┼─────────────────────────────┤" + RESET);
            printStatRow("Cloud Distribution", String.format("%.1f%%", cloudCount * 100.0 / total));
            printStatRow("RSU Distribution", String.format("%.1f%%", rsuCount * 100.0 / total));
            printStatRow("Local Distribution", String.format("%.1f%%", localCount * 100.0 / total));
        }

        System.out.println(YELLOW + "└────────────────────────────┴─────────────────────────────┘" + RESET);
    }

    private static void printStatRow(String metric, String value) {
        System.out.printf(YELLOW + "│ " + RESET + "%-26s " + YELLOW + "│ " + RESET + "%27s " + YELLOW + "│" + RESET + "%n",
                metric, value);
    }

    // ─────────────────────────────────────────────────────────
    //  RSU SETUP TABLE
    // ─────────────────────────────────────────────────────────
    public static void printRsuSetupTable(List<RSUServer> rsus) {
        System.out.println(CYAN + "  ┌──────────┬────────────┬────────────┬─────────────┬───────────┐" + RESET);
        System.out.printf(CYAN + "  │" + BOLD + WHITE + " %-8s " + RESET +
                        CYAN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        CYAN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        CYAN + "│" + BOLD + WHITE + " %-11s " + RESET +
                        CYAN + "│" + BOLD + WHITE + " %-9s " + RESET +
                        CYAN + "│" + RESET + "%n",
                "RSU ID", "MIPS", "RAM (MB)", "BW (Mbps)", "SINR (dB)");
        System.out.println(CYAN + "  ├──────────┼────────────┼────────────┼─────────────┼───────────┤" + RESET);
        for (RSUServer rsu : rsus) {
            System.out.printf(CYAN + "  │" + RESET + " %-8s " +
                            CYAN + "│" + RESET + " %,10d " +
                            CYAN + "│" + RESET + " %,10d " +
                            CYAN + "│" + RESET + " %9.0f   " +
                            CYAN + "│" + RESET + " %7.1f   " +
                            CYAN + "│" + RESET + "%n",
                    rsu.getId(), rsu.getTotalMips(), rsu.getTotalRamMB(),
                    rsu.getBandwidthMbps(), rsu.getSinr());
        }
        System.out.println(CYAN + "  └──────────┴────────────┴────────────┴─────────────┴───────────┘" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  VEHICLE SETUP TABLE
    // ─────────────────────────────────────────────────────────
    public static void printVehicleSetupTable(List<Vehicle> vehicles) {
        System.out.println(GREEN + "  ┌──────────────┬────────────┬────────────┐" + RESET);
        System.out.printf(GREEN + "  │" + BOLD + WHITE + " %-12s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + BOLD + WHITE + " %-10s " + RESET +
                        GREEN + "│" + RESET + "%n",
                "Vehicle ID", "MIPS", "RAM (MB)");
        System.out.println(GREEN + "  ├──────────────┼────────────┼────────────┤" + RESET);
        for (Vehicle v : vehicles) {
            System.out.printf(GREEN + "  │" + RESET + " %-12s " +
                            GREEN + "│" + RESET + " %,10d " +
                            GREEN + "│" + RESET + " %,10d " +
                            GREEN + "│" + RESET + "%n",
                    v.getId(), v.getTotalMips(), v.getTotalRamMB());
        }
        System.out.println(GREEN + "  └──────────────┴────────────┴────────────┘" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  FEDERATED LEARNING TABLES
    // ─────────────────────────────────────────────────────────

    /**
     * Print a single FL round summary inline during simulation.
     */
    public static void printFederatedRoundSummary(int round, int updates, double avgLoss, boolean aggregated) {
        String status = aggregated
                ? GREEN + "AGGREGATED" + RESET
                : RED  + "AGG FAILED" + RESET;
        System.out.printf("%n  " + MAGENTA + "[FL Round %d]" + RESET +
                        "  Updates: %d  |  Avg Loss: %.4f  |  %s%n",
                round, updates, avgLoss, status);
    }

    /**
     * Print final FL statistics at end of simulation.
     */
    public static void printFederatedLearningSummary(int totalRounds, int totalUpdates,
                                                     int failedUpdates, int roundSize) {
        System.out.println();
        System.out.println(MAGENTA + "╔" + "═".repeat(60) + "╗" + RESET);
        System.out.println(MAGENTA + "║  " + BOLD + WHITE + padRight("FEDERATED LEARNING SUMMARY", 56) + RESET + MAGENTA + "  ║" + RESET);
        System.out.println(MAGENTA + "╚" + "═".repeat(60) + "╝" + RESET);
        System.out.println();
        System.out.println(MAGENTA + "┌────────────────────────────┬─────────────────────────────┐" + RESET);
        System.out.printf(MAGENTA + "│ " + BOLD + WHITE + "%-26s" + RESET + MAGENTA + " │ " + BOLD + WHITE + "%-27s" + RESET + MAGENTA + " │" + RESET + "%n", "Metric", "Value");
        System.out.println(MAGENTA + "├────────────────────────────┼─────────────────────────────┤" + RESET);

        printFLStatRow("FedAvg Rounds Completed", String.valueOf(totalRounds));
        printFLStatRow("Local Updates Sent", String.valueOf(totalUpdates));
        printFLStatRow("Failed Updates", String.valueOf(failedUpdates));
        printFLStatRow("Round Size (K)", String.valueOf(roundSize));

        System.out.println(MAGENTA + "└────────────────────────────┴─────────────────────────────┘" + RESET);
    }

    private static void printFLStatRow(String metric, String value) {
        System.out.printf(MAGENTA + "│ " + RESET + "%-26s " + MAGENTA + "│ " + RESET + "%27s " + MAGENTA + "│" + RESET + "%n",
                metric, value);
    }

    // ─────────────────────────────────────────────────────────
    //  TASK EXECUTION DETAILS TABLE
    //  Shows per-task resource hold times and execution metrics
    // ─────────────────────────────────────────────────────────
    public static void printTaskExecutionHeader() {
        System.out.println();
        System.out.println(CYAN + "╔" + "═".repeat(130) + "╗" + RESET);
        System.out.println(CYAN + "║  " + BOLD + WHITE + padRight("TASK EXECUTION & RESOURCE HOLD DETAILS", 126) + RESET + CYAN + "  ║" + RESET);
        System.out.println(CYAN + "╚" + "═".repeat(130) + "╝" + RESET);
        System.out.println();
        System.out.println(BLUE + "┌──────────┬────────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┬───────────┬──────────────┐" + RESET);
        System.out.printf(BLUE + "│" + BOLD + WHITE + " %-8s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-10s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-9s " + RESET +
                        BLUE + "│" + BOLD + WHITE + " %-12s " + RESET +
                        BLUE + "│" + RESET + "%n",
                "Task ID", "Target", "Exec (ms)", "Transfer(ms)", "Prop (ms)", "Hold (ms)", "MIPS Alloc", "RAM (MB)", "State");
        System.out.println(BLUE + "├──────────┼────────────┼──────────────┼──────────────┼──────────────┼──────────────┼──────────────┼───────────┼──────────────┤" + RESET);
    }

    public static void printTaskExecutionRow(String taskId, String target,
                                             double execMs, double transferMs, double propMs,
                                             long holdMs, int allocMips, int allocRam,
                                             String state) {
        String stateColor;
        switch (state) {
            case "COMPLETED": stateColor = GREEN; break;
            case "RUNNING":   stateColor = YELLOW; break;
            case "FAILED":    stateColor = RED; break;
            default:          stateColor = WHITE; break;
        }

        System.out.printf(BLUE + "│" + RESET + " %-8s " +
                        BLUE + "│" + RESET + " %-10s " +
                        BLUE + "│" + RESET + " %10.2f   " +
                        BLUE + "│" + RESET + " %10.2f   " +
                        BLUE + "│" + RESET + " %10.2f   " +
                        BLUE + "│" + RESET + " %10d   " +
                        BLUE + "│" + RESET + " %10d   " +
                        BLUE + "│" + RESET + " %7d   " +
                        BLUE + "│" + RESET + " %s%-12s" + RESET + " " +
                        BLUE + "│" + RESET + "%n",
                taskId, target, execMs, transferMs, propMs, holdMs,
                allocMips, allocRam, stateColor, state);
    }

    public static void printTaskExecutionFooter() {
        System.out.println(BLUE + "└──────────┴────────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────────┴───────────┴──────────────┘" + RESET);
    }

    // ─────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────
    private static String getDecisionColor(String decision) {
        if (decision == null) return WHITE;
        switch (decision.toLowerCase()) {
            case "cloud": return MAGENTA;
            case "rsu":   return CYAN;
            case "local": return GREEN;
            default:      return WHITE;
        }
    }

    private static String getHealthBar(double health) {
        int filled = Math.max(0, Math.min(5, (int) (health * 5)));
        String bar = "█".repeat(filled) + "░".repeat(5 - filled);

        if (health >= 0.7) return GREEN + bar + RESET;
        else if (health >= 0.4) return YELLOW + bar + RESET;
        else return RED + bar + RESET;
    }

    private static String getHealthColor(double health) {
        if (health >= 0.7) return GREEN;
        else if (health >= 0.4) return YELLOW;
        else return RED;
    }

    private static String getHealthStatus(double health) {
        if (health >= 0.8) return "HEALTHY";
        else if (health >= 0.6) return "GOOD";
        else if (health >= 0.4) return "FAIR";
        else if (health >= 0.2) return "POOR";
        else return "CRITICAL";
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}

