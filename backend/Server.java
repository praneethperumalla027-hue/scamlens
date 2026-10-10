import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ScamLens — Lightweight Java HTTP Server with Gemma AI Integration & Rule Fallback
 * 
 * Uses standard Java SE libraries (com.sun.net.httpserver & java.net.http).
 * No external Maven or Gradle dependencies required.
 */
public class Server {

    private static final int PORT = 8080;
    private static final String DEFAULT_GEMMA_MODEL = "gemma-4-26b-a4b-it";
    private static final Map<String, String> envCache = new HashMap<>();

    public static void main(String[] args) {
        // Pre-load local .env file if available
        loadLocalEnv();

        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port " + PORT);
            }
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Endpoints
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/analyze", new AnalyzeHandler());

            server.setExecutor(null);

            String apiKey = getGemmaApiKey();
            String model = getGemmaModel();

            System.out.println("==================================================");
            System.out.println(" 🛡️  ScamLens Java Backend is running!");
            System.out.println(" Server Port  : http://localhost:" + port);
            System.out.println(" Health Check : http://localhost:" + port + "/api/health");
            System.out.println(" Analyze API  : http://localhost:" + port + "/api/analyze (POST)");
            if (apiKey != null && !apiKey.isBlank()) {
                System.out.println(" AI Engine    : Enabled (Model: " + model + ")");
            } else {
                System.out.println(" AI Engine    : Disabled (No GEMMA_API_KEY found, running Heuristic Rules)");
            }
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

            boolean hasKey = getGemmaApiKey() != null && !getGemmaApiKey().isBlank();
            String response = "{"
                    + "\"status\":\"ok\","
                    + "\"service\":\"ScamLens Java Backend\","
                    + "\"version\":\"1.1.0\","
                    + "\"aiConfigured\":" + hasKey + ","
                    + "\"model\":\"" + getGemmaModel() + "\""
                    + "}";
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

            String message = extractMessageFromJson(body);
            if (message.isBlank()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Message text cannot be empty\"}");
                return;
            }

            System.out.println("\n[ScamLens] Analyzing incoming message (" + message.length() + " chars)...");

            AnalysisResult result = null;
            String apiKey = getGemmaApiKey();
            String model = getGemmaModel();

            // 1. Try Gemma AI if API key is present
            if (apiKey != null && !apiKey.isBlank()) {
                try {
                    System.out.println("[ScamLens] Calling Gemma API (model: " + model + ")...");
                    result = callGemmaApi(message, apiKey, model);
                    System.out.println("[ScamLens] Gemma AI analysis successful! Assigned risk: " + result.level);
                } catch (Exception e) {
                    System.err.println("[ScamLens] Gemma API request failed: " + e.getMessage());
                    System.out.println("[ScamLens] Automatically falling back to local heuristic rules...");
                }
            }

            // 2. Fallback to rule-based analyzer if AI was skipped or failed
            if (result == null) {
                result = analyzeMessage(message);
            }

            String jsonResponse = result.toJson();
            sendJsonResponse(exchange, 200, jsonResponse);
        }
    }

    /**
     * Call Gemma API using Java 11+ HttpClient
     */
    private static AnalysisResult callGemmaApi(String message, String apiKey, String model) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        String prompt = "You are ScamLens, a security AI analyzing suspicious messages for scam indicators.\n"
                + "Analyze the following message for fraud, phishing, advance-fee scam, or fake job/internship signals:\n\n"
                + "\"" + escapeJson(message) + "\"\n\n"
                + "Respond ONLY with a valid JSON object matching this schema. Do not include markdown codeblocks or thinking:\n"
                + "{\n"
                + "  \"level\": \"high\" or \"medium\" or \"low\",\n"
                + "  \"label\": \"Brief descriptive title (e.g. High Risk Scam, Suspicious Offer, Low Risk Alert)\",\n"
                + "  \"summary\": \"1 to 2 sentence assessment summary\",\n"
                + "  \"score\": integer from 0 (completely safe) to 100 (critical scam),\n"
                + "  \"phrases\": [\"suspicious phrase 1\", \"suspicious phrase 2\"],\n"
                + "  \"explanation\": \"Clear educational explanation of why this looks suspicious or safe\",\n"
                + "  \"actions\": [\"safe action step 1\", \"safe action step 2\", \"safe action step 3\"]\n"
                + "}";

        String requestJson = "{"
                + "\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]"
                + ",\"generationConfig\":{\"responseMimeType\":\"application/json\",\"temperature\":0.1}"
                + "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " - " + response.body());
        }

        return parseGemmaResponse(response.body(), model);
    }

    /**
     * Parse and sanitize AI JSON response safely
     */
    private static AnalysisResult parseGemmaResponse(String apiResponseBody, String model) {
        // Extract candidate text
        String text = extractCandidateText(apiResponseBody);
        if (text.isBlank()) {
            throw new RuntimeException("Gemma API returned empty response candidate");
        }

        // Find outermost JSON brackets { ... } (handles models that output markdown or thoughts)
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("Could not locate JSON block in Gemma output: " + text);
        }

        String json = text.substring(start, end + 1);

        // Extract individual fields with fallback defaults
        String rawLevel = extractStringField(json, "level").toLowerCase();
        String level;
        if (rawLevel.contains("high") || rawLevel.contains("critical")) {
            level = "high";
        } else if (rawLevel.contains("med") || rawLevel.contains("suspicious") || rawLevel.contains("mod")) {
            level = "medium";
        } else if (rawLevel.contains("low") || rawLevel.contains("safe")) {
            level = "low";
        } else {
            level = "medium";
        }

        String label = extractStringField(json, "label");
        if (label.isBlank()) label = "AI Risk Assessment";

        String summary = extractStringField(json, "summary");
        if (summary.isBlank()) summary = "Assessment generated by Gemma AI.";

        String explanation = extractStringField(json, "explanation");
        if (explanation.isBlank()) explanation = "No detailed explanation provided by the AI.";

        int score = extractIntField(json, "score", -1);
        if (score < 0) {
            score = switch (level) {
                case "high" -> 85;
                case "medium" -> 50;
                default -> 15;
            };
        }
        score = Math.max(0, Math.min(100, score));

        List<String> phrases = extractStringArray(json, "phrases");
        if (phrases.isEmpty()) {
            phrases.add("Specific phrase indicators evaluated by AI");
        }

        List<String> actions = extractStringArray(json, "actions");
        if (actions.isEmpty()) {
            actions.add("Verify sender identity through independent official channels.");
            actions.add("Never share financial credentials or OTPs.");
        }

        String engine = "Gemma AI (" + model + ")";
        return new AnalysisResult(level, label, summary, explanation, phrases, actions, score, engine);
    }

    /**
     * Heuristic analysis logic (Rule-based fallback)
     */
    private static AnalysisResult analyzeMessage(String message) {
        String lower = message.toLowerCase();

        List<String> suspiciousPhrases = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int score = 10;

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

        return new AnalysisResult(level, label, summary, explanation, suspiciousPhrases, actions, score, "Rule Heuristics");
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
        String engine;

        AnalysisResult(String level, String label, String summary, String explanation,
                       List<String> suspiciousPhrases, List<String> actions, int score, String engine) {
            this.level = level;
            this.label = label;
            this.summary = summary;
            this.explanation = explanation;
            this.suspiciousPhrases = suspiciousPhrases;
            this.actions = actions;
            this.score = score;
            this.engine = engine;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"level\":").append(quote(level)).append(",");
            sb.append("\"label\":").append(quote(label)).append(",");
            sb.append("\"summary\":").append(quote(summary)).append(",");
            sb.append("\"explanation\":").append(quote(explanation)).append(",");
            sb.append("\"engine\":").append(quote(engine)).append(",");
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
     * Environment variable & .env loader helpers
     */
    private static void loadLocalEnv() {
        try {
            Path envPath = Paths.get(".env");
            if (!Files.exists(envPath)) {
                envPath = Paths.get("..", ".env");
            }
            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim();
                    String val = trimmed.substring(eq + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    envCache.put(key, val);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getGemmaApiKey() {
        String key = System.getenv("GEMMA_API_KEY");
        if (key == null || key.isBlank()) {
            key = envCache.get("GEMMA_API_KEY");
        }
        return (key != null) ? key.trim() : null;
    }

    private static String getGemmaModel() {
        String model = System.getenv("GEMMA_MODEL");
        if (model == null || model.isBlank()) {
            model = envCache.get("GEMMA_MODEL");
        }
        return (model != null && !model.isBlank()) ? model.trim() : DEFAULT_GEMMA_MODEL;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static String extractMessageFromJson(String json) {
        if (json == null || json.isBlank()) return "";
        return extractStringField(json, "message");
    }

    private static String extractCandidateText(String json) {
        int textIdx = json.indexOf("\"text\":");
        if (textIdx == -1) return "";
        int firstQuote = json.indexOf("\"", textIdx + 7);
        if (firstQuote == -1) return "";

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractStringField(String json, String field) {
        String target = "\"" + field + "\"";
        int idx = json.indexOf(target);
        if (idx == -1) return "";

        int colon = json.indexOf(':', idx + target.length());
        if (colon == -1) return "";

        int firstQuote = json.indexOf('\"', colon);
        if (firstQuote == -1) return "";

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static int extractIntField(String json, String field, int defaultValue) {
        String target = "\"" + field + "\"";
        int idx = json.indexOf(target);
        if (idx == -1) return defaultValue;

        int colon = json.indexOf(':', idx + target.length());
        if (colon == -1) return defaultValue;

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }

        if (start < end) {
            try {
                return Integer.parseInt(json.substring(start, end));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static List<String> extractStringArray(String json, String field) {
        List<String> list = new ArrayList<>();
        String target = "\"" + field + "\"";
        int idx = json.indexOf(target);
        if (idx == -1) return list;

        int bracketStart = json.indexOf('[', idx);
        if (bracketStart == -1) return list;
        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketEnd == -1) return list;

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                if (inQuotes) {
                    list.add(sb.toString().trim());
                    sb.setLength(0);
                    inQuotes = false;
                } else {
                    inQuotes = true;
                }
            } else if (inQuotes) {
                sb.append(c);
            }
        }
        return list;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
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
        return sb.toString();
    }

    private static String quote(String string) {
        return "\"" + escapeJson(string) + "\"";
    }
}
