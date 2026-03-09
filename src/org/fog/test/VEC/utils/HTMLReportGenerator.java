package org.fog.test.VEC.utils;

import org.fog.test.VEC.task.Task;
import org.fog.test.VEC.infrastructure.*;

import java.io.*;
import java.util.*;

/**
 * HTML Report Generator for VEC Simulator
 */
public class HTMLReportGenerator {

    private StringBuilder html;
    private List<Task> allTasks;
    private Map<String, Integer> infraStats;

    public HTMLReportGenerator() {
        this.html = new StringBuilder();
        this.allTasks = new ArrayList<>();
        this.infraStats = new HashMap<>();
    }

    public void addTask(Task task) {
        allTasks.add(task);
    }

    public void generateReport(CloudServer cloud, List<RSUServer> rsus,
                               List<Vehicle> vehicles, int totalAssigned,
                               int totalCompleted, int totalFailed, double avgLatency,
                               int cloudCount, int rsuCount, int vehicleCount) throws IOException {

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<meta charset='UTF-8'>\n");
        sb.append("<title>VEC Simulator Report</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        sb.append("h1 { color: #333; border-bottom: 3px solid #0066cc; padding-bottom: 10px; }\n");
        sb.append("h2 { color: #0066cc; margin-top: 30px; }\n");
        sb.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; background-color: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("th { background-color: #0066cc; color: white; padding: 12px; text-align: left; }\n");
        sb.append("td { padding: 10px; border-bottom: 1px solid #ddd; }\n");
        sb.append("tr:hover { background-color: #f9f9f9; }\n");
        sb.append(".healthy { background-color: #d4edda; color: #155724; font-weight: bold; }\n");
        sb.append(".good { background-color: #cce5ff; color: #004085; font-weight: bold; }\n");
        sb.append(".fair { background-color: #fff3cd; color: #856404; font-weight: bold; }\n");
        sb.append(".poor { background-color: #f8d7da; color: #721c24; font-weight: bold; }\n");
        sb.append(".critical { background-color: #f5c6cb; color: #721c24; font-weight: bold; }\n");
        sb.append(".stat-box { display: inline-block; margin: 10px; padding: 15px; background-color: white; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); min-width: 200px; }\n");
        sb.append(".stat-value { font-size: 24px; font-weight: bold; color: #0066cc; }\n");
        sb.append(".stat-label { font-size: 12px; color: #666; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        // Header
        sb.append("<h1>🚗 Vehicular Edge Computing Simulator - Report</h1>\n");
        sb.append("<p><strong>Date:</strong> ").append(new Date()).append("</p>\n");

        // Statistics Summary
        sb.append("<h2>📊 Simulation Statistics</h2>\n");
        sb.append("<div>\n");
        sb.append("<div class='stat-box'><div class='stat-value'>").append(totalAssigned).append("</div><div class='stat-label'>Tasks Assigned</div></div>\n");
        sb.append("<div class='stat-box'><div class='stat-value'>").append(totalCompleted).append("</div><div class='stat-label'>Tasks Completed</div></div>\n");
        sb.append("<div class='stat-box'><div class='stat-value'>").append(totalFailed).append("</div><div class='stat-label'>Tasks Failed</div></div>\n");
        sb.append("<div class='stat-box'><div class='stat-value'>").append(String.format("%.0f ms", avgLatency)).append("</div><div class='stat-label'>Avg Latency</div></div>\n");
        double completionRate = totalAssigned > 0 ? (totalCompleted * 100.0 / totalAssigned) : 0;
        sb.append("<div class='stat-box'><div class='stat-value'>").append(String.format("%.1f%%", completionRate)).append("</div><div class='stat-label'>Completion Rate</div></div>\n");
        sb.append("</div>\n");

        // Task Generation Table
        sb.append("<h2>📋 Task Generation Details</h2>\n");
        sb.append("<table>\n");
        sb.append("<tr><th>Task ID</th><th>Vehicle</th><th>Mobility</th><th>Signal (dBm)</th><th>Critical</th><th>Bandwidth (Mbps)</th><th>Instructions (×1k MI)</th><th>Size (MB)</th><th>Offload Target</th></tr>\n");

        for (Task t : allTasks) {
            sb.append("<tr>\n");
            sb.append("<td>").append(t.getTaskId()).append("</td>\n");
            sb.append("<td>").append(t.getVehicleId()).append("</td>\n");
            sb.append("<td>").append(t.getMobilityStatus()).append("</td>\n");
            sb.append("<td>").append(t.getSignalStrength()).append("</td>\n");
            String critical = t.getCriticalTask() == 1 ? "YES" : "NO";
            sb.append("<td>").append(critical).append("</td>\n");
            sb.append("<td>").append(String.format("%.1f", t.getBandwidthMbps())).append("</td>\n");
            sb.append("<td>").append(t.getNumberOfInstructions() / 1000).append("</td>\n");
            sb.append("<td>").append(String.format("%.2f", t.getTaskSizeMB())).append("</td>\n");
            sb.append("<td>").append(t.getOffloadTarget() != null ? t.getOffloadTarget() : "N/A").append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("</table>\n");

        // Assignment Distribution
        sb.append("<h2>🎯 Task Assignment Distribution</h2>\n");
        sb.append("<table>\n");
        sb.append("<tr><th>Node Type</th><th>Count</th><th>Percentage</th></tr>\n");
        int total = cloudCount + rsuCount + vehicleCount;
        double cloudPct = total > 0 ? (cloudCount * 100.0 / total) : 0;
        double rsuPct = total > 0 ? (rsuCount * 100.0 / total) : 0;
        double vehiclePct = total > 0 ? (vehicleCount * 100.0 / total) : 0;

        sb.append("<tr><td><strong>Cloud</strong></td><td>").append(cloudCount).append("</td><td>").append(String.format("%.1f%%", cloudPct)).append("</td></tr>\n");
        sb.append("<tr><td><strong>RSU</strong></td><td>").append(rsuCount).append("</td><td>").append(String.format("%.1f%%", rsuPct)).append("</td></tr>\n");
        sb.append("<tr><td><strong>Vehicle (Local)</strong></td><td>").append(vehicleCount).append("</td><td>").append(String.format("%.1f%%", vehiclePct)).append("</td></tr>\n");
        sb.append("</table>\n");

        // Infrastructure Health
        sb.append("<h2>💪 Infrastructure Health Status</h2>\n");
        sb.append("<table>\n");
        sb.append("<tr><th>Node</th><th>CPU Usage</th><th>RAM Usage</th><th>Health Score</th><th>Status</th></tr>\n");

        // Cloud
        double cloudHealth = cloud.getHealthScore();
        String cloudStatus = getHealthStatus(cloudHealth);
        String cloudClass = getHealthClass(cloudHealth);
        sb.append("<tr><td><strong>CLOUD_1</strong></td><td>").append(String.format("%.1f%%", cloud.getCpuUtilization() * 100)).append("</td><td>").append(String.format("%.1f%%", cloud.getRamUtilization() * 100)).append("</td><td>").append(String.format("%.3f", cloudHealth)).append("</td><td class='").append(cloudClass).append("'>").append(cloudStatus).append("</td></tr>\n");

        // RSUs
        for (RSUServer r : rsus) {
            double rsuHealth = r.getHealthScore();
            String rsuStatus = getHealthStatus(rsuHealth);
            String rsuClass = getHealthClass(rsuHealth);
            sb.append("<tr><td><strong>").append(r.getId()).append("</strong></td><td>").append(String.format("%.1f%%", r.getCpuUtilization() * 100)).append("</td><td>").append(String.format("%.1f%%", r.getRamUtilization() * 100)).append("</td><td>").append(String.format("%.3f", rsuHealth)).append("</td><td class='").append(rsuClass).append("'>").append(rsuStatus).append("</td></tr>\n");
        }

        // Vehicle averages
        double vehicleCpuAvg = 0, vehicleRamAvg = 0, vehicleHealthAvg = 0;
        for (Vehicle v : vehicles) {
            vehicleCpuAvg += v.getCpuUtilization();
            vehicleRamAvg += v.getRamUtilization();
            vehicleHealthAvg += v.getHealthScore();
        }
        vehicleCpuAvg /= vehicles.size();
        vehicleRamAvg /= vehicles.size();
        vehicleHealthAvg /= vehicles.size();

        String vehicleStatus = getHealthStatus(vehicleHealthAvg);
        String vehicleClass = getHealthClass(vehicleHealthAvg);
        sb.append("<tr><td><strong>Vehicles (Avg)</strong></td><td>").append(String.format("%.1f%%", vehicleCpuAvg * 100)).append("</td><td>").append(String.format("%.1f%%", vehicleRamAvg * 100)).append("</td><td>").append(String.format("%.3f", vehicleHealthAvg)).append("</td><td class='").append(vehicleClass).append("'>").append(vehicleStatus).append("</td></tr>\n");

        sb.append("</table>\n");

        // Footer
        sb.append("<hr>\n");
        sb.append("<p style='text-align: center; color: #999;'><small>Generated by VEC Simulator v1.0</small></p>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        // Write to file
        try (FileWriter fw = new FileWriter("VEC_Simulator_Report.html")) {
            fw.write(sb.toString());
        }

        System.out.println("\n✅ HTML Report generated: VEC_Simulator_Report.html");
    }

    private String getHealthStatus(double health) {
        if (health >= 0.8) return "HEALTHY";
        else if (health >= 0.6) return "GOOD";
        else if (health >= 0.4) return "FAIR";
        else if (health >= 0.2) return "POOR";
        else return "CRITICAL";
    }

    private String getHealthClass(double health) {
        if (health >= 0.8) return "healthy";
        else if (health >= 0.6) return "good";
        else if (health >= 0.4) return "fair";
        else if (health >= 0.2) return "poor";
        else return "critical";
    }
}

