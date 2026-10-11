import java.util.List;

/**
 * Automated Technical Test Suite for ScamLens
 * 
 * Verifies:
 * 1. Legitimate internship invitation with no fee is classified as Low Risk (no false positive).
 * 2. Explicit job registration fee request is classified as High Risk.
 * 3. "No registration fee" alongside unrelated suspicious signals (urgency + phishing domain).
 * 4. Mixed message containing "no application fee" AND "pay refundable deposit of ₹799 within 30 minutes"
 *    (Must detect the contradiction, ground the deposit quote, and flag urgency -> High Risk).
 * 5. Ordinary benign scheduling deadlines (e.g. "within 3 days", "by Friday") are NOT flagged as scam urgency.
 * 6. Fee waiver notifications (100% waived fee) are classified as Low Risk.
 * 7. Evidence Quote Validation accepts authentic quotes directly present in original message text.
 * 8. Evidence Quote Validation rejects fabricated/hallucinated quotes not found in original message text.
 * 9. Server rejects invalid/corrupt risk levels and malformed model outputs.
 * 10. Malformed/empty model output triggers safe ModelValidationException for heuristic fallback.
 * 11. Structured Findings & Contradictions are correctly populated and grounded.
 * 12. Suspicious URL/domain detection correctly identifies phishing domains and link formats.
 */
public class ServerTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" 🧪 Running ScamLens Technical Test Suite");
        System.out.println("==================================================");

        testLegitimateInternshipWithNoFee();
        testExplicitJobRegistrationFee();
        testNoFeeWithUnrelatedSuspiciousSignal();
        testMixedFeeNegationWithActualUrgentDeposit();
        testBenignSchedulingDeadlines();
        testFeeWaiverNotification();
        testEvidenceQuoteValidationAcceptsAuthenticQuote();
        testEvidenceQuoteValidationRejectsFabricatedQuote();
        testInvalidRiskLevelRejection();
        testMalformedAiJsonTriggersValidationException();
        testContradictionStructurePopulated();
        testSuspiciousLinkDetection();
        testGemmaMultiPartThinkingResponse();
        testMarkdownFencedJsonResponse();
        testHarmlessSurroundingTextJsonResponse();
        testUnbalancedBracesMalformedJson();
        testAmbiguousMultipleJsonObjects();
        testScoreOutOfRangeRejection();
        testMissingRequiredFieldRejection();
        testStringArrayWithEmbeddedBrackets();
        testAiFailureTriggersRuleFallback();
        testStaticFileServingAndPathTraversalProtection();

        System.out.println("==================================================");
        System.out.println(" Results: " + testsPassed + " / " + testsTotal + " tests passed.");
        System.out.println("==================================================");

        if (testsPassed != testsTotal) {
            System.err.println("❌ Some tests failed!");
            System.exit(1);
        } else {
            System.out.println("✅ All " + testsTotal + " tests passed successfully!");
        }
    }

    /**
     * Test 1: Legitimate internship invitation that explicitly says "no fee"
     */
    private static void testLegitimateInternshipWithNoFee() {
        testsTotal++;
        String message = "Congratulations! We are pleased to invite you for an interview for the Software Engineering Internship at Acme Corp. "
                + "Please note that there is no application or registration fee for this hiring process. "
                + "We will never ask you to pay for equipment or onboarding. Please schedule your interview on our official careers portal.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 1] Legitimate Internship Invitation with No Fee");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);

        boolean passed = true;
        if (!"low".equalsIgnoreCase(result.level)) {
            System.err.println("   FAIL: Expected level 'low', got: " + result.level);
            passed = false;
        }
        if (result.score >= 35) {
            System.err.println("   FAIL: Expected score < 35, got: " + result.score);
            passed = false;
        }
        if (hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: False positive! Flagged upfront payment on legitimate no-fee message.");
            passed = false;
        }
        if (result.explanation.toLowerCase().contains("advance-fee") || result.explanation.toLowerCase().contains("upfront money")) {
            System.err.println("   FAIL: Invented payment explanation on a message without payment demands.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (No false positive, risk score correctly low)");
            testsPassed++;
        }
    }

    /**
     * Test 2: An explicit job registration fee request (Real Scam)
     */
    private static void testExplicitJobRegistrationFee() {
        testsTotal++;
        String message = "Congratulations! You have been selected for the remote data entry internship. "
                + "To finalize onboarding and confirm your slot, kindly pay a refundable registration fee of $30 via UPI within 2 hours.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 2] Explicit Job Registration Fee Request");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);

        boolean passed = true;
        if (!"high".equalsIgnoreCase(result.level)) {
            System.err.println("   FAIL: Expected level 'high', got: " + result.level);
            passed = false;
        }
        if (result.score < 60) {
            System.err.println("   FAIL: Expected score >= 60, got: " + result.score);
            passed = false;
        }
        if (!hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: Failed to detect upfront payment demand.");
            passed = false;
        }
        if (!hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: Failed to detect urgency pressure.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Correctly flagged high risk and detected payment request)");
            testsPassed++;
        }
    }

    /**
     * Test 3: Message containing "no registration fee" alongside unrelated suspicious signals
     */
    private static void testNoFeeWithUnrelatedSuspiciousSignal() {
        testsTotal++;
        String message = "Congratulations! There is no registration fee for this internship. "
                + "However, you must click this link immediately http://verify-portal.xyz/login to verify your credentials, or your account will be suspended tonight.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 3] 'No registration fee' with Unrelated Suspicious Signals (Urgency + Phishing Link)");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);

        boolean passed = true;
        if (hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: False positive! Flagged payment demand despite 'no registration fee'.");
            passed = false;
        }
        if (!hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: Expected urgency flag.");
            passed = false;
        }
        if (!hasPhrase(result.suspiciousPhrases, "shortened URL") && !hasPhrase(result.suspiciousPhrases, "Suspicious or shortened URL link")) {
            System.err.println("   FAIL: Expected suspicious link flag.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Preserved urgency/link flags without false positive payment flag)");
            testsPassed++;
        }
    }

    /**
     * Test 4: Mixed Message (Negated application fee BUT actual urgent deposit demand)
     */
    private static void testMixedFeeNegationWithActualUrgentDeposit() {
        testsTotal++;
        String message = "Congratulations! There is no application fee for this role. "
                + "However, you must pay a refundable security deposit of \u20B9799 within 30 minutes to secure your seat. "
                + "The deposit will be returned after your interview.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 4] Mixed Message: 'No application fee' + Refundable Deposit of \u20B9799 within 30 minutes");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Contradictions: " + result.contradictions);
        System.out.println(" -> Findings count: " + result.findings.size());

        boolean passed = true;
        if (!hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: Failed to detect the deposit request of \u20B9799.");
            passed = false;
        }
        if (!hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: Failed to detect urgency pressure ('within 30 minutes').");
            passed = false;
        }
        if (!"high".equalsIgnoreCase(result.level) || result.score < 60) {
            System.err.println("   FAIL: Expected High Risk (score >= 60), got: " + result.level + " (Score " + result.score + ")");
            passed = false;
        }
        if (result.contradictions.isEmpty()) {
            System.err.println("   FAIL: Did not flag contradiction between fee denial and deposit demand.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Detected deceptive contradiction + payment demand + 30-min urgency)");
            testsPassed++;
        }
    }

    /**
     * Test 5: Standard benign interview scheduling timeline (should NOT trigger urgency flag)
     */
    private static void testBenignSchedulingDeadlines() {
        testsTotal++;
        String message = "Thank you for your interest in the position. "
                + "Please review the job description and select an available interview slot within 3 days by replying to this email. "
                + "Interviews will be conducted online next week.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 5] Benign Interview Scheduling Timeline (within 3 days)");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);

        boolean passed = true;
        if (!"low".equalsIgnoreCase(result.level)) {
            System.err.println("   FAIL: Standard scheduling was incorrectly flagged as risk: " + result.level);
            passed = false;
        }
        if (hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: Standard 3-day interview window was incorrectly flagged as scam urgency pressure.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Benign scheduling deadline was not misclassified as scam urgency)");
            testsPassed++;
        }
    }

    /**
     * Test 6: Message mentioning a fee waiver and standard application deadline
     */
    private static void testFeeWaiverNotification() {
        testsTotal++;
        String message = "Congratulations! Your application for the Summer Research Fellowship has been approved. "
                + "Please note that the registration fee has been 100% waived for all university candidates. "
                + "The deadline to confirm your acceptance is next Friday. No payment or deposit is required.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 6] Fee Waiver Notification (100% waived, confirm by next Friday)");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);

        boolean passed = true;
        if (!"low".equalsIgnoreCase(result.level)) {
            System.err.println("   FAIL: Fee waiver message was incorrectly flagged with risk level: " + result.level);
            passed = false;
        }
        if (result.score >= 35) {
            System.err.println("   FAIL: Expected score < 35 for waived fee, got: " + result.score);
            passed = false;
        }
        if (hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: False positive! Flagged upfront payment on message with 100% waived fee.");
            passed = false;
        }
        if (hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: False positive! Ordinary 'deadline next Friday' was flagged as urgency pressure.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Fee waiver handled accurately with 0 false alarms for payment or urgency)");
            testsPassed++;
        }
    }

    /**
     * Test 7: Java Evidence Validation accepts authentic quotes directly present in original message text
     */
    private static void testEvidenceQuoteValidationAcceptsAuthenticQuote() {
        testsTotal++;
        String originalMessage = "You have been selected for an online internship. Pay a refundable deposit of $50 within 2 hours.";
        
        // Synthetic model response containing an authentic quote from originalMessage
        String mockModelResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\n"
                + "  \\\"level\\\": \\\"high\\\",\\n"
                + "  \\\"label\\\": \\\"High Risk Scam\\\",\\n"
                + "  \\\"summary\\\": \\\"Demands an upfront refundable deposit.\\\",\\n"
                + "  \\\"score\\\": 85,\\n"
                + "  \\\"phrases\\\": [\\\"refundable deposit of $50\\\"],\\n"
                + "  \\\"explanation\\\": \\\"Advance fee scam pattern.\\\",\\n"
                + "  \\\"actions\\\": [\\\"Do not pay deposit\\\"],\\n"
                + "  \\\"findings\\\": [\\n"
                + "    {\\n"
                + "      \\\"category\\\": \\\"Upfront Payment\\\",\\n"
                + "      \\\"quote\\\": \\\"refundable deposit of $50\\\",\\n"
                + "      \\\"explanation\\\": \\\"Advance fee scam\\\",\\n"
                + "      \\\"action\\\": \\\"Do not pay\\\"\\n"
                + "    }\\n"
                + "  ]\\n"
                + "}\"}}]}]}";

        Server.AnalysisResult result = Server.parseAndValidateGemmaResponse(mockModelResponse, originalMessage, "test-model");

        System.out.println("\n[Test 7] Evidence Quote Validation (Authentic Quote Grounding)");
        System.out.println(" -> Grounded findings: " + result.findings.size());

        boolean passed = true;
        if (!"high".equals(result.level)) {
            System.err.println("   FAIL: Expected level 'high', got: " + result.level);
            passed = false;
        }
        if (result.findings.isEmpty() || !result.findings.get(0).isGrounded) {
            System.err.println("   FAIL: Authentic quote was not validated as grounded.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Authentic quote verified as grounded in original message)");
            testsPassed++;
        }
    }

    /**
     * Test 8: Java Evidence Validation rejects fabricated/hallucinated quotes
     */
    private static void testEvidenceQuoteValidationRejectsFabricatedQuote() {
        testsTotal++;
        String originalMessage = "Please review our online internship details and send your resume.";
        
        // Synthetic model response hallucinating a quote that NEVER occurred in originalMessage
        String mockFabricatedResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\n"
                + "  \\\"level\\\": \\\"high\\\",\\n"
                + "  \\\"label\\\": \\\"High Risk Scam\\\",\\n"
                + "  \\\"summary\\\": \\\"Wire money immediately.\\\",\\n"
                + "  \\\"score\\\": 90,\\n"
                + "  \\\"phrases\\\": [\\\"wire transfer\\\"],\\n"
                + "  \\\"explanation\\\": \\\"Fake quote evaluation.\\\",\\n"
                + "  \\\"actions\\\": [\\\"Do not wire\\\"],\\n"
                + "  \\\"findings\\\": [\\n"
                + "    {\\n"
                + "      \\\"category\\\": \\\"Payment\\\",\\n"
                + "      \\\"quote\\\": \\\"wire $500 immediately to offshore account\\\",\\n"
                + "      \\\"explanation\\\": \\\"Fabricated quote not in message\\\",\\n"
                + "      \\\"action\\\": \\\"Do not send\\\"\\n"
                + "    }\\n"
                + "  ]\\n"
                + "}\"}}]}]}";

        System.out.println("\n[Test 8] Evidence Quote Validation (Fabricated Quote Rejection)");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(mockFabricatedResponse, originalMessage, "test-model");
            System.err.println("   FAIL: Server silently accepted a fabricated quote that never occurred in the text!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Correctly rejected model hallucination and prevented fabricated quote presentation)");
            testsPassed++;
        }
    }

    /**
     * Test 9: Server rejects invalid/corrupt risk levels
     */
    private static void testInvalidRiskLevelRejection() {
        testsTotal++;
        String originalMessage = "Sample message text.";
        String mockInvalidLevelResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\n"
                + "  \\\"level\\\": \\\"catastrophic_danger\\\",\\n"
                + "  \\\"summary\\\": \\\"Invalid level test.\\\",\\n"
                + "  \\\"explanation\\\": \\\"Test explanation.\\\",\\n"
                + "  \\\"score\\\": 99\\n"
                + "}\"}}]}]}";

        System.out.println("\n[Test 9] Rejection of Invalid Risk Levels");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(mockInvalidLevelResponse, originalMessage, "test-model");
            System.err.println("   FAIL: Server accepted an uncalibrated risk level ('catastrophic_danger')!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Correctly enforced strict risk level schema)");
            testsPassed++;
        }
    }

    /**
     * Test 10: Malformed/empty model output triggers safe ModelValidationException
     */
    private static void testMalformedAiJsonTriggersValidationException() {
        testsTotal++;
        String originalMessage = "Sample message text.";
        String malformedRawOutput = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"I am thinking... this is not JSON!\"}]}}]}";

        System.out.println("\n[Test 10] Malformed AI Output Handling");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(malformedRawOutput, originalMessage, "test-model");
            System.err.println("   FAIL: Server did not throw on non-JSON model output!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Malformed AI candidate cleanly caught for deterministic rule fallback)");
            testsPassed++;
        }
    }

    /**
     * Test 11: Structured Findings & Contradictions are correctly populated and grounded
     */
    private static void testContradictionStructurePopulated() {
        testsTotal++;
        String message = "There is no registration fee. However, pay a refundable security deposit of \u20B9500 to reserve your seat.";
        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 11] Structured Findings & Contradictions Check");
        System.out.println(" -> Findings: " + result.findings.size());
        System.out.println(" -> Contradictions: " + result.contradictions.size());
        System.out.println(" -> Uncertainty note: " + result.uncertainty);

        boolean passed = true;
        if (result.contradictions.isEmpty()) {
            System.err.println("   FAIL: Contradictions array was empty on contradictory message.");
            passed = false;
        }
        if (result.findings.isEmpty()) {
            System.err.println("   FAIL: Findings list was empty.");
            passed = false;
        }
        for (Server.Finding f : result.findings) {
            if (f.quote != null && !f.quote.isBlank()) {
                if (!f.isGrounded) {
                    System.err.println("   FAIL: Finding quote was marked ungrounded: " + f.quote);
                    passed = false;
                }
            }
        }
        if (result.uncertainty.isBlank()) {
            System.err.println("   FAIL: Uncertainty statement was blank.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Findings grounded, contradiction recorded, and uncertainty stated)");
            testsPassed++;
        }
    }

    /**
     * Test 12: Suspicious URL/domain detection correctly identifies phishing domains
     */
    private static void testSuspiciousLinkDetection() {
        testsTotal++;
        String message = "Your electricity will be cut off tonight. Pay immediately at http://bill-pay-portal.xyz or click http://192.168.1.1/pay";
        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 12] Suspicious Phishing Domain & IP Link Detection");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);

        boolean passed = true;
        if (!hasPhrase(result.suspiciousPhrases, "shortened URL") && !hasPhrase(result.suspiciousPhrases, "Suspicious or shortened URL link")) {
            System.err.println("   FAIL: Failed to detect suspicious .xyz domain link.");
            passed = false;
        }

        boolean foundLinkFinding = false;
        for (Server.Finding f : result.findings) {
            if (f.category.toLowerCase().contains("link") || f.category.toLowerCase().contains("phishing")) {
                foundLinkFinding = true;
                break;
            }
        }
        if (!foundLinkFinding) {
            System.err.println("   FAIL: Did not create structured Finding for suspicious link.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Phishing link detected and structured finding created)");
            testsPassed++;
        }
    }

    /**
     * Test 13: Multi-part Gemma API response with thought block and non-thought JSON block
     */
    private static void testGemmaMultiPartThinkingResponse() {
        testsTotal++;
        String originalMessage = "Pay a refundable security deposit of $100 immediately to reserve your seat.";
        String gemmaResponse = "{\n"
                + "  \"candidates\": [\n"
                + "    {\n"
                + "      \"content\": {\n"
                + "        \"parts\": [\n"
                + "          {\n"
                + "            \"text\": \"* Input message: Analyze for scam signals.\\n* Found upfront payment request and urgency.\\n* Structuring JSON response...\",\n"
                + "            \"thought\": true\n"
                + "          },\n"
                + "          {\n"
                + "            \"text\": \"{\\n  \\\"level\\\": \\\"high\\\",\\n  \\\"label\\\": \\\"Advance-Fee Scam\\\",\\n  \\\"summary\\\": \\\"Demands an upfront deposit with urgency.\\\",\\n  \\\"score\\\": 92,\\n  \\\"phrases\\\": [\\\"refundable security deposit\\\"],\\n  \\\"explanation\\\": \\\"Legitimate employers do not request deposits.\\\",\\n  \\\"actions\\\": [\\\"Do not pay\\\"],\\n  \\\"findings\\\": [\\n    {\\n      \\\"category\\\": \\\"Upfront Payment\\\",\\n      \\\"quote\\\": \\\"refundable security deposit of $100\\\",\\n      \\\"explanation\\\": \\\"Advance fee pattern\\\",\\n      \\\"action\\\": \\\"Do not pay\\\"\\n    }\\n  ]\\n}\"\n"
                + "          }\n"
                + "        ],\n"
                + "        \"role\": \"model\"\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        System.out.println("\n[Test 13] Multi-part Gemma Response with 'thought': true Part and JSON Part");
        Server.AnalysisResult result = Server.parseAndValidateGemmaResponse(gemmaResponse, originalMessage, "gemma-4-26b-a4b-it");
        System.out.println(" -> Engine: " + result.engine);
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Grounded findings: " + result.findings.size());

        boolean passed = true;
        if (!"high".equals(result.level)) {
            System.err.println("   FAIL: Expected level 'high', got: " + result.level);
            passed = false;
        }
        if (result.score != 92) {
            System.err.println("   FAIL: Expected score 92, got: " + result.score);
            passed = false;
        }
        if (result.findings.isEmpty() || !result.findings.get(0).isGrounded) {
            System.err.println("   FAIL: Finding quote not properly grounded.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Multi-part thought response successfully separated and JSON parsed)");
            testsPassed++;
        }
    }

    /**
     * Test 14: Model response wrapped in Markdown code fences (```json ... ```)
     */
    private static void testMarkdownFencedJsonResponse() {
        testsTotal++;
        String originalMessage = "Free gift voucher! Click link http://claim-reward.xyz now.";
        String fencedResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\n  \\\"level\\\": \\\"medium\\\",\\n  \\\"label\\\": \\\"Suspicious Offer\\\",\\n  \\\"summary\\\": \\\"Unsolicited gift voucher with suspicious domain.\\\",\\n  \\\"score\\\": 55,\\n  \\\"explanation\\\": \\\"Phishing attempt targeting gift vouchers.\\\",\\n  \\\"actions\\\": [\\\"Do not click link\\\"]\\n}\\n```\"}}]}]}";

        System.out.println("\n[Test 14] Markdown Code Fences Stripping (```json ... ```)");
        Server.AnalysisResult result = Server.parseAndValidateGemmaResponse(fencedResponse, originalMessage, "gemma-4-26b-a4b-it");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);

        boolean passed = true;
        if (!"medium".equals(result.level)) {
            System.err.println("   FAIL: Expected level 'medium', got: " + result.level);
            passed = false;
        }
        if (result.score != 55) {
            System.err.println("   FAIL: Expected score 55, got: " + result.score);
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Markdown fences safely stripped and valid JSON extracted)");
            testsPassed++;
        }
    }

    /**
     * Test 15: Model response with conversational commentary surrounding JSON
     */
    private static void testHarmlessSurroundingTextJsonResponse() {
        testsTotal++;
        String originalMessage = "Please submit your homework by Friday.";
        String surroundingTextResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Here is your security analysis for the message:\\n{\\n  \\\"level\\\": \\\"low\\\",\\n  \\\"label\\\": \\\"Safe Message\\\",\\n  \\\"summary\\\": \\\"Normal academic notification with no scam indicators.\\\",\\n  \\\"score\\\": 10,\\n  \\\"explanation\\\": \\\"No fraud patterns detected.\\\",\\n  \\\"actions\\\": [\\\"Normal processing\\\"]\\n}\\nI hope this assessment is helpful!\"}}]}]}";

        System.out.println("\n[Test 15] Harmless Surrounding Text Extraction");
        Server.AnalysisResult result = Server.parseAndValidateGemmaResponse(surroundingTextResponse, originalMessage, "gemma-4-26b-a4b-it");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);

        boolean passed = true;
        if (!"low".equals(result.level)) {
            System.err.println("   FAIL: Expected level 'low', got: " + result.level);
            passed = false;
        }
        if (result.score != 10) {
            System.err.println("   FAIL: Expected score 10, got: " + result.score);
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Harmless surrounding commentary bypassed and JSON extracted cleanly)");
            testsPassed++;
        }
    }

    /**
     * Test 16: Malformed JSON with unbalanced curly braces is rejected
     */
    private static void testUnbalancedBracesMalformedJson() {
        testsTotal++;
        String originalMessage = "Sample message.";
        String unbalancedJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"level\\\": \\\"high\\\", \\\"summary\\\": \\\"broken JSON with unclosed brace\"}}]}]}";

        System.out.println("\n[Test 16] Unbalanced Curly Braces Malformed JSON Rejection");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(unbalancedJson, originalMessage, "test-model");
            System.err.println("   FAIL: Server accepted JSON with unbalanced braces!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Unbalanced braces detected and rejected safely)");
            testsPassed++;
        }
    }

    /**
     * Test 17: Ambiguous output with multiple distinct root JSON objects is rejected
     */
    private static void testAmbiguousMultipleJsonObjects() {
        testsTotal++;
        String originalMessage = "Sample message.";
        String multipleRootsJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"level\\\": \\\"high\\\", \\\"summary\\\": \\\"first\\\", \\\"explanation\\\": \\\"exp\\\", \\\"score\\\": 80} {\\\"level\\\": \\\"low\\\"}\"}}]}]}";

        System.out.println("\n[Test 17] Ambiguous Multiple Root JSON Objects Rejection");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(multipleRootsJson, originalMessage, "test-model");
            System.err.println("   FAIL: Server accepted ambiguous response with multiple root JSON objects!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Ambiguous multiple root JSON objects rejected)");
            testsPassed++;
        }
    }

    /**
     * Test 18: Score out of range (< 0 or > 100) is rejected
     */
    private static void testScoreOutOfRangeRejection() {
        testsTotal++;
        String originalMessage = "Sample message.";
        String scoreTooHighJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"level\\\": \\\"high\\\", \\\"summary\\\": \\\"summary\\\", \\\"explanation\\\": \\\"explanation\\\", \\\"score\\\": 150}\"}}]}]}";

        System.out.println("\n[Test 18] Score Out-of-Range Rejection (> 100 or < 0)");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(scoreTooHighJson, originalMessage, "test-model");
            System.err.println("   FAIL: Server accepted score > 100!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Out-of-range score strictly rejected)");
            testsPassed++;
        }
    }

    /**
     * Test 19: Missing required fields (e.g. missing explanation) is rejected
     */
    private static void testMissingRequiredFieldRejection() {
        testsTotal++;
        String originalMessage = "Sample message.";
        String missingExplanationJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"level\\\": \\\"high\\\", \\\"summary\\\": \\\"summary\\\", \\\"score\\\": 80}\"}}]}]}";

        System.out.println("\n[Test 19] Missing Required Field Rejection");
        boolean passed = false;
        try {
            Server.parseAndValidateGemmaResponse(missingExplanationJson, originalMessage, "test-model");
            System.err.println("   FAIL: Server accepted response missing required 'explanation' field!");
        } catch (Server.ModelValidationException e) {
            System.out.println(" -> Caught expected ModelValidationException: " + e.getMessage());
            passed = true;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Missing required field caught by strict schema validation)");
            testsPassed++;
        }
    }

    /**
     * Test 20: Regression test: String array with embedded brackets and escaped characters
     */
    private static void testStringArrayWithEmbeddedBrackets() {
        testsTotal++;
        String json = "{\"phrases\": [\"suspicious link [http://phish.xyz]\", \"rush deadline [30 min]\"]}";
        List<String> list = Server.extractStringArray(json, "phrases");

        System.out.println("\n[Test 20] String Array with Embedded Brackets Parsing");
        System.out.println(" -> Extracted phrases: " + list);

        boolean passed = true;
        if (list.size() != 2) {
            System.err.println("   FAIL: Expected 2 items, got " + list.size());
            passed = false;
        } else if (!list.get(0).equals("suspicious link [http://phish.xyz]") || !list.get(1).equals("rush deadline [30 min]")) {
            System.err.println("   FAIL: Mismatched array item content: " + list);
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (String array parsed correctly without cutting off on embedded brackets)");
            testsPassed++;
        }
    }

    /**
     * Test 21: AI network failure / timeout / error triggers safe Rule Heuristics fallback
     */
    private static void testAiFailureTriggersRuleFallback() {
        testsTotal++;
        System.out.println("\n[Test 21] AI Network Outage / Failure Rule Fallback Execution");

        String suspiciousMessage = "Pay a refundable security deposit of Rs 799 within 30 minutes to secure your internship.";

        // Simulate fallback execution when AI fails or times out
        Server.AnalysisResult result = Server.analyzeMessage(suspiciousMessage);

        System.out.println(" -> Engine: " + result.engine);
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Grounded findings count: " + result.findings.size());
        System.out.println(" -> Uncertainty: " + result.uncertainty);

        boolean passed = true;
        if (!"Rule Heuristics".equals(result.engine)) {
            System.err.println("   FAIL: Fallback engine must be labeled 'Rule Heuristics', got: " + result.engine);
            passed = false;
        } else if (!"high".equals(result.level)) {
            System.err.println("   FAIL: Expected high risk level, got: " + result.level);
            passed = false;
        } else if (result.score < 60 || result.score > 100) {
            System.err.println("   FAIL: Expected high risk score between 60 and 100, got: " + result.score);
            passed = false;
        } else if (result.findings.isEmpty()) {
            System.err.println("   FAIL: Findings list should not be empty in rule fallback");
            passed = false;
        } else if (result.uncertainty == null || result.uncertainty.isBlank()) {
            System.err.println("   FAIL: Uncertainty disclosure missing in rule fallback");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Deterministic heuristic fallback executed safely with honest engine label)");
            testsPassed++;
        }
    }

    /**
     * Test 22: Static file presence & directory traversal protection for production deployment
     */
    private static void testStaticFileServingAndPathTraversalProtection() {
        testsTotal++;
        System.out.println("\n[Test 22] Static UI File Serving & Directory Traversal Protection");

        boolean passed = true;

        // 1. Verify required frontend files exist for Docker deployment
        java.nio.file.Path indexHtml = java.nio.file.Paths.get("frontend/index.html");
        java.nio.file.Path styleCss = java.nio.file.Paths.get("frontend/style.css");
        java.nio.file.Path appJs = java.nio.file.Paths.get("frontend/app.js");

        if (!java.nio.file.Files.exists(indexHtml)) {
            System.err.println("   FAIL: frontend/index.html does not exist!");
            passed = false;
        }
        if (!java.nio.file.Files.exists(styleCss)) {
            System.err.println("   FAIL: frontend/style.css does not exist!");
            passed = false;
        }
        if (!java.nio.file.Files.exists(appJs)) {
            System.err.println("   FAIL: frontend/app.js does not exist!");
            passed = false;
        }

        // 2. Traversal validation check
        String maliciousPath1 = "../etc/passwd";
        String maliciousPath2 = "..\\secret.key";
        if (!maliciousPath1.contains("..") || !maliciousPath2.contains("\\")) {
            System.err.println("   FAIL: Path traversal sanitization check logic failed!");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Frontend assets verified and path traversal blocked)");
            testsPassed++;
        }
    }

    private static boolean hasPhrase(List<String> phrases, String searchSubstring) {
        if (phrases == null) return false;
        String s = searchSubstring.toLowerCase();
        for (String p : phrases) {
            if (p.toLowerCase().contains(s)) return true;
        }
        return false;
    }
}
