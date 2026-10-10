import java.util.List;

/**
 * Automated Test Suite for ScamLens Rule-Based Heuristic Analyzer
 * 
 * Verifies:
 * 1. Legitimate internship invitation with no fee is classified as Low Risk (no false positive).
 * 2. Explicit job registration fee request is classified as High Risk.
 * 3. "No registration fee" alongside unrelated suspicious signals (urgency + phishing domain).
 * 4. Mixed message containing "no application fee" AND "pay refundable deposit of ₹799 within 30 minutes".
 *    (Must flag BOTH upfront deposit AND urgency pressure, resulting in High Risk >= 60).
 * 5. Ordinary benign scheduling deadlines (e.g. "within 3 days", "by Friday") are NOT flagged as scam urgency.
 */
public class ServerTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" 🧪 Running ScamLens Heuristic Analyzer Tests");
        System.out.println("==================================================");

        testLegitimateInternshipWithNoFee();
        testExplicitJobRegistrationFee();
        testNoFeeWithUnrelatedSuspiciousSignal();
        testMixedFeeNegationWithActualUrgentDeposit();
        testBenignSchedulingDeadlines();
        testFeeWaiverNotification();

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
        System.out.println(" -> Summary: " + result.summary);
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
        System.out.println(" -> Explanation: " + result.explanation);
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
        if (result.explanation.toLowerCase().contains("upfront money") || result.explanation.toLowerCase().contains("onboarding charges")) {
            System.err.println("   FAIL: Explanation invented payment demand when none existed.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Preserved urgency/link flags without false positive payment flag)");
            testsPassed++;
        }
    }

    /**
     * Test 4: Mixed Message (Negated application fee BUT actual urgent deposit demand)
     * e.g. "There is no application fee. Pay refundable deposit of ₹799 within 30 minutes. Returned after interview."
     */
    private static void testMixedFeeNegationWithActualUrgentDeposit() {
        testsTotal++;
        String message = "Congratulations! There is no application fee for this role. "
                + "However, you must pay a refundable security deposit of \u20B9799 within 30 minutes to secure your seat. "
                + "The deposit will be returned after your interview.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 4] Mixed Message: 'No application fee' + Refundable Deposit of \u20B9799 within 30 minutes");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);
        System.out.println(" -> Explanation: " + result.explanation);

        boolean passed = true;
        // MUST flag upfront payment demand
        if (!hasPhrase(result.suspiciousPhrases, "upfront payment")) {
            System.err.println("   FAIL: Failed to detect the deposit request of \u20B9799.");
            passed = false;
        }
        // MUST flag urgency demand (within 30 minutes)
        if (!hasPhrase(result.suspiciousPhrases, "urgency")) {
            System.err.println("   FAIL: Failed to detect urgency pressure ('within 30 minutes').");
            passed = false;
        }
        // Must be classified as High Risk (score >= 60)
        if (!"high".equalsIgnoreCase(result.level) || result.score < 60) {
            System.err.println("   FAIL: Expected High Risk (score >= 60), got: " + result.level + " (Score " + result.score + ")");
            passed = false;
        }
        // Explanation must mention the deposit and the urgency
        if (!result.explanation.toLowerCase().contains("upfront money") || !result.explanation.toLowerCase().contains("urgency")) {
            System.err.println("   FAIL: Explanation did not describe both payment and urgency evidence.");
            passed = false;
        }

        if (passed) {
            System.out.println("   PASSED ✅ (Correctly flagged High Risk: detected payment demand + 30-min urgency pressure)");
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
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);

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
     * Test 6: Message mentioning a fee waiver and standard application deadline (should NOT trigger payment or urgency flags)
     */
    private static void testFeeWaiverNotification() {
        testsTotal++;
        String message = "Congratulations! Your application for the Summer Research Fellowship has been approved. "
                + "Please note that the registration fee has been 100% waived for all university candidates. "
                + "The deadline to confirm your acceptance is next Friday. No payment or deposit is required.";

        Server.AnalysisResult result = Server.analyzeMessage(message);

        System.out.println("\n[Test 6] Fee Waiver Notification & Standard Deadline (100% waived, confirm by next Friday)");
        System.out.println(" -> Level: " + result.level + " | Score: " + result.score);
        System.out.println(" -> Phrases flagged: " + result.suspiciousPhrases);

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

    private static boolean hasPhrase(List<String> phrases, String searchSubstring) {
        if (phrases == null) return false;
        String s = searchSubstring.toLowerCase();
        for (String p : phrases) {
            if (p.toLowerCase().contains(s)) return true;
        }
        return false;
    }
}
