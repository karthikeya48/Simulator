package org.fog.test.VEC.task;

import org.fog.test.VEC.infrastructure.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the ML prediction endpoint to decide offloading target.
 *
 * POST http://127.0.0.1:8000/predict
 *
 * Request body includes task parameters + full infrastructure state:
 * {
 *   "bandwidth_mbps": 20,
 *   "critical_task": 1,
 *   "mobility_status": "High",
 *   "number_of_instructions_mips": 1000,
 *   "Infrastructure": {
 *     "local": { "mips_available": 100, "ram_mb": 1000 },
 *     "cloud": { "mips_available": 10000, "ram_mb": 16000 },
 *     "Rsu": {
 *       "RSU1": { "mips_available": 2500, "ram_mb": 4000, "bandwidth_mbps": 50, "sinr": 25 },
 *       ...
 *     }
 *   }
 * }
 *
 * Response:
 * {
 *   "actnet_decision": "rsu",
 *   "target_node": ["RSU3", { "RSU1": 0.17, "RSU2": 0.24, "RSU3": 0.14 }],
 *   "input_summary": { "mips": 1000.0, "mobility": "low" }
 * }
 */
public class MLOffloadPredictor {

    private static final String PREDICT_URL = "http://127.0.0.1:8000/predict";
    private static final int TIMEOUT_MS = 5000;

    /**
     * Queries the ML model with task + infrastructure state.
     * On failure, returns a rule-based fallback.
     */
    public static OffloadDecision predict(Task task, Vehicle localVehicle,
                                          CloudServer cloud, List<RSUServer> rsus) {
        try {
            String json = buildRequestJson(task, localVehicle, cloud, rsus);
            String response = httpPost(PREDICT_URL, json);
            return parseResponse(response);
        } catch (Exception e) {
            System.out.println("  [ML] Prediction service unavailable (" + e.getMessage() + "), using rule-based fallback.");
            return ruleBasedFallback(task);
        }
    }

    /**
     * Build the new request JSON with full infrastructure state.
     */
    private static String buildRequestJson(Task task, Vehicle localVehicle,
                                           CloudServer cloud, List<RSUServer> rsus) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"bandwidth_mbps\": ").append(String.format("%.1f", task.getBandwidthMbps())).append(", ");
        sb.append("\"critical_task\": ").append(task.getCriticalTask()).append(", ");

        // Send mobility status in all lowercase (low, medium, high)
        String mobility = task.getMobilityStatus().name().toLowerCase();
        sb.append("\"mobility_status\": \"").append(mobility).append("\", ");

        sb.append("\"number_of_instructions_mips\": ").append(task.getNumberOfInstructions()).append(", ");

        // Infrastructure block
        sb.append("\"Infrastructure\": {");

        // Local (vehicle)
        sb.append("\"local\": {");
        sb.append("\"mips_available\": ").append(localVehicle.getAvailableMips()).append(", ");
        sb.append("\"ram_mb\": ").append(localVehicle.getAvailableRamMB());
        sb.append("}, ");

        // Cloud
        sb.append("\"cloud\": {");
        sb.append("\"mips_available\": ").append(cloud.getAvailableMips()).append(", ");
        sb.append("\"ram_mb\": ").append(cloud.getAvailableRamMB());
        sb.append("}, ");

        // RSUs
        sb.append("\"Rsu\": {");
        for (int i = 0; i < rsus.size(); i++) {
            RSUServer rsu = rsus.get(i);
            if (i > 0) sb.append(", ");
            sb.append("\"").append(rsu.getId()).append("\": {");
            sb.append("\"mips_available\": ").append(rsu.getAvailableMips()).append(", ");
            sb.append("\"ram_mb\": ").append(rsu.getAvailableRamMB()).append(", ");
            sb.append("\"bandwidth_mbps\": ").append(String.format("%.0f", rsu.getBandwidthMbps())).append(", ");
            sb.append("\"sinr\": ").append(String.format("%.0f", rsu.getSinr()));
            sb.append("}");
        }
        sb.append("}");  // end Rsu

        sb.append("}");  // end Infrastructure
        sb.append("}");  // end root

        return sb.toString();
    }

    private static String httpPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    /**
     * Parse the ML response JSON.
     *
     * Response format:
     * {
     *   "actnet_decision": "rsu",
     *   "target_node": ["RSU3", {"RSU1": 0.17, "RSU2": 0.24, "RSU3": 0.14}],
     *   "input_summary": {"mips": 1000.0, "mobility": "low"}
     * }
     */
    private static OffloadDecision parseResponse(String json) {
        String decision = "local";
        String targetNodeName = null;
        Map<String, Double> rsuScores = new HashMap<>();

        // Parse actnet_decision
        int decIdx = json.indexOf("\"actnet_decision\"");
        if (decIdx >= 0) {
            int colonIdx = json.indexOf(":", decIdx);
            int firstQuote = json.indexOf("\"", colonIdx + 1);
            int secondQuote = json.indexOf("\"", firstQuote + 1);
            if (firstQuote >= 0 && secondQuote >= 0) {
                decision = json.substring(firstQuote + 1, secondQuote).toLowerCase();
            }
        }

        // Parse target_node if decision is "rsu"
        if ("rsu".equals(decision)) {
            int tnIdx = json.indexOf("\"target_node\"");
            if (tnIdx >= 0) {
                int bracketStart = json.indexOf("[", tnIdx);
                int bracketEnd = findMatchingBracket(json, bracketStart);
                if (bracketStart >= 0 && bracketEnd >= 0) {
                    String targetArray = json.substring(bracketStart + 1, bracketEnd);

                    // First element: target node name (string)
                    int fq = targetArray.indexOf("\"");
                    int sq = targetArray.indexOf("\"", fq + 1);
                    if (fq >= 0 && sq >= 0) {
                        targetNodeName = targetArray.substring(fq + 1, sq);
                    }

                    // Second element: scores map {...}
                    int braceStart = targetArray.indexOf("{");
                    int braceEnd = targetArray.lastIndexOf("}");
                    if (braceStart >= 0 && braceEnd >= 0) {
                        String scoresStr = targetArray.substring(braceStart + 1, braceEnd);
                        // Parse key-value pairs like "RSU1": 0.17
                        String[] pairs = scoresStr.split(",");
                        for (String pair : pairs) {
                            pair = pair.trim();
                            int kq1 = pair.indexOf("\"");
                            int kq2 = pair.indexOf("\"", kq1 + 1);
                            int colon = pair.indexOf(":", kq2);
                            if (kq1 >= 0 && kq2 >= 0 && colon >= 0) {
                                String key = pair.substring(kq1 + 1, kq2);
                                String valStr = pair.substring(colon + 1).trim();
                                try {
                                    rsuScores.put(key, Double.parseDouble(valStr));
                                } catch (NumberFormatException e) {
                                    // skip
                                }
                            }
                        }
                    }
                }
            }
        }

        return new OffloadDecision(decision, targetNodeName, rsuScores);
    }

    /**
     * Find the matching closing bracket for an opening bracket at position idx.
     */
    private static int findMatchingBracket(String json, int idx) {
        if (idx < 0 || idx >= json.length()) return -1;
        char open = json.charAt(idx);
        char close = (open == '[') ? ']' : '}';
        int depth = 1;
        for (int i = idx + 1; i < json.length(); i++) {
            if (json.charAt(i) == open) depth++;
            else if (json.charAt(i) == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Rule-based fallback when ML service is unavailable:
     *   - Critical task + high mobility → Cloud (stable backhaul)
     *   - Good signal + low mobility    → RSU
     *   - Otherwise                     → Local vehicle
     */
    private static OffloadDecision ruleBasedFallback(Task task) {
        if (task.getCriticalTask() == 1 && task.getMobilityStatus() == Task.MobilityStatus.HIGH) {
            return new OffloadDecision("cloud", null, null);
        }
        if (task.getNumberOfInstructions() > 5000 && task.getBandwidthMbps() > 15) {
            return new OffloadDecision("rsu", "RSU1", null);
        }
        return new OffloadDecision("local", null, null);
    }

    /**
     * Result holder for ML prediction.
     */
    public static class OffloadDecision {
        public final String actnetDecision;    // "local", "cloud", "rsu"
        public final String targetNodeName;    // e.g. "RSU3" (only for rsu decision)
        public final Map<String, Double> rsuScores; // RSU selection scores

        public OffloadDecision(String actnetDecision, String targetNodeName,
                               Map<String, Double> rsuScores) {
            this.actnetDecision = actnetDecision;
            this.targetNodeName = targetNodeName;
            this.rsuScores = rsuScores != null ? rsuScores : new HashMap<>();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Decision=").append(actnetDecision);
            if (targetNodeName != null) {
                sb.append(", Target=").append(targetNodeName);
            }
            if (!rsuScores.isEmpty()) {
                sb.append(", Scores=").append(rsuScores);
            }
            return sb.toString();
        }
    }
}

