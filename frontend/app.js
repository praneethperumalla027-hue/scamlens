/**
 * ScamLens — Frontend Interaction Script
 * 
 * Connected to the ScamLens Java Backend Server:
 * Endpoint: POST http://localhost:8080/api/analyze
 */

// API Configuration
const API_BASE_URL = 'http://localhost:8080';
const ANALYZE_ENDPOINT = `${API_BASE_URL}/api/analyze`;

// DOM Elements — Inputs & Controls
const messageInput = document.getElementById('message-input');
const charCounter = document.getElementById('char-counter');
const clearBtn = document.getElementById('clear-btn');
const analyzeBtn = document.getElementById('analyze-btn');

// DOM Elements — Results & State Views
const emptyState = document.getElementById('empty-state');
const loadingState = document.getElementById('loading-state');
const errorState = document.getElementById('error-state');
const errorTitle = document.getElementById('error-title');
const errorMessage = document.getElementById('error-message');
const resultsContent = document.getElementById('results-content');

// DOM Elements — Assessment Output Fields
const riskBadge = document.getElementById('risk-badge');
const riskLabel = document.getElementById('risk-label');
const riskSummary = document.getElementById('risk-summary');
const riskScorePill = document.getElementById('risk-score-pill');
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
  showState('empty');
  messageInput.focus();
});

// 3. Quick Sample Chips
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

// 4. View State Controller (Empty / Loading / Error / Results)
function showState(state) {
  emptyState.classList.add('hidden');
  loadingState.classList.add('hidden');
  errorState.classList.add('hidden');
  resultsContent.classList.add('hidden');

  switch (state) {
    case 'loading':
      loadingState.classList.remove('hidden');
      break;
    case 'error':
      errorState.classList.remove('hidden');
      break;
    case 'results':
      resultsContent.classList.remove('hidden');
      break;
    case 'empty':
    default:
      emptyState.classList.remove('hidden');
      break;
  }
}

// 5. Analyze Button Click Handler (Real Java Backend Connection)
analyzeBtn.addEventListener('click', async () => {
  const text = messageInput.value.trim();

  if (!text) {
    alert("Please paste or type a message first, or select one of the quick samples above!");
    messageInput.focus();
    return;
  }

  // Set button loading state
  analyzeBtn.disabled = true;
  const btnTextSpan = analyzeBtn.querySelector('.btn-text');
  const originalText = btnTextSpan ? btnTextSpan.textContent : 'Analyze Message';
  if (btnTextSpan) btnTextSpan.textContent = 'Analyzing...';

  // Show loading view
  showState('loading');

  try {
    const response = await fetch(ANALYZE_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ message: text })
    });

    if (!response.ok) {
      let serverError = `Server responded with HTTP ${response.status}`;
      try {
        const errorJson = await response.json();
        if (errorJson && errorJson.error) {
          serverError = errorJson.error;
        }
      } catch (_) {
        // Fall back to HTTP status message
      }
      throw new Error(serverError);
    }

    const data = await response.json();
    validateAndRenderAssessment(data);

  } catch (err) {
    console.error('[ScamLens] Backend request failed:', err);
    displayFriendlyError(err);
  } finally {
    // Restore button state
    analyzeBtn.disabled = false;
    if (btnTextSpan) btnTextSpan.textContent = originalText;
  }
});

// 6. Response Validation & Safe DOM Rendering
function validateAndRenderAssessment(data) {
  if (!data || typeof data !== 'object') {
    throw new Error("Invalid response format received from server.");
  }

  // Validate risk level against permitted CSS themes
  const allowedLevels = ['high', 'medium', 'low'];
  const level = (data.level && allowedLevels.includes(String(data.level).toLowerCase()))
    ? String(data.level).toLowerCase()
    : 'medium';

  // Sanitize text values
  const label = (typeof data.label === 'string' && data.label.trim())
    ? data.label.trim()
    : 'Assessment Complete';

  const summary = (typeof data.summary === 'string' && data.summary.trim())
    ? data.summary.trim()
    : 'No summary provided.';

  const explanation = (typeof data.explanation === 'string' && data.explanation.trim())
    ? data.explanation.trim()
    : 'No additional explanation available.';

  // Validate score
  const score = (typeof data.score === 'number' && !Number.isNaN(data.score))
    ? Math.max(0, Math.min(100, Math.round(data.score)))
    : null;

  // Validate arrays
  const phrases = Array.isArray(data.phrases) ? data.phrases : [];
  const actions = Array.isArray(data.actions) ? data.actions : [];

  // Safe DOM updates using textContent (preventing any XSS vulnerabilities)
  riskBadge.className = `risk-badge ${level}`;
  riskLabel.textContent = label;
  riskSummary.textContent = summary;

  if (riskScorePill) {
    riskScorePill.textContent = score !== null ? `Risk Score: ${score} / 100` : 'Score: N/A';
  }

  // Populate suspicious phrases
  clearChildren(suspiciousPhrasesList);
  if (phrases.length === 0) {
    const li = document.createElement('li');
    li.textContent = 'No explicit high-risk keywords detected';
    suspiciousPhrasesList.appendChild(li);
  } else {
    phrases.forEach(phrase => {
      const li = document.createElement('li');
      li.textContent = String(phrase);
      suspiciousPhrasesList.appendChild(li);
    });
  }

  // Populate explanation
  explanationText.textContent = explanation;

  // Populate recommended safe actions
  clearChildren(actionList);
  if (actions.length === 0) {
    const li = document.createElement('li');
    li.textContent = 'Always exercise caution when interacting with unverified senders.';
    actionList.appendChild(li);
  } else {
    actions.forEach(action => {
      const li = document.createElement('li');
      li.textContent = String(action);
      actionList.appendChild(li);
    });
  }

  // Switch to results view
  showState('results');
}

// 7. Friendly Error Handler
function displayFriendlyError(err) {
  showState('error');

  const isNetworkFailure = err.name === 'TypeError' || (err.message && err.message.toLowerCase().includes('failed to fetch'));

  if (isNetworkFailure) {
    if (errorTitle) errorTitle.textContent = "Java Backend Offline";
    if (errorMessage) {
      errorMessage.textContent = "Could not reach http://localhost:8080. The Java backend server is not running or port 8080 is blocked.";
    }
  } else {
    if (errorTitle) errorTitle.textContent = "Analysis Request Error";
    if (errorMessage) {
      errorMessage.textContent = err.message || "An unexpected error occurred while communicating with the server.";
    }
  }
}

// Helper: Safely remove all child nodes without unsafe innerHTML
function clearChildren(parent) {
  while (parent.firstChild) {
    parent.removeChild(parent.firstChild);
  }
}
