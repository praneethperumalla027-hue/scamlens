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
        String envPort = System.getenv("PORT");
        if (envPort == null || envPort.isBlank()) {
            envPort = envCache.get("PORT");
        }
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT env variable: " + envPort + ". Using default " + PORT);
            }
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number argument: " + args[0] + ". Using " + port);
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
            boolean aiDisabled = isAiDisabled();

            System.out.println("==================================================");
            System.out.println(" 🛡️  ScamLens Java Backend is running!");
            System.out.println(" Server Port  : http://localhost:" + port);
            System.out.println(" Health Check : http://localhost:" + port + "/api/health");
            System.out.println(" Analyze API  : http://localhost:" + port + "/api/analyze (POST)");
            if (aiDisabled) {
                System.out.println(" AI Engine    : Disabled via DISABLE_AI flag (Using Rule Heuristics)");
            } else if (apiKey != null && !apiKey.isBlank()) {
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

            // 1. Try Gemma AI if API key is present and AI is not disabled
            if (!isAiDisabled() && apiKey != null && !apiKey.isBlank()) {
                try {
                    System.out.println("[ScamLens] Calling Gemma API (model: " + model + ")...");
                    result = callGemmaApi(message, apiKey, model);
                    if (result != null) {
                        System.out.println("[ScamLens] Gemma AI analysis successful! Assigned risk: " + result.level + " (Score: " + result.score + ")");
                    }
                } catch (Exception e) {
                    String cleanError = sanitizeErrorMessage(e.getMessage());
                    System.err.println("[ScamLens] Gemma API request failed: " + cleanError);
                    System.out.println("[ScamLens] Automatically falling back to local heuristic rules...");
                }
            } else if (isAiDisabled()) {
                System.out.println("[ScamLens] AI evaluation disabled via DISABLE_AI flag. Using Rule Heuristics.");
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
     * Configured with 45s request timeout, 10s connect timeout, and header-based authentication.
     */
    private static AnalysisResult callGemmaApi(String message, String apiKey, String model) throws Exception {
        // Clean endpoint without API key in URL (avoids secret leaks in logs/traces)
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

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
                + ",\"generationConfig\":{\"responseMimeType\":\"application/json\",\"temperature\":0.1,\"maxOutputTokens\":800}"
                + "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long elapsed = System.currentTimeMillis() - startTime;

        if (response.statusCode() != 200) {
            String sanitizedBody = sanitizeErrorMessage(response.body());
            throw new RuntimeException("HTTP " + response.statusCode() + " after " + elapsed + "ms: " + sanitizedBody);
        }

        System.out.println("[ScamLens] Gemma API completed successfully in " + elapsed + "ms (HTTP 200).");
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
     * Performs contextual detection, supports negation ("no registration fee"),
     * and generates explanations strictly based on evidence found.
     */
    static AnalysisResult analyzeMessage(String message) {
        String lower = message.toLowerCase();

        List<String> suspiciousPhrases = new ArrayList<>();
        List<String> evidenceExplanations = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int score = 10;

        // 1. Contextual Upfront Payment Detection (with Negation Handling)
        boolean hasPayment = hasPaymentDemand(lower);
        boolean hasNegatedPayment = hasExplicitNoFee(lower);

        if (hasPayment) {
            suspiciousPhrases.add("Request for upfront payment, deposit, or fee");
            evidenceExplanations.add("Requests upfront money, deposit, or fees (often disguised as refundable onboarding charges), which is characteristic of advance-fee scams.");
            actions.add("Do NOT send money, UPI transfers, cryptocurrency, or gift cards.");
            score += 45;
        } else if (hasNegatedPayment) {
            // Reward explicit verification that no fees are required
            score = Math.max(5, score - 5);
        }

        // 2. Artificial Urgency / Psychological Pressure
        boolean hasUrgency = detectUrgency(lower, suspiciousPhrases);
        if (hasUrgency) {
            evidenceExplanations.add("Uses high urgency or pressure tactics to rush your decision before you have time to verify.");
            actions.add("Do not rush. Legitimate employers allow reasonable time to evaluate opportunities.");
            score += 25;
        }

        // 3. Unrealistic Earnings / Unsolicited Rewards
        boolean hasUnrealistic = detectUnrealisticOffer(lower, suspiciousPhrases);
        if (hasUnrealistic) {
            evidenceExplanations.add("Promises unrealistic compensation, selection without prior interview, or unearned prizes/grants.");
            score += 25;
        }

        // 4. Phishing links / Suspicious domains
        boolean hasSuspiciousLink = detectSuspiciousLinks(lower, suspiciousPhrases);
        if (hasSuspiciousLink) {
            evidenceExplanations.add("Contains suspicious, shortened, or unverified URL links commonly associated with credential theft.");
            actions.add("Do not click links or enter passwords/credentials on unverified domains.");
            score += 20;
        }

        // Universal safe actions
        actions.add("Verify the opportunity directly on the organization's official careers portal.");
        actions.add("Never share one-time passwords (OTPs), bank account PINs, or sensitive documents.");

        score = Math.min(score, 98);

        String level;
        String label;
        String summary;
        String explanation;

        if (score >= 60) {
            level = "high";
            label = "High Risk";
            summary = "Multiple severe scam indicators detected in this message.";
            explanation = "Warning signs identified: " + String.join(" ", evidenceExplanations)
                    + " Legitimate organizations do not operate using these patterns.";
        } else if (score >= 35) {
            level = "medium";
            label = "Suspicious / Moderate Risk";
            summary = "Potential warning signals detected. Proceed with high caution.";
            explanation = "Specific concerns identified: " + String.join(" ", evidenceExplanations)
                    + " Exercise caution and independently verify the sender's identity.";
        } else {
            level = "low";
            label = "Low Risk Indicators";
            if (hasNegatedPayment) {
                summary = "No scam indicators found. The message explicitly states no fees are required.";
                explanation = "The message explicitly clarifies that no application or registration fees are required, and contains no urgency or phishing links. Always verify directly via official company channels.";
            } else {
                summary = "No obvious urgency or payment demands found in primary heuristics.";
                explanation = "The message does not trigger common high-risk scam patterns. Continue to follow standard online security hygiene.";
            }
        }

        return new AnalysisResult(level, label, summary, explanation, suspiciousPhrases, actions, score, "Rule Heuristics");
    }

    /**
     * Contextual payment detection:
     * Splits into clauses and verifies that payment keywords are not part of a negated statement.
     */
    static boolean hasPaymentDemand(String lower) {
        String[] clauses = lower.split("[.!?;\\n]+");
        for (String clause : clauses) {
            clause = clause.trim();
            if (clause.isEmpty()) continue;
            // If the clause specifically negates payment, skip it
            if (isNegatedPaymentClause(clause)) continue;

            if (clause.matches(".*\\b(pay|send|transfer|wire|remit)\\b.*\\b(fee|fees|deposit|deposits|money|amount|funds|\\$|\\u20B9|rs|inr|upi|crypto|gift card).*")) return true;
            if (clause.matches(".*\\b(registration|processing|application|onboarding|equipment|laptop|verification|refundable)\\s+(security\\s+)?(fee|fees|deposit|deposits)\\b.*")) return true;
            if (clause.matches(".*\\b(fee|deposit)\\s+(is|of)\\s+(\\$|\\u20B9|rs|inr|[0-9]+).*")) return true;
            if (clause.matches(".*\\b(pay|send)\\s+(\\$|\\u20B9|rs|inr|[0-9]+).*")) return true;
            if (clause.matches(".*\\b(via|through|using)\\s+(upi|crypto|bitcoin|gift card).*")) return true;
        }
        return false;
    }

    /**
     * Checks if a clause contains a negated payment statement like:
     * "no application or registration fee", "no fee", "never ask you to pay", "without any fee"
     */
    static boolean isNegatedPaymentClause(String s) {
        // Fee waiver and exemption patterns (e.g. "fee is waived", "fee waiver", "100% waiver")
        if (s.matches(".*\\b(fee|fees|deposit|charge|charges)\\s+([a-z0-9%]+\\s+){0,3}(waived|exempt|exempted|canceled|zero|not\\s+applicable)\\b.*")) return true;
        if (s.matches(".*\\b(waived|waives|exempts|cancel)\\s+([a-z0-9%]+\\s+){0,3}(fee|fees|deposit|charge|charges)\\b.*")) return true;
        if (s.contains("fee waiver") || s.contains("fees waived") || s.contains("waiver of fee") || s.contains("waiver for")
                || s.contains("100% waiver") || s.contains("waiver applied") || s.contains("waiving the fee") || s.contains("waive the fee")) return true;

        if (s.matches(".*\\bno\\s+([a-z0-9%]+\\s+){0,4}(fee|fees|deposit|deposits|charge|charges|cost|costs|payment|payments)\\b.*")) return true;
        if (s.matches(".*\\b(never|not|won't|will not)\\s+([a-z]+\\s+){0,4}(ask|require|demand)\\b.*\\b(pay|fee|deposit|money)\\b.*")) return true;
        if (s.matches(".*\\b(not\\s+be\\s+asked\\s+to\\s+pay|do\\s+not\\s+pay|not\\s+require\\s+payment)\\b.*")) return true;
        if (s.contains("free of cost") || s.contains("free of charge") || s.contains("at no cost") || s.contains("without any fee") || s.contains("without paying")) return true;
        return false;
    }

    /**
     * Checks if the overall message contains an explicit statement confirming no fees
     */
    static boolean hasExplicitNoFee(String lower) {
        return isNegatedPaymentClause(lower);
    }

    /**
     * Detects artificial urgency and psychological pressure tactics.
     * Accurately recognizes short windows (e.g. "within 30 minutes"), immediate action demands,
     * and ultimatums (e.g. "avoid losing your seat"), while not misidentifying standard interview deadlines.
     */
    static boolean detectUrgency(String lower, List<String> phrases) {
        // 1. Extreme short window: minutes or a few hours
        boolean shortWindow = lower.matches(".*\\bwithin\\s+([0-9]+|one|two|three|half\\s+an?)\\s*(minute|min|second|sec|hour|hr)s?\\b.*");

        // 2. Immediate action / pressure demands
        boolean immediateAction = lower.matches(".*\\b(pay|transfer|send|act|respond|confirm|register)\\s+(immediately|urgently|right\\s+away)\\b.*")
                || lower.contains("immediately") || lower.contains("urgently") || lower.contains("act now") || lower.contains("act fast");

        // 3. Time pressure & expiration
        boolean expirationPressure = lower.contains("today only") || lower.contains("only today")
                || lower.contains("expires tonight") || lower.contains("tonight at") || lower.contains("last chance");

        // 4. Fear of loss / Ultimatums
        boolean fearOfLoss = lower.matches(".*\\b(avoid|before)\\s+losing\\s+(your\\s+)?(seat|slot|position|offer|internship|job|opportunity)\\b.*")
                || lower.matches(".*\\b(your\\s+)?(offer|slot|seat|position|application)\\s+will\\s+expire\\b.*")
                || lower.matches(".*\\b(slot|seat|position)\\s+will\\s+be\\s+(given|allocated|passed|released)\\b.*")
                || lower.matches(".*\\b(account|service|connection)\\s+(will\\s+be\\s+)?(suspended|disconnected|blocked|terminated)\\b.*")
                || lower.contains("immediate action required") || lower.contains("immediate response required")
                || lower.contains("urgent notice") || lower.contains("urgent message");

        boolean match = shortWindow || immediateAction || expirationPressure || fearOfLoss;
        if (match) {
            phrases.add("High urgency / psychological pressure");
        }
        return match;
    }

    /**
     * Detects unrealistic offers / compensation without interviews
     */
    static boolean detectUnrealisticOffer(String lower, List<String> phrases) {
        boolean match = lower.contains("no prior interview") || lower.contains("no interview needed")
                || lower.contains("selected without interview") || lower.contains("winner")
                || lower.contains("won the lottery") || lower.contains("unsolicited grant")
                || lower.contains("$45/hour") || lower.contains("$800/week") || lower.contains("lottery");
        if (match) {
            phrases.add("Unsolicited reward or unrealistic compensation without interview");
        }
        return match;
    }

    /**
     * Detects suspicious / shortened / free TLD links
     */
    static boolean detectSuspiciousLinks(String lower, List<String> phrases) {
        boolean match = lower.contains(".xyz") || lower.contains(".top") || lower.contains("bit.ly")
                || lower.contains("tinyurl") || lower.contains(".is.gd") || lower.contains(".online");
        if (match) {
            phrases.add("Suspicious or shortened URL link");
        }
        return match;
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
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String allowedOrigin = resolveCorsOrigin(origin);
        if (allowedOrigin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
    }

    private static String resolveCorsOrigin(String requestOrigin) {
        String allowedOriginsEnv = System.getenv("ALLOWED_ORIGINS");
        if (allowedOriginsEnv == null || allowedOriginsEnv.isBlank()) {
            allowedOriginsEnv = envCache.get("ALLOWED_ORIGINS");
        }

        // If ALLOWED_ORIGINS is not set (development default), allow local origins or echo request origin
        if (allowedOriginsEnv == null || allowedOriginsEnv.isBlank()) {
            return (requestOrigin != null && !requestOrigin.isBlank()) ? requestOrigin : "*";
        }

        if (requestOrigin == null || requestOrigin.isBlank()) {
            return null;
        }

        // Always allow local development origins (browser file:// and localhost)
        if ("null".equals(requestOrigin) || requestOrigin.startsWith("http://localhost:")
                || requestOrigin.startsWith("http://127.0.0.1:") || requestOrigin.startsWith("https://localhost:")) {
            return requestOrigin;
        }

        // Check against comma-separated whitelist (e.g. https://scamlens.onrender.com,https://user.github.io)
        for (String allowed : allowedOriginsEnv.split(",")) {
            String trimmed = allowed.trim();
            if (!trimmed.isEmpty() && (trimmed.equalsIgnoreCase(requestOrigin) || trimmed.equals("*"))) {
                return requestOrigin;
            }
        }
        return null;
    }

    private static boolean isAiDisabled() {
        String disable = System.getenv("DISABLE_AI");
        if (disable == null || disable.isBlank()) {
            disable = envCache.get("DISABLE_AI");
        }
        if (disable == null) return false;
        String trimmed = disable.trim();
        return "true".equalsIgnoreCase(trimmed) || "1".equals(trimmed);
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
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                String hex = json.substring(i + 1, i + 5);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (Exception ignored) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                    }
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
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                String hex = json.substring(i + 1, i + 5);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (Exception ignored) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                    }
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

    /**
     * Safely sanitize error messages before logging (strips secret patterns and caps length)
     */
    private static String sanitizeErrorMessage(String error) {
        if (error == null || error.isBlank()) return "Unknown error";
        // Strip out any accidental API keys or authorization tokens
        String cleaned = error.replaceAll("(?i)(key|token|auth)=[^&\\s]+", "$1=[REDACTED]")
                              .replaceAll("AQ\\.[0-9A-Za-z-_]{20,}", "[REDACTED_KEY]")
                              .replaceAll("AIza[0-9A-Za-z-_]{30,}", "[REDACTED_KEY]");
        // Truncate if very long
        if (cleaned.length() > 300) {
            cleaned = cleaned.substring(0, 300) + "... [truncated]";
        }
        return cleaned.trim();
    }
}
