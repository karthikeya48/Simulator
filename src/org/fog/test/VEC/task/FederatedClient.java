package org.fog.test.VEC.task;

import org.fog.test.VEC.config.SimConstants;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP client for Federated Learning endpoints.
 *
 * POST /local_update  — sends a completed task's parameters + actual offload
 *                        target so the server can do one local SGD step on
 *                        a clone of the global ACTNet model.
 *
 * POST /aggregate     — triggers Federated Averaging (FedAvg) on the server.
 *                        The server averages all collected local weight updates
 *                        and replaces the global model:
 *
 *                        w_global = (1/K) Σ_k w_k
 *
 *                        Reference: McMahan et al., AISTATS 2017
 *
 * Request body for /local_update:
 * {
 *   "vehicle_id": "VEH_1",
 *   "bandwidth_mbps": 45.2,
 *   "critical_task": 0,
 *   "mobility_status": "low",
 *   "number_of_instructions_mips": 3200,
 *   "actual_target": "rsu",
 *   "Infrastructure": { ... }
 * }
 *
 * Response from /local_update:
 * { "status": "Weights received", "local_loss": 0.0342 }
 *
 * Response from /aggregate:
 * { "message": "Global model updated using updates from 5 clients." }
 */
public class FederatedClient {

    /**
     * Send a local training update for a completed task.
     *
     * @return the local loss value returned by the server, or -1.0 on error
     */
    public static double sendLocalUpdate(Task task, String actualTarget) {
        try {
            String json = buildLocalUpdateJson(task, actualTarget);
            String response = httpPost(SimConstants.FL_LOCAL_UPDATE_URL, json);
            return parseLoss(response);
        } catch (Exception e) {
            // Silently ignore — FL is best-effort and must not block simulation
            return -1.0;
        }
    }

    /**
     * Trigger federated aggregation on the server.
     *
     * @return the server response message, or null on error
     */
    public static String triggerAggregation() {
        try {
            String response = httpPost(SimConstants.FL_AGGREGATE_URL, "{}");
            return parseMessage(response);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── JSON builders ────────────────────────────────────

    private static String buildLocalUpdateJson(Task task, String actualTarget) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"vehicle_id\": \"").append(task.getVehicleId()).append("\", ");
        sb.append("\"bandwidth_mbps\": ").append(String.format("%.1f", task.getBandwidthMbps())).append(", ");
        sb.append("\"critical_task\": ").append(task.getCriticalTask()).append(", ");
        sb.append("\"mobility_status\": \"").append(task.getMobilityStatus().name().toLowerCase()).append("\", ");
        sb.append("\"number_of_instructions_mips\": ").append(task.getNumberOfInstructions()).append(", ");
        sb.append("\"actual_target\": \"").append(actualTarget.toLowerCase()).append("\", ");

        // Infrastructure block (empty — server only needs the 5 task fields + actual_target)
        sb.append("\"Infrastructure\": {}");
        sb.append("}");
        return sb.toString();
    }

    // ─── HTTP helper ──────────────────────────────────────

    private static String httpPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(SimConstants.FL_TIMEOUT_MS);
        conn.setReadTimeout(SimConstants.FL_TIMEOUT_MS);

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

    // ─── Response parsers ─────────────────────────────────

    /** Extract "local_loss" value from /local_update response */
    private static double parseLoss(String json) {
        int idx = json.indexOf("\"local_loss\"");
        if (idx < 0) return -1.0;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return -1.0;
        // find end of number (next comma or brace)
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        try {
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    /** Extract "message" value from /aggregate response */
    private static String parseMessage(String json) {
        int idx = json.indexOf("\"message\"");
        if (idx < 0) return json;
        int colon = json.indexOf(":", idx);
        int fq = json.indexOf("\"", colon + 1);
        int sq = json.indexOf("\"", fq + 1);
        if (fq >= 0 && sq >= 0) {
            return json.substring(fq + 1, sq);
        }
        return json;
    }
}

