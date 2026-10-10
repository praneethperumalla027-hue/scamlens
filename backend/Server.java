import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ScamLens — Lightweight Java HTTP Server
 * 
 * Uses standard Java SE library (com.sun.net.httpserver)
 * No Maven, Gradle, or external dependencies required.
 */
public class Server {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port " + PORT);
            }
        }

        try {
            // Create an HTTP server listening on all interfaces at the chosen port
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Endpoints
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/analyze", new AnalyzeHandler());

            // Default thread executor
            server.setExecutor(null);

            System.out.println("==================================================");
            System.out.println(" 🛡️  ScamLens Java Backend is running!");
            System.out.println(" Server Port : http://localhost:" + port);
            System.out.println(" Health Check: http://localhost:" + port + "/api/health");
            System.out.println(" Analyze API : http://localhost:" + port + "/api/analyze (POST)");
            System.out.println(" Press Ctrl+C in this terminal to stop the server.");
            System.out.println("==================================================");

            server.start();
        } catch (java.net.BindException e) {
            System.err.println("\n❌ Port " + port + " is already in use by another program.");
            System.err.println("👉 Troubleshooting tips:");
            System.err.println("   1. Check if another terminal window is already running ScamLens.");
            System.err.println("   2. Close the other instance, or specify a different port: java -cp backend Server <port>");
        } catch (IOException e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint: GET /api/health
     */
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String response = "{\"status\":\"ok\",\"service\":\"ScamLens Java Backend\",\"version\":\"1.0.0\"}";
            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * Analysis endpoint: POST /api/analyze
     */
    static class AnalyzeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            // Handle browser CORS pre-flight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            // Read request body
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Extract the message text from JSON (e.g. {"message": "..."})
            String message = extractMessageFromJson(body);

            if (message.isBlank()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Message text cannot be empty\"}");
                return;
            }

            System.out.println("\n[ScamLens] Analyzing incoming message (" + message.length() + " chars)...");

            // Perform heuristic analysis (Step 1 backend rules)
            AnalysisResult result = analyzeMessage(message);

            // Convert result to JSON string
            String jsonResponse = result.toJson();
            sendJsonResponse(exchange, 200, jsonResponse);
        }
    }

    /**
     * Heuristic analysis logic.
     * Evaluates text for known scam indicators (urgency, upfront money demands, unrealistic promises).
     * Later, this will be enhanced with Gemma API integration.
     */
    private static AnalysisResult analyzeMessage(String message) {
        String lower = message.toLowerCase();

        List<String> suspiciousPhrases = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int score = 10; // Base score (0 = completely safe, 100 = critical scam)

        // 1. Upfront Payment / Money Demands
        if (lower.contains("fee") || lower.contains("deposit") || lower.contains("pay")
                || lower.contains("registration fee") || lower.contains("refundable")
                || lower.contains("crypto") || lower.contains("gift card") || lower.contains("upi")) {
            suspiciousPhrases.add("Request for upfront payment, deposit, or fee");
            score += 45;
        }

        // 2. Artificial Urgency / Pressure
        if (lower.contains("urgent") || lower.contains("immediately") || lower.contains("within 2 hours")
                || lower.contains("tonight") || lower.contains("account suspended") || lower.contains("disconnected")) {
            suspiciousPhrases.add("High urgency / psychological pressure");
            score += 25;
        }

        // 3. Unrealistic Earnings / Unsolicited Rewards
        if (lower.contains("no prior interview") || lower.contains("selected") || lower.contains("winner")
                || lower.contains("won") || lower.contains("$45/hour") || lower.contains("$800/week")
                || lower.contains("grant") || lower.contains("lottery")) {
            suspiciousPhrases.add("Unsolicited reward or unrealistic compensation without interview");
            score += 25;
        }

        // 4. Phishing links / Suspicious domains
        if (lower.contains(".xyz") || lower.contains(".top") || lower.contains("bit.ly") || lower.contains("tinyurl")) {
            suspiciousPhrases.add("Suspicious or shortened URL link");
            score += 20;
        }

        // Cap score at 98 max
        score = Math.min(score, 98);

        String level;
        String label;
        String summary;
        String explanation;

        if (score >= 60) {
            level = "high";
            label = "High Risk";
            summary = "Multiple severe scam indicators detected, including upfront payment or urgent pressure.";
            explanation = "Legitimate employers, grant committees, and official agencies do not demand upfront fees or create artificial panic to force quick money transfers. This pattern strongly matches standard advance-fee fraud.";
            actions.add("Do NOT send money, UPI transfers, cryptocurrency, or gift cards.");
            actions.add("Do not click links or download any file attachments.");
            actions.add("Verify the company or agency independently on their official website.");
        } else if (score >= 35) {
            level = "medium";
            label = "Suspicious / Moderate Risk";
            summary = "Contains some common red flags. Proceed with high caution.";
            explanation = "The message contains potential urgency or unsolicited claims. While it could be automated marketing, it warrants independent verification.";
            actions.add("Check the sender's exact email domain (not just display name).");
            actions.add("Contact the organization through their official published phone number.");
        } else {
            level = "low";
            label = "Low Risk Indicators";
            summary = "No obvious urgency or payment demands found in primary heuristics.";
            explanation = "The message does not trigger common high-risk scam keywords, but always exercise standard security hygiene.";
            actions.add("Never share one-time passwords (OTPs), PINs, or credentials.");
            actions.add("Ensure the communication came from a verified, official contact channel.");
        }

        return new AnalysisResult(level, label, summary, explanation, suspiciousPhrases, actions, score);
    }

    /**
     * Data record for analysis output
     */
    static class AnalysisResult {
        String level;
        String label;
        String summary;
        String explanation;
        List<String> suspiciousPhrases;
        List<String> actions;
        int score;

        AnalysisResult(String level, String label, String summary, String explanation,
                       List<String> suspiciousPhrases, List<String> actions, int score) {
            this.level = level;
            this.label = label;
            this.summary = summary;
            this.explanation = explanation;
            this.suspiciousPhrases = suspiciousPhrases;
            this.actions = actions;
            this.score = score;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"level\":").append(quote(level)).append(",");
            sb.append("\"label\":").append(quote(label)).append(",");
            sb.append("\"summary\":").append(quote(summary)).append(",");
            sb.append("\"explanation\":").append(quote(explanation)).append(",");
            sb.append("\"score\":").append(score).append(",");

            // Phrases array
            sb.append("\"phrases\":[");
            for (int i = 0; i < suspiciousPhrases.size(); i++) {
                sb.append(quote(suspiciousPhrases.get(i)));
                if (i < suspiciousPhrases.size() - 1) sb.append(",");
            }
            sb.append("],");

            // Actions array
            sb.append("\"actions\":[");
            for (int i = 0; i < actions.size(); i++) {
                sb.append(quote(actions.get(i)));
                if (i < actions.size() - 1) sb.append(",");
            }
            sb.append("]");

            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Helper to send JSON responses with appropriate headers
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Add CORS headers so frontend from any port or file can communicate with backend
     */
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    /**
     * Simple JSON message field extractor (no third party library needed)
     */
    private static String extractMessageFromJson(String json) {
        if (json == null || json.isBlank()) return "";
        // Look for "message" : "value"
        int keyIndex = json.indexOf("\"message\"");
        if (keyIndex == -1) {
            // If body wasn't JSON formatted, treat the entire body as the message
            return json.trim();
        }

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return "";

        int firstQuote = json.indexOf("\"", colonIndex);
        if (firstQuote == -1) return "";

        StringBuilder val = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') val.append('\n');
                else if (c == 'r') val.append('\r');
                else if (c == 't') val.append('\t');
                else if (c == '"') val.append('"');
                else if (c == '\\') val.append('\\');
                else val.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                val.append(c);
            }
        }
        return val.toString();
    }

    /**
     * Helper to JSON-escape and quote strings
     */
    private static String quote(String string) {
        if (string == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : string.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
