package org.fog.test.VEC.utils;

import org.fog.test.VEC.task.Task;
import org.fog.test.VEC.infrastructure.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Comprehensive simulation logger that captures all events and generates HTML report.
 * This class intercepts all major simulation events to build a complete report.
 */
public class SimulationLogger {

    private static SimulationLogger instance;

    // Data structures to capture simulation events
    private List<InfrastructureSnapshot> infrastructureSnapshots;
    private List<TaskGenerationEvent> taskGenerationEvents;
    private List<String> simulationLogs;
    private long simulationStartTime;
    private long simulationEndTime;
    private int simulationDurationSec;

    // Infrastructure references
    private CloudServer cloud;
    private List<RSUServer> rsus;
    private Vehicle localVehicle;

    // Statistics
    private int totalAssigned = 0;
    private int totalCompleted = 0;
    private int totalFailed = 0;
    private long totalLatencyMs = 0;
    private int cloudAssigned = 0;
    private int rsuAssigned = 0;
    private int localAssigned = 0;

    private SimulationLogger() {
        this.infrastructureSnapshots = Collections.synchronizedList(new ArrayList<>());
        this.taskGenerationEvents = Collections.synchronizedList(new ArrayList<>());
        this.simulationLogs = Collections.synchronizedList(new ArrayList<>());
    }

    public static synchronized SimulationLogger getInstance() {
        if (instance == null) {
            instance = new SimulationLogger();
        }
        return instance;
    }

    public void initialize(CloudServer cloud, List<RSUServer> rsus, Vehicle localVehicle, int durationSec) {
        this.cloud = cloud;
        this.rsus = new ArrayList<>(rsus);
        this.localVehicle = localVehicle;
        this.simulationDurationSec = durationSec;
        this.simulationStartTime = System.currentTimeMillis();
    }

    public void logTaskGeneration(Task task, String mlDecision, String targetNode) {
        TaskGenerationEvent event = new TaskGenerationEvent(
            System.currentTimeMillis() - simulationStartTime,
            task.getTaskId(),
            task.getVehicleId(),
            task.getMobilityStatus().name(),
            task.getSignalStrength(),
            task.getCriticalTask(),
            task.getBandwidthMbps(),
            task.getNumberOfInstructions(),
            task.getTaskSizeMB(),
            mlDecision,
            targetNode
        );
        taskGenerationEvents.add(event);
    }

    public void captureInfrastructureSnapshot(String label) {
        InfrastructureSnapshot snapshot = new InfrastructureSnapshot(
            System.currentTimeMillis() - simulationStartTime,
            label,
            new NodeSnapshot("CLOUD_1", "CLOUD", cloud.getTotalMips(), cloud.getAvailableMips(),
                           cloud.getTotalRamMB(), cloud.getAvailableRamMB(),
                           cloud.getCpuUtilization(), cloud.getRamUtilization(), cloud.getHealthScore()),
            rsus.stream()
                .map(r -> new NodeSnapshot(r.getId(), "RSU", r.getTotalMips(), r.getAvailableMips(),
                                         r.getTotalRamMB(), r.getAvailableRamMB(),
                                         r.getCpuUtilization(), r.getRamUtilization(), r.getHealthScore()))
                .toArray(NodeSnapshot[]::new),
            new NodeSnapshot("VEH_LOCAL", "VEHICLE", localVehicle.getTotalMips(), localVehicle.getAvailableMips(),
                           localVehicle.getTotalRamMB(), localVehicle.getAvailableRamMB(),
                           localVehicle.getCpuUtilization(), localVehicle.getRamUtilization(), localVehicle.getHealthScore())
        );
        infrastructureSnapshots.add(snapshot);
    }

    public void logEvent(String message) {
        String timestamp = String.format("[%.2fs]", (System.currentTimeMillis() - simulationStartTime) / 1000.0);
        simulationLogs.add(timestamp + " " + message);
    }

    public void updateStatistics(int assigned, int completed, int failed, long totalLatency,
                                 int cloud, int rsu, int local) {
        this.totalAssigned = assigned;
        this.totalCompleted = completed;
        this.totalFailed = failed;
        this.totalLatencyMs = totalLatency;
        this.cloudAssigned = cloud;
        this.rsuAssigned = rsu;
        this.localAssigned = local;
    }

    public void generateHTMLReport(String filename) {
        simulationEndTime = System.currentTimeMillis();

        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            writeHTMLHeader(out);
            writeSimulationOverview(out);
            writeStatisticsSummary(out);
            writeTaskGenerationTable(out);
            writeInfrastructureHealthHistory(out);
            writeSimulationLogs(out);
            writeHTMLFooter(out);

            System.out.println("\n✅ HTML Report generated: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to generate HTML report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeHTMLHeader(PrintWriter out) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");
        out.println("<head>");
        out.println("    <meta charset='UTF-8'>");
        out.println("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        out.println("    <title>VEC Simulator Report</title>");
        out.println("    <style>");
        out.println("        * { margin: 0; padding: 0; box-sizing: border-box; }");
        out.println("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }");
        out.println("        .container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 10px; box-shadow: 0 10px 40px rgba(0,0,0,0.3); overflow: hidden; }");
        out.println("        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; text-align: center; }");
        out.println("        .header h1 { font-size: 2.5em; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }");
        out.println("        .header p { font-size: 1.2em; opacity: 0.9; }");
        out.println("        .content { padding: 30px; }");
        out.println("        .section { margin-bottom: 40px; }");
        out.println("        .section h2 { color: #667eea; border-left: 5px solid #667eea; padding-left: 15px; margin-bottom: 20px; font-size: 1.8em; }");
        out.println("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }");
        out.println("        .stat-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 25px; border-radius: 10px; text-align: center; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }");
        out.println("        .stat-value { font-size: 2.5em; font-weight: bold; margin-bottom: 5px; }");
        out.println("        .stat-label { font-size: 0.9em; opacity: 0.9; text-transform: uppercase; letter-spacing: 1px; }");
        out.println("        table { width: 100%; border-collapse: collapse; background: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-radius: 8px; overflow: hidden; }");
        out.println("        thead { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }");
        out.println("        th { padding: 15px; text-align: left; font-weight: 600; }");
        out.println("        td { padding: 12px 15px; border-bottom: 1px solid #f0f0f0; }");
        out.println("        tbody tr:hover { background: #f8f9ff; }");
        out.println("        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 0.85em; font-weight: 600; }");
        out.println("        .badge-cloud { background: #e3d5ff; color: #764ba2; }");
        out.println("        .badge-rsu { background: #d5f0ff; color: #0066cc; }");
        out.println("        .badge-local { background: #d5ffd5; color: #2d8c2d; }");
        out.println("        .badge-yes { background: #ffcccc; color: #cc0000; }");
        out.println("        .badge-no { background: #ccffcc; color: #00cc00; }");
        out.println("        .health-healthy { background: #d4edda; color: #155724; padding: 5px 10px; border-radius: 5px; font-weight: bold; }");
        out.println("        .health-good { background: #cce5ff; color: #004085; padding: 5px 10px; border-radius: 5px; font-weight: bold; }");
        out.println("        .health-fair { background: #fff3cd; color: #856404; padding: 5px 10px; border-radius: 5px; font-weight: bold; }");
        out.println("        .health-poor { background: #f8d7da; color: #721c24; padding: 5px 10px; border-radius: 5px; font-weight: bold; }");
        out.println("        .health-critical { background: #f5c6cb; color: #721c24; padding: 5px 10px; border-radius: 5px; font-weight: bold; }");
        out.println("        .log-container { background: #1e1e1e; color: #d4d4d4; padding: 20px; border-radius: 8px; font-family: 'Consolas', 'Monaco', monospace; font-size: 0.9em; max-height: 500px; overflow-y: auto; }");
        out.println("        .log-entry { margin-bottom: 5px; line-height: 1.6; }");
        out.println("        .timestamp { color: #4ec9b0; }");
        out.println("        .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; border-top: 1px solid #e0e0e0; }");
        out.println("        .chart-container { background: #f8f9ff; padding: 20px; border-radius: 8px; margin-bottom: 20px; }");
        out.println("    </style>");
        out.println("</head>");
        out.println("<body>");
        out.println("    <div class='container'>");
    }

    private void writeSimulationOverview(PrintWriter out) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long durationMs = simulationEndTime - simulationStartTime;

        out.println("        <div class='header'>");
        out.println("            <h1>🚗 Vehicular Edge Computing Simulator</h1>");
        out.println("            <p>ML-Driven Offloading Decision System v2.0</p>");
        out.println("        </div>");
        out.println("        <div class='content'>");
        out.println("            <div class='section'>");
        out.println("                <h2>📋 Simulation Overview</h2>");
        out.println("                <table>");
        out.println("                    <tr><td><strong>Start Time</strong></td><td>" + sdf.format(new Date(simulationStartTime)) + "</td></tr>");
        out.println("                    <tr><td><strong>End Time</strong></td><td>" + sdf.format(new Date(simulationEndTime)) + "</td></tr>");
        out.println("                    <tr><td><strong>Duration</strong></td><td>" + (durationMs / 1000.0) + " seconds</td></tr>");
        out.println("                    <tr><td><strong>ML Endpoint</strong></td><td>http://127.0.0.1:8000/predict</td></tr>");
        out.println("                    <tr><td><strong>Infrastructure</strong></td><td>1 Cloud Server, " + rsus.size() + " RSU Servers, 1 Local Vehicle</td></tr>");
        out.println("                </table>");
        out.println("            </div>");
    }

    private void writeStatisticsSummary(PrintWriter out) {
        double avgLatency = totalCompleted > 0 ? (double) totalLatencyMs / totalCompleted : 0;
        int total = cloudAssigned + rsuAssigned + localAssigned;
        double cloudPct = total > 0 ? (cloudAssigned * 100.0 / total) : 0;
        double rsuPct = total > 0 ? (rsuAssigned * 100.0 / total) : 0;
        double localPct = total > 0 ? (localAssigned * 100.0 / total) : 0;

        out.println("            <div class='section'>");
        out.println("                <h2>📊 Simulation Statistics</h2>");
        out.println("                <div class='stats-grid'>");
        out.println("                    <div class='stat-card'>");
        out.println("                        <div class='stat-value'>" + totalAssigned + "</div>");
        out.println("                        <div class='stat-label'>Tasks Assigned</div>");
        out.println("                    </div>");
        out.println("                    <div class='stat-card'>");
        out.println("                        <div class='stat-value'>" + totalCompleted + "</div>");
        out.println("                        <div class='stat-label'>Tasks Completed</div>");
        out.println("                    </div>");
        out.println("                    <div class='stat-card'>");
        out.println("                        <div class='stat-value'>" + totalFailed + "</div>");
        out.println("                        <div class='stat-label'>Tasks Failed</div>");
        out.println("                    </div>");
        out.println("                    <div class='stat-card'>");
        out.println("                        <div class='stat-value'>" + String.format("%.0f", avgLatency) + " ms</div>");
        out.println("                        <div class='stat-label'>Avg Latency</div>");
        out.println("                    </div>");
        out.println("                </div>");
        out.println("                <h3 style='color: #667eea; margin-top: 20px;'>Task Distribution</h3>");
        out.println("                <table>");
        out.println("                    <thead><tr><th>Target</th><th>Count</th><th>Percentage</th></tr></thead>");
        out.println("                    <tbody>");
        out.println("                        <tr><td><span class='badge badge-cloud'>CLOUD</span></td><td>" + cloudAssigned + "</td><td>" + String.format("%.1f%%", cloudPct) + "</td></tr>");
        out.println("                        <tr><td><span class='badge badge-rsu'>RSU</span></td><td>" + rsuAssigned + "</td><td>" + String.format("%.1f%%", rsuPct) + "</td></tr>");
        out.println("                        <tr><td><span class='badge badge-local'>LOCAL</span></td><td>" + localAssigned + "</td><td>" + String.format("%.1f%%", localPct) + "</td></tr>");
        out.println("                    </tbody>");
        out.println("                </table>");
        out.println("            </div>");
    }

    private void writeTaskGenerationTable(PrintWriter out) {
        out.println("            <div class='section'>");
        out.println("                <h2>🎯 Task Generation & ML Offloading</h2>");
        out.println("                <table>");
        out.println("                    <thead>");
        out.println("                        <tr>");
        out.println("                            <th>Task ID</th>");
        out.println("                            <th>Vehicle</th>");
        out.println("                            <th>Mobility</th>");
        out.println("                            <th>Signal (dBm)</th>");
        out.println("                            <th>Critical</th>");
        out.println("                            <th>BW (Mbps)</th>");
        out.println("                            <th>Instructions</th>");
        out.println("                            <th>Size (MB)</th>");
        out.println("                            <th>ML Decision</th>");
        out.println("                            <th>Target</th>");
        out.println("                        </tr>");
        out.println("                    </thead>");
        out.println("                    <tbody>");

        for (TaskGenerationEvent event : taskGenerationEvents) {
            String criticalBadge = event.critical == 1 ?
                "<span class='badge badge-yes'>YES</span>" :
                "<span class='badge badge-no'>NO</span>";

            String decisionBadge = "";
            if ("cloud".equalsIgnoreCase(event.mlDecision)) {
                decisionBadge = "<span class='badge badge-cloud'>CLOUD</span>";
            } else if ("rsu".equalsIgnoreCase(event.mlDecision)) {
                decisionBadge = "<span class='badge badge-rsu'>RSU</span>";
            } else if ("local".equalsIgnoreCase(event.mlDecision)) {
                decisionBadge = "<span class='badge badge-local'>LOCAL</span>";
            }

            out.println("                        <tr>");
            out.println("                            <td>" + event.taskId + "</td>");
            out.println("                            <td>" + event.vehicleId + "</td>");
            out.println("                            <td>" + event.mobility + "</td>");
            out.println("                            <td>" + event.signal + "</td>");
            out.println("                            <td>" + criticalBadge + "</td>");
            out.println("                            <td>" + String.format("%.1f", event.bandwidth) + "</td>");
            out.println("                            <td>" + event.instructions + "</td>");
            out.println("                            <td>" + String.format("%.2f", event.taskSize) + "</td>");
            out.println("                            <td>" + decisionBadge + "</td>");
            out.println("                            <td>" + (event.targetNode != null ? event.targetNode : "-") + "</td>");
            out.println("                        </tr>");
        }

        out.println("                    </tbody>");
        out.println("                </table>");
        out.println("            </div>");
    }

    private void writeInfrastructureHealthHistory(PrintWriter out) {
        out.println("            <div class='section'>");
        out.println("                <h2>💪 Infrastructure Health Status</h2>");

        for (InfrastructureSnapshot snapshot : infrastructureSnapshots) {
            out.println("                <h3 style='color: #667eea; margin-top: 25px;'>" + snapshot.label +
                       " <span style='font-size: 0.8em; color: #999;'>(" +
                       String.format("%.1fs", snapshot.timestamp / 1000.0) + ")</span></h3>");
            out.println("                <table>");
            out.println("                    <thead>");
            out.println("                        <tr>");
            out.println("                            <th>Node</th>");
            out.println("                            <th>Type</th>");
            out.println("                            <th>Total MIPS</th>");
            out.println("                            <th>Avail MIPS</th>");
            out.println("                            <th>Total RAM</th>");
            out.println("                            <th>Avail RAM</th>");
            out.println("                            <th>CPU Usage</th>");
            out.println("                            <th>RAM Usage</th>");
            out.println("                            <th>Health</th>");
            out.println("                            <th>Status</th>");
            out.println("                        </tr>");
            out.println("                    </thead>");
            out.println("                    <tbody>");

            // Cloud
            writeNodeRow(out, snapshot.cloud);

            // RSUs
            for (NodeSnapshot rsu : snapshot.rsus) {
                writeNodeRow(out, rsu);
            }

            // Local Vehicle
            writeNodeRow(out, snapshot.localVehicle);

            out.println("                    </tbody>");
            out.println("                </table>");
        }

        out.println("            </div>");
    }

    private void writeNodeRow(PrintWriter out, NodeSnapshot node) {
        String healthStatus = getHealthStatus(node.health);
        String healthClass = getHealthClass(node.health);

        out.println("                        <tr>");
        out.println("                            <td><strong>" + node.id + "</strong></td>");
        out.println("                            <td>" + node.type + "</td>");
        out.println("                            <td>" + node.totalMips + "</td>");
        out.println("                            <td>" + node.availMips + "</td>");
        out.println("                            <td>" + node.totalRam + " MB</td>");
        out.println("                            <td>" + node.availRam + " MB</td>");
        out.println("                            <td>" + String.format("%.1f%%", node.cpuUsage * 100) + "</td>");
        out.println("                            <td>" + String.format("%.1f%%", node.ramUsage * 100) + "</td>");
        out.println("                            <td>" + String.format("%.3f", node.health) + "</td>");
        out.println("                            <td><span class='" + healthClass + "'>" + healthStatus + "</span></td>");
        out.println("                        </tr>");
    }

    private void writeSimulationLogs(PrintWriter out) {
        out.println("            <div class='section'>");
        out.println("                <h2>📜 Simulation Event Log</h2>");
        out.println("                <div class='log-container'>");

        for (String log : simulationLogs) {
            out.println("                    <div class='log-entry'>" + escapeHtml(log) + "</div>");
        }

        out.println("                </div>");
        out.println("            </div>");
    }

    private void writeHTMLFooter(PrintWriter out) {
        out.println("        </div>");
        out.println("        <div class='footer'>");
        out.println("            <p>Generated by VEC Simulator v2.0 | ML-Driven Offloading Decision System</p>");
        out.println("            <p style='font-size: 0.9em; margin-top: 5px;'>Report generated at: " +
                   new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>");
        out.println("        </div>");
        out.println("    </div>");
        out.println("</body>");
        out.println("</html>");
    }

    private String getHealthStatus(double health) {
        if (health >= 0.8) return "HEALTHY";
        else if (health >= 0.6) return "GOOD";
        else if (health >= 0.4) return "FAIR";
        else if (health >= 0.2) return "POOR";
        else return "CRITICAL";
    }

    private String getHealthClass(double health) {
        if (health >= 0.8) return "health-healthy";
        else if (health >= 0.6) return "health-good";
        else if (health >= 0.4) return "health-fair";
        else if (health >= 0.2) return "health-poor";
        else return "health-critical";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    // Inner classes for data storage
    static class TaskGenerationEvent {
        long timestamp;
        String taskId, vehicleId, mobility;
        int signal, critical, instructions;
        double bandwidth, taskSize;
        String mlDecision, targetNode;

        TaskGenerationEvent(long timestamp, String taskId, String vehicleId, String mobility,
                          int signal, int critical, double bandwidth, int instructions,
                          double taskSize, String mlDecision, String targetNode) {
            this.timestamp = timestamp;
            this.taskId = taskId;
            this.vehicleId = vehicleId;
            this.mobility = mobility;
            this.signal = signal;
            this.critical = critical;
            this.bandwidth = bandwidth;
            this.instructions = instructions;
            this.taskSize = taskSize;
            this.mlDecision = mlDecision;
            this.targetNode = targetNode;
        }
    }

    static class InfrastructureSnapshot {
        long timestamp;
        String label;
        NodeSnapshot cloud;
        NodeSnapshot[] rsus;
        NodeSnapshot localVehicle;

        InfrastructureSnapshot(long timestamp, String label, NodeSnapshot cloud,
                             NodeSnapshot[] rsus, NodeSnapshot localVehicle) {
            this.timestamp = timestamp;
            this.label = label;
            this.cloud = cloud;
            this.rsus = rsus;
            this.localVehicle = localVehicle;
        }
    }

    static class NodeSnapshot {
        String id, type;
        int totalMips, availMips, totalRam, availRam;
        double cpuUsage, ramUsage, health;

        NodeSnapshot(String id, String type, int totalMips, int availMips,
                   int totalRam, int availRam, double cpuUsage, double ramUsage, double health) {
            this.id = id;
            this.type = type;
            this.totalMips = totalMips;
            this.availMips = availMips;
            this.totalRam = totalRam;
            this.availRam = availRam;
            this.cpuUsage = cpuUsage;
            this.ramUsage = ramUsage;
            this.health = health;
        }
    }
}

