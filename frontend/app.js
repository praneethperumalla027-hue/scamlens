/**
 * ScamLens — Frontend Interaction Script
 * 
 * In this step, this script handles:
 * 1. Sample message insertion buttons
 * 2. Character counter & input clearing
 * 3. A client-side demonstration analyzer (so you can test the UI immediately)
 * 
 * In the next step, we will connect this to our Java backend server!
 */

// DOM Elements
const messageInput = document.getElementById('message-input');
const charCounter = document.getElementById('char-counter');
const clearBtn = document.getElementById('clear-btn');
const analyzeBtn = document.getElementById('analyze-btn');

const emptyState = document.getElementById('empty-state');
const resultsContent = document.getElementById('results-content');
const riskBadge = document.getElementById('risk-badge');
const riskLabel = document.getElementById('risk-label');
const riskSummary = document.getElementById('risk-summary');
const suspiciousPhrasesList = document.getElementById('suspicious-phrases');
const explanationText = document.getElementById('explanation-text');
const actionList = document.getElementById('action-list');

// Quick sample templates
const sampleMessages = {
  internship: "Congratulations! You have been selected for an exclusive remote data entry internship at Global Tech LLC paying $45/hour. No prior interview needed! To finalize onboarding and reserve your company laptop, kindly pay a refundable security deposit of $30 via UPI / crypto within 2 hours.",
  payment: "URGENT NOTICE: Your electricity connection will be disconnected tonight at 9:30 PM due to an unpaid bill. Please immediately call our officer at +91-98765-XXXXX or pay via this link http://pay-bill-quick-now.xyz to avoid disruption.",
  scholarship: "Dear Student, you have won the $5,000 International Merit Grant 2026. Claim your grant code by confirming your banking credentials and sending a $15 processing fee."
};

// 1. Character Counter
messageInput.addEventListener('input', () => {
  const count = messageInput.value.length;
  charCounter.textContent = `${count} character${count === 1 ? '' : 's'}`;
});

// 2. Clear Button
clearBtn.addEventListener('click', () => {
  messageInput.value = '';
  charCounter.textContent = '0 characters';
  messageInput.focus();
});

// 3. Sample Chips
document.getElementById('sample-internship').addEventListener('click', () => {
  messageInput.value = sampleMessages.internship;
  messageInput.dispatchEvent(new Event('input'));
});

document.getElementById('sample-payment').addEventListener('click', () => {
  messageInput.value = sampleMessages.payment;
  messageInput.dispatchEvent(new Event('input'));
});

document.getElementById('sample-scholarship').addEventListener('click', () => {
  messageInput.value = sampleMessages.scholarship;
  messageInput.dispatchEvent(new Event('input'));
});

// 4. Client-side Preview Analyzer (Placeholder for Step 1 testing)
analyzeBtn.addEventListener('click', () => {
  const text = messageInput.value.trim();

  if (!text) {
    alert("Please paste or type a message first, or click one of the quick samples above!");
    return;
  }

  // Simulate analysis state
  analyzeBtn.disabled = true;
  analyzeBtn.querySelector('.btn-text').textContent = 'Analyzing...';

  setTimeout(() => {
    // Basic local keyword check for initial UI demonstration
    const lower = text.toLowerCase();
    const hasUrgency = lower.includes('urgent') || lower.includes('immediately') || lower.includes('within 2 hours');
    const hasMoneyDemand = lower.includes('fee') || lower.includes('deposit') || lower.includes('pay') || lower.includes('crypto');
    const hasUnrealisticOffer = lower.includes('$45/hour') || lower.includes('no prior interview') || lower.includes('won');

    let assessment = {
      level: 'high',
      label: 'High Risk',
      summary: 'Multiple classic scam indicators detected (urgent pressure & upfront payment request).',
      phrases: ['Refundable security deposit', 'Pay within 2 hours', 'No prior interview needed'],
      explanation: 'Legitimate employers and institutions never ask candidates to pay an upfront refundable fee or deposit for equipment or processing. Creating artificial urgency is a common psychological manipulation tactic.',
      actions: [
        'Do not send any money, UPI transfers, or gift card codes.',
        'Never click unknown links or download attachments from this sender.',
        'Verify the opportunity directly on the official company careers portal.'
      ]
    };

    if (!hasMoneyDemand && !hasUrgency) {
      assessment = {
        level: 'low',
        label: 'Low Risk Indicators',
        summary: 'No obvious urgency or payment demands found in quick heuristics.',
        phrases: ['No immediate high-risk phrases detected'],
        explanation: 'The message does not contain blatant scam signals, but stay cautious. Scammers can be subtle.',
        actions: [
          'Verify sender email domain matches the official organization.',
          'Never reveal passwords, OTPs, or personal identity numbers.'
        ]
      };
    }

    renderAssessment(assessment);

    analyzeBtn.disabled = false;
    analyzeBtn.querySelector('.btn-text').textContent = 'Analyze Message';
  }, 400);
});

// 5. Render Assessment to UI
function renderAssessment(data) {
  emptyState.classList.add('hidden');
  resultsContent.classList.remove('hidden');

  // Set risk class (high, medium, low)
  riskBadge.className = `risk-badge ${data.level}`;
  riskLabel.textContent = data.label;
  riskSummary.textContent = data.summary;

  // Suspicious phrases
  suspiciousPhrasesList.innerHTML = '';
  data.phrases.forEach(phrase => {
    const li = document.createElement('li');
    li.textContent = phrase;
    suspiciousPhrasesList.appendChild(li);
  });

  // Explanation
  explanationText.textContent = data.explanation;

  // Action steps
  actionList.innerHTML = '';
  data.actions.forEach(act => {
    const li = document.createElement('li');
    li.textContent = act;
    actionList.appendChild(li);
  });
}
