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
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

            // Endpoints
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/analyze", new AnalyzeHandler());
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(null);

            String apiKey = getGemmaApiKey();
            String model = getGemmaModel();
            boolean aiDisabled = isAiDisabled();

            System.out.println("==================================================");
            System.out.println(" 🛡️  ScamLens Java Backend is running!");
            System.out.println(" Server Port  : http://localhost:" + port);
            System.out.println(" Web UI       : http://localhost:" + port + "/");
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
     * Static file handler: Serves frontend UI assets (index.html, style.css, app.js) in production
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = "/index.html";
            }

            // Path traversal protection
            if (path.contains("..") || path.contains("\\")) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }

            // Locate file in frontend/ directory or current directory
            Path filePath = Paths.get("frontend" + path);
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                filePath = Paths.get("." + path);
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"File not found\"}");
                return;
            }

            String contentType = "application/octet-stream";
            String lower = filePath.getFileName().toString().toLowerCase();
            if (lower.endsWith(".html")) {
                contentType = "text/html; charset=UTF-8";
            } else if (lower.endsWith(".css")) {
                contentType = "text/css; charset=UTF-8";
            } else if (lower.endsWith(".js")) {
                contentType = "application/javascript; charset=UTF-8";
            } else if (lower.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".ico")) {
                contentType = "image/x-icon";
            }

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.getResponseBody().close();
            }
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
                + "  \"actions\": [\"safe action step 1\", \"safe action step 2\", \"safe action step 3\"],\n"
                + "  \"findings\": [\n"
                + "    {\n"
                + "      \"category\": \"Category name (e.g. Upfront Payment, Urgency / Pressure, Suspicious Link, Unrealistic Offer)\",\n"
                + "      \"quote\": \"EXACT verbatim quote copied directly from the message. MUST be a word-for-word substring from the submitted message text. Never fabricate quotes.\",\n"
                + "      \"explanation\": \"Plain-English explanation of why this specific quote is concerning\",\n"
                + "      \"action\": \"Specific verification action\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"contradictions\": [\"Note any contradictory statements in the message, e.g. claiming no fees while requesting a deposit\"],\n"
                + "  \"uncertainty\": \"Analytical confidence statement highlighting limitations and why this assessment is not 100% definitive\"\n"
                + "}";

        String requestJson = "{"
                + "\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]"
                + ",\"generationConfig\":{\"responseMimeType\":\"application/json\",\"temperature\":0.1,\"maxOutputTokens\":2500}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long elapsed = System.currentTimeMillis() - startTime;

        if (response.statusCode() != 200) {
            String sanitizedBody = sanitizeErrorMessage(response.body());
            throw new RuntimeException("HTTP " + response.statusCode() + " after " + elapsed + "ms: " + sanitizedBody);
        }

        System.out.println("[ScamLens] Gemma API completed successfully in " + elapsed + "ms (HTTP 200).");
        return parseAndValidateGemmaResponse(response.body(), message, model);
    }

    /**
     * Parse and strictly validate AI JSON response on the server.
     * Rejects fabricated quotes, missing required fields, and invalid risk levels.
     */
    static AnalysisResult parseAndValidateGemmaResponse(String apiResponseBody, String originalMessage, String model) {
        // Extract candidate text from Gemini/Gemma REST response
        String text = extractCandidateText(apiResponseBody);
        if (text.isBlank()) {
            if (apiResponseBody != null && apiResponseBody.trim().startsWith("{") && apiResponseBody.contains("\"level\"")) {
                text = apiResponseBody;
            } else {
                throw new ModelValidationException("Gemma API returned empty response candidate");
            }
        }

        // Safely extract single JSON object block (handles fences, surrounding text, malformed syntax)
        String json = extractJsonBlock(text);

        // 1. Strict Risk Level Validation
        String rawLevel = extractStringField(json, "level").toLowerCase().trim();
        if (rawLevel.isBlank()) {
            throw new ModelValidationException("Missing required 'level' field in AI response");
        }

        String level;
        if (rawLevel.equals("high") || rawLevel.contains("critical")) {
            level = "high";
        } else if (rawLevel.equals("medium") || rawLevel.contains("suspicious") || rawLevel.contains("mod")) {
            level = "medium";
        } else if (rawLevel.equals("low") || rawLevel.contains("safe")) {
            level = "low";
        } else {
            throw new ModelValidationException("Invalid risk level value in AI response: '" + rawLevel + "'");
        }

        // 2. Strict Required Fields Validation
        String summary = extractStringField(json, "summary");
        if (summary.isBlank()) {
            throw new ModelValidationException("Missing required 'summary' field in AI response");
        }

        String explanation = extractStringField(json, "explanation");
        if (explanation.isBlank()) {
            throw new ModelValidationException("Missing required 'explanation' field in AI response");
        }

        String label = extractStringField(json, "label");
        if (label.isBlank()) label = "AI Risk Assessment";

        int score = extractIntField(json, "score", -1);
        if (score < 0 || score > 100) {
            throw new ModelValidationException("Missing or invalid 'score' field (must be an integer from 0 to 100): " + score);
        }

        List<String> phrases = extractStringArray(json, "phrases");
        List<String> actions = extractStringArray(json, "actions");
        if (actions.isEmpty()) {
            actions.add("Verify sender identity through independent official channels.");
            actions.add("Never share financial credentials or OTPs.");
        }

        // 3. Strict Evidence Quote Validation against the original message text
        List<Finding> validatedFindings = new ArrayList<>();
        List<String> rawObjList = extractObjectArray(json, "findings");
        int ungroundedCount = 0;

        for (String objJson : rawObjList) {
            String category = extractStringField(objJson, "category");
            String quote = extractStringField(objJson, "quote");
            String exp = extractStringField(objJson, "explanation");
            String act = extractStringField(objJson, "action");
            if (category.isBlank()) category = "Suspicious Indicator";
            if ("null".equalsIgnoreCase(quote) || quote.isBlank()) quote = null;

            boolean grounded = true;
            if (quote != null) {
                grounded = isQuoteInOriginal(originalMessage, quote);
                if (!grounded) {
                    ungroundedCount++;
                    System.err.println("[ScamLens Validation] Detected ungrounded/fabricated quote from AI: '" + quote + "'");
                }
            }

            // Only retain findings with authentic quotes or category-level findings without quotes
            if (quote == null || grounded) {
                validatedFindings.add(new Finding(category, quote, exp, act, grounded));
            }
        }

        // Strict grounding enforcement: if model supplied findings with quotes and ALL were fabricated, reject response
        if (!rawObjList.isEmpty() && validatedFindings.isEmpty() && ungroundedCount > 0) {
            throw new ModelValidationException("All evidence quotes in AI response were fabricated and could not be grounded in message");
        }

        List<String> contradictions = extractStringArray(json, "contradictions");
        String uncertainty = extractStringField(json, "uncertainty");
        if (uncertainty.isBlank()) {
            uncertainty = "Analytical risk assessment based on detected textual patterns. Not a guarantee or calibrated mathematical probability.";
        }

        String engine = "Gemma AI (" + model + ")";
        return new AnalysisResult(level, label, summary, explanation, phrases, actions, score, engine, validatedFindings, contradictions, uncertainty);
    }

    /**
     * Heuristic analysis logic (Rule-based fallback)
     * Performs contextual detection, supports negation ("no registration fee"),
     * detects deceptive contradictions, grounds quotes, and generates explanations strictly based on evidence found.
     */
    static AnalysisResult analyzeMessage(String message) {
        String lower = message.toLowerCase();

        List<String> suspiciousPhrases = new ArrayList<>();
        List<String> evidenceExplanations = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        int score = 10;

        // 1. Contextual Upfront Payment Detection (with Negation Handling)
        boolean hasPayment = hasPaymentDemand(lower);
        boolean hasNegatedPayment = hasExplicitNoFee(lower);

        if (hasPayment && hasNegatedPayment) {
            // Contradiction detected: denies fee but asks for deposit/payment
            String feeQuote = extractNegationQuote(message);
            String payQuote = extractPaymentQuote(message);
            String contraMsg = "Message contains conflicting statements: denies fees ('" + feeQuote + "') but subsequently demands payment ('" + payQuote + "').";
            contradictions.add(contraMsg);
            suspiciousPhrases.add("Contradictory fee statement (Fee denial combined with payment demand)");
            evidenceExplanations.add("Deceptively claims no application fee while simultaneously demanding an upfront deposit or fee.");
            score += 15;

            findings.add(new Finding(
                "Contradictory Fee Statements",
                feeQuote + " ... " + payQuote,
                "The message states that no application fee is required, yet subsequently requests a deposit or payment. Scammers frequently use initial 'no fee' assurances to lower guard before introducing mandatory fees.",
                "Disregard initial assurances of 'no fee' when payment is requested elsewhere. Legitimate employers never require onboarding or security deposits.",
                true
            ));
        }

        if (hasPayment) {
            String payQuote = extractPaymentQuote(message);
            suspiciousPhrases.add("Request for upfront payment, deposit, or fee");
            evidenceExplanations.add("Requests upfront money, deposit, or fees (often disguised as refundable onboarding charges), which is characteristic of advance-fee scams.");
            actions.add("Do NOT send money, UPI transfers, cryptocurrency, or gift cards under any circumstances.");
            score += 45;

            findings.add(new Finding(
                "Upfront Payment / Deposit Demand",
                payQuote.isEmpty() ? null : payQuote,
                "Requests upfront money, deposit, or fees (often disguised as refundable onboarding charges), which is characteristic of advance-fee scams.",
                "Do NOT send money, UPI transfers, cryptocurrency, or gift cards under any circumstances.",
                !payQuote.isEmpty()
            ));
        } else if (hasNegatedPayment) {
            // Explicit fee negation without payment demand
            score = Math.max(5, score - 5);
            String negationQuote = extractNegationQuote(message);
            findings.add(new Finding(
                "Explicit Fee Exemption",
                negationQuote.isEmpty() ? null : negationQuote,
                "The message explicitly states that no application or registration fees are required.",
                "Verify the opportunity directly on the organization's official careers portal.",
                !negationQuote.isEmpty()
            ));
        }

        // 2. Artificial Urgency / Psychological Pressure
        boolean hasUrgency = detectUrgency(lower, suspiciousPhrases);
        if (hasUrgency) {
            String urgencyQuote = extractUrgencyQuote(message);
            evidenceExplanations.add("Uses high urgency or pressure tactics to rush your decision before you have time to verify.");
            actions.add("Do not rush. Legitimate employers allow reasonable time to evaluate opportunities.");
            score += 25;

            findings.add(new Finding(
                "High Urgency / Pressure Tactics",
                urgencyQuote.isEmpty() ? null : urgencyQuote,
                "Uses artificial time pressure or ultimatums to rush your decision before you have time to independently verify the opportunity.",
                "Do not rush. Take time to verify the sender through independent channels.",
                !urgencyQuote.isEmpty()
            ));
        }

        // 3. Unrealistic Earnings / Unsolicited Rewards
        boolean hasUnrealistic = detectUnrealisticOffer(lower, suspiciousPhrases);
        if (hasUnrealistic) {
            String offerQuote = extractUnrealisticQuote(message);
            evidenceExplanations.add("Promises unrealistic compensation, selection without prior interview, or unearned prizes/grants.");
            score += 25;

            findings.add(new Finding(
                "Unrealistic Offer / Selection",
                offerQuote.isEmpty() ? null : offerQuote,
                "Promises unrealistic compensation, selection without prior interview, or unearned prizes/grants.",
                "Research standard compensation for the role and verify the hiring manager on LinkedIn.",
                !offerQuote.isEmpty()
            ));
        }

        // 4. Phishing links / Suspicious domains
        boolean hasSuspiciousLink = detectSuspiciousLinks(lower, suspiciousPhrases);
        if (hasSuspiciousLink) {
            String linkQuote = extractLinkQuote(message);
            evidenceExplanations.add("Contains suspicious, shortened, or unverified URL links commonly associated with credential theft.");
            actions.add("Do not click links or enter passwords/credentials on unverified domains.");
            score += 20;

            findings.add(new Finding(
                "Suspicious Link / Phishing Risk",
                linkQuote.isEmpty() ? null : linkQuote,
                "Contains a shortened, unverified, or suspicious URL commonly associated with credential harvesting or phishing.",
                "Do NOT click the link. Navigate directly to the organization's verified official portal.",
                !linkQuote.isEmpty()
            ));
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

        String uncertainty;
        if (score >= 60) {
            uncertainty = "High-confidence risk indicators identified. While automated text analysis cannot establish legal proof of fraud, the detected combination of demands strongly aligns with known advance-fee and phishing scam patterns.";
        } else if (score >= 35) {
            uncertainty = "Moderate-confidence indicators detected. Some legitimate organizations occasionally use urgent deadlines or third-party links; independent verification is strongly advised before taking action.";
        } else {
            uncertainty = "Low risk indicators detected in text. Automated analysis cannot verify sender email headers, offline identity, or sophisticated multi-stage scams that introduce fees in subsequent messages.";
        }

        return new AnalysisResult(level, label, summary, explanation, suspiciousPhrases, actions, score, "Rule Heuristics", findings, contradictions, uncertainty);
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

    static class ModelValidationException extends RuntimeException {
        public ModelValidationException(String message) {
            super(message);
        }
    }

    /**
     * Structured finding representing grounded evidence, explanation, and action
     */
    static class Finding {
        String category;
        String quote;
        String explanation;
        String action;
        boolean isGrounded;

        Finding(String category, String quote, String explanation, String action, boolean isGrounded) {
            this.category = category;
            this.quote = quote;
            this.explanation = explanation;
            this.action = action;
            this.isGrounded = isGrounded;
        }

        public String toJson() {
            return "{"
                    + "\"category\":" + Server.quote(category) + ","
                    + "\"quote\":" + (quote != null ? Server.quote(quote) : "null") + ","
                    + "\"explanation\":" + Server.quote(explanation) + ","
                    + "\"action\":" + Server.quote(action) + ","
                    + "\"isGrounded\":" + isGrounded
                    + "}";
        }
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
        List<Finding> findings;
        List<String> contradictions;
        String uncertainty;

        AnalysisResult(String level, String label, String summary, String explanation,
                       List<String> suspiciousPhrases, List<String> actions, int score, String engine) {
            this(level, label, summary, explanation, suspiciousPhrases, actions, score, engine,
                 new ArrayList<>(), new ArrayList<>(), "");
        }

        AnalysisResult(String level, String label, String summary, String explanation,
                       List<String> suspiciousPhrases, List<String> actions, int score, String engine,
                       List<Finding> findings, List<String> contradictions, String uncertainty) {
            this.level = level;
            this.label = label;
            this.summary = summary;
            this.explanation = explanation;
            this.suspiciousPhrases = suspiciousPhrases != null ? suspiciousPhrases : new ArrayList<>();
            this.actions = actions != null ? actions : new ArrayList<>();
            this.score = score;
            this.engine = engine;
            this.findings = findings != null ? findings : new ArrayList<>();
            this.contradictions = contradictions != null ? contradictions : new ArrayList<>();
            this.uncertainty = uncertainty != null ? uncertainty : "";
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
            sb.append("],");

            // Findings array
            sb.append("\"findings\":[");
            for (int i = 0; i < findings.size(); i++) {
                sb.append(findings.get(i).toJson());
                if (i < findings.size() - 1) sb.append(",");
            }
            sb.append("],");

            // Contradictions array
            sb.append("\"contradictions\":[");
            for (int i = 0; i < contradictions.size(); i++) {
                sb.append(quote(contradictions.get(i)));
                if (i < contradictions.size() - 1) sb.append(",");
            }
            sb.append("],");

            // Uncertainty
            sb.append("\"uncertainty\":").append(quote(uncertainty));

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

    static String extractCandidateText(String apiResponseBody) {
        if (apiResponseBody == null || apiResponseBody.isBlank()) return "";

        // Attempt to extract candidates array from response
        List<String> candidates = extractObjectArray(apiResponseBody, "candidates");
        String candidateContent = candidates.isEmpty() ? apiResponseBody : candidates.get(0);
        List<String> parts = extractObjectArray(candidateContent, "parts");

        if (!parts.isEmpty()) {
            // First priority: collect text from non-thought parts (Gemini/Gemma reasoning models return thought parts)
            StringBuilder nonThoughtText = new StringBuilder();
            for (String part : parts) {
                boolean isThought = part.contains("\"thought\": true") || part.contains("\"thought\":true");
                if (!isThought) {
                    String partText = extractStringField(part, "text");
                    if (!partText.isBlank()) {
                        if (nonThoughtText.length() > 0) nonThoughtText.append("\n");
                        nonThoughtText.append(partText);
                    }
                }
            }
            if (nonThoughtText.length() > 0) {
                return nonThoughtText.toString().trim();
            }

            // Fallback: if all parts had thought flag, look for any part containing a JSON object
            for (String part : parts) {
                String partText = extractStringField(part, "text");
                if (partText.contains("{") && partText.contains("}")) {
                    return partText.trim();
                }
            }

            // Fallback: return the last part text
            String lastText = extractStringField(parts.get(parts.size() - 1), "text");
            if (!lastText.isBlank()) return lastText.trim();
        }

        // Direct fallback: if no "parts" array detected, extract the first "text" field
        return extractStringField(apiResponseBody, "text");
    }

    static String extractJsonBlock(String text) {
        if (text == null || text.isBlank()) {
            throw new ModelValidationException("Empty model output candidate");
        }

        String cleaned = text.trim();

        // 1. If markdown code fences are present, try extracting within the fence first
        if (cleaned.contains("```")) {
            int firstFence = cleaned.indexOf("```");
            int closingFence = cleaned.indexOf("```", firstFence + 3);
            if (closingFence != -1) {
                String insideFence = cleaned.substring(firstFence + 3, closingFence).trim();
                if (insideFence.toLowerCase().startsWith("json")) {
                    insideFence = insideFence.substring(4).trim();
                }
                if (insideFence.contains("\"level\"")) {
                    cleaned = insideFence;
                }
            }
        }

        // 2. Find the root JSON object enclosing "level"
        int levelIdx = cleaned.indexOf("\"level\"");
        if (levelIdx == -1) {
            // Fallback: look for generic outermost braces
            int s = cleaned.indexOf('{');
            int e = cleaned.lastIndexOf('}');
            if (s == -1 || e == -1 || e <= s) {
                throw new ModelValidationException("Could not locate JSON block in Gemma output: " + text);
            }
            return validateJsonObject(cleaned.substring(s, e + 1).trim());
        }

        // Check for multiple ambiguous root objects containing "level"
        int secondLevelIdx = cleaned.indexOf("\"level\"", levelIdx + 7);
        if (secondLevelIdx != -1) {
            String obj1 = findEnclosingObjectAt(cleaned, levelIdx);
            String obj2 = findEnclosingObjectAt(cleaned, secondLevelIdx);
            if (obj1 != null && obj2 != null && !obj1.equals(obj2)) {
                throw new ModelValidationException("Ambiguous JSON: multiple distinct root objects containing 'level'");
            }
        }

        String result = findEnclosingObjectAt(cleaned, levelIdx);
        if (result == null) {
            throw new ModelValidationException("Could not extract balanced JSON object containing 'level'");
        }
        return validateJsonObject(result);
    }

    private static String findEnclosingObjectAt(String text, int targetPos) {
        for (int s = targetPos; s >= 0; s--) {
            if (text.charAt(s) == '{') {
                int end = findMatchingCloseBrace(text, s);
                if (end != -1 && end >= targetPos) {
                    return text.substring(s, end + 1).trim();
                }
            }
        }
        return null;
    }

    private static int findMatchingCloseBrace(String text, int start) {
        int depth = 0;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                    if (depth < 0) {
                        return -1;
                    }
                }
            }
        }
        return -1;
    }

    private static String validateJsonObject(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ModelValidationException("Malformed JSON: object must start with '{' and end with '}'");
        }
        int close = findMatchingCloseBrace(json, 0);
        if (close != json.length() - 1) {
            throw new ModelValidationException("Malformed JSON: unbalanced braces or trailing characters");
        }
        return json;
    }

    static String extractStringField(String json, String field) {
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

    static int extractIntField(String json, String field, int defaultValue) {
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

    static List<String> extractStringArray(String json, String field) {
        List<String> list = new ArrayList<>();
        String target = "\"" + field + "\"";
        int idx = json.indexOf(target);
        if (idx == -1) return list;

        int bracketStart = json.indexOf('[', idx);
        if (bracketStart == -1) return list;

        int bracketEnd = -1;
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = bracketStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && c == ']') {
                bracketEnd = i;
                break;
            }
        }
        if (bracketEnd == -1) return list;

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        inQuotes = false;
        StringBuilder sb = new StringBuilder();
        escaped = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
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

    static List<String> extractObjectArray(String json, String field) {
        List<String> list = new ArrayList<>();
        String target = "\"" + field + "\"";
        int idx = json.indexOf(target);
        if (idx == -1) return list;

        int bracketStart = json.indexOf('[', idx);
        if (bracketStart == -1) return list;

        int depth = 0;
        int objStart = -1;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = bracketStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) {
                        objStart = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        list.add(json.substring(objStart, i + 1));
                        objStart = -1;
                    }
                } else if (c == ']' && depth == 0) {
                    break;
                }
            }
        }
        return list;
    }

    static boolean isQuoteInOriginal(String original, String quote) {
        if (original == null || quote == null) return false;
        String normOrig = original.toLowerCase().replaceAll("\\s+", " ").trim();
        String normQuote = quote.toLowerCase().replaceAll("\\s+", " ").trim();
        if (normQuote.isEmpty()) return false;
        return normOrig.contains(normQuote);
    }

    static String extractPaymentQuote(String original) {
        if (original == null) return "";
        String[] clauses = original.split("[.!?;\\n]+");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.isEmpty() || isNegatedPaymentClause(lower)) continue;
            if (lower.matches(".*\\b(pay|send|transfer|wire|remit)\\b.*\\b(fee|fees|deposit|deposits|money|amount|funds|\\$|\\u20B9|rs|inr|upi|crypto|gift card).*")
                    || lower.matches(".*\\b(registration|processing|application|onboarding|equipment|laptop|verification|refundable)\\s+(security\\s+)?(fee|fees|deposit|deposits)\\b.*")
                    || lower.matches(".*\\b(fee|deposit)\\s+(is|of)\\s+(\\$|\\u20B9|rs|inr|[0-9]+).*")
                    || lower.matches(".*\\b(pay|send)\\s+(\\$|\\u20B9|rs|inr|[0-9]+).*")
                    || lower.matches(".*\\b(via|through|using)\\s+(upi|crypto|bitcoin|gift card).*")) {
                return trimmed;
            }
        }
        return "";
    }

    static String extractNegationQuote(String original) {
        if (original == null) return "";
        String[] clauses = original.split("[.!?;\\n]+");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) continue;
            if (isNegatedPaymentClause(trimmed.toLowerCase())) {
                return trimmed;
            }
        }
        return "";
    }

    static String extractUrgencyQuote(String original) {
        if (original == null) return "";
        String[] clauses = original.split("[.!?;\\n]+");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.isEmpty()) continue;
            if (lower.matches(".*\\bwithin\\s+([0-9]+|one|two|three|half\\s+an?)\\s*(minute|min|second|sec|hour|hr)s?\\b.*")
                    || lower.contains("immediately") || lower.contains("urgently") || lower.contains("act now")
                    || lower.contains("act fast") || lower.contains("today only") || lower.contains("expires tonight")
                    || lower.contains("avoid losing") || lower.contains("will expire") || lower.contains("immediate action required")) {
                return trimmed;
            }
        }
        return "";
    }

    static String extractLinkQuote(String original) {
        if (original == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(https?://[^\\s<>\"'\\]]+|t\\.me/[^\\s<>\"'\\]]+|wa\\.me/[^\\s<>\"'\\]]+)").matcher(original);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    static String extractUnrealisticQuote(String original) {
        if (original == null) return "";
        String[] clauses = original.split("[.!?;\\n]+");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.isEmpty()) continue;
            if (lower.contains("no prior interview") || lower.contains("no interview needed")
                    || lower.contains("selected without interview") || lower.contains("won the lottery")
                    || lower.contains("$45/hour") || lower.contains("$800/week") || lower.contains("winner")) {
                return trimmed;
            }
        }
        return "";
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
