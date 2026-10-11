/**
 * ScamLens — Frontend Interaction & Evidence Presentation Script
 * "Explain Before You Trust"
 * 
 * Connected to the ScamLens Java Backend Server:
 * Endpoint: POST http://localhost:8080/api/analyze
 */

// API Configuration: dynamically resolves custom URL -> localStorage override -> cloud origin -> local default
function getApiBaseUrl() {
  if (typeof window !== 'undefined' && window.SCAMLENS_API_URL) {
    return window.SCAMLENS_API_URL.replace(/\/+$/, '');
  }
  try {
    const stored = localStorage.getItem('scamlens_api_url');
    if (stored && stored.trim()) {
      return stored.trim().replace(/\/+$/, '');
    }
  } catch (_) {}

  // If hosted on a deployed cloud host (not file:// and not localhost), use current origin
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    const host = window.location.hostname;
    const protocol = window.location.protocol;
    if (protocol.startsWith('http') && host !== 'localhost' && host !== '127.0.0.1' && !host.endsWith('.local') && !host.endsWith('github.io')) {
      return window.location.origin;
    }
  }

  return 'http://localhost:8080';
}

const API_BASE_URL = getApiBaseUrl();
const ANALYZE_ENDPOINT = `${API_BASE_URL}/api/analyze`;

// DOM Elements — Inputs & Controls
const messageInput = document.getElementById('message-input');
const charCounter = document.getElementById('char-counter');
const clearBtn = document.getElementById('clear-btn');
const analyzeBtn = document.getElementById('analyze-btn');
const tryExampleBtn = document.getElementById('try-example-btn');
const demoCards = document.querySelectorAll('.demo-card');

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
const enginePill = document.getElementById('engine-pill');

const contradictionBlock = document.getElementById('contradiction-block');
const contradictionText = document.getElementById('contradiction-text');

const findingsContainer = document.getElementById('findings-container');
const groundedCount = document.getElementById('grounded-count');
const explanationText = document.getElementById('explanation-text');
const uncertaintyBox = document.getElementById('uncertainty-box');
const uncertaintyText = document.getElementById('uncertainty-text');
const actionList = document.getElementById('action-list');

// Curated Demo Scenarios
const demoScenarios = {
  contradiction: "Congratulations! There is no application fee for this role. However, you must pay a refundable security deposit of \u20B9799 within 30 minutes to secure your seat. The deposit will be returned after your interview.",
  phishing: "URGENT NOTICE: Your electricity connection will be suspended tonight at 9:30 PM due to an unpaid bill. Please immediately call our officer at +91-98765-XXXXX or pay via this link http://pay-bill-quick-now.xyz to avoid disruption.",
  legitimate: "Congratulations! We are pleased to invite you for an interview for the Software Engineering Internship at Acme Corp. Please note that there is no application or registration fee for this hiring process. We will never ask you to pay for equipment or onboarding. Please schedule your interview on our official careers portal.",
  waiver: "Congratulations! Your application for the Summer Research Fellowship has been approved. Please note that the registration fee has been 100% waived for all university candidates. The deadline to confirm your acceptance is next Friday. No payment or deposit is required."
};

let activeDemoKey = 'contradiction';

// Initialize with default demo scenario text
if (messageInput && !messageInput.value.trim()) {
  messageInput.value = demoScenarios.contradiction;
  updateCharCounter();
}

// 1. Character Counter
function updateCharCounter() {
  if (!messageInput || !charCounter) return;
  const count = messageInput.value.length;
  charCounter.textContent = `${count} character${count === 1 ? '' : 's'}`;
}

messageInput.addEventListener('input', updateCharCounter);

// 2. Clear Button
clearBtn.addEventListener('click', () => {
  messageInput.value = '';
  updateCharCounter();
  demoCards.forEach(c => c.classList.remove('active'));
  showState('empty');
  messageInput.focus();
});

// 3. Demo Scenario Selector Handlers
demoCards.forEach(card => {
  card.addEventListener('click', () => {
    demoCards.forEach(c => c.classList.remove('active'));
    card.classList.add('active');
    const demoKey = card.getAttribute('data-demo');
    if (demoScenarios[demoKey]) {
      activeDemoKey = demoKey;
      messageInput.value = demoScenarios[demoKey];
      updateCharCounter();
    }
  });
});

// 4. "Try Example & Analyze" Button
tryExampleBtn.addEventListener('click', () => {
  const currentDemo = demoScenarios[activeDemoKey] || demoScenarios.contradiction;
  messageInput.value = currentDemo;
  updateCharCounter();
  runAnalysis();
});

// 5. View State Controller (Empty / Loading / Error / Results)
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

// 6. Primary Analyze Trigger
analyzeBtn.addEventListener('click', () => {
  runAnalysis();
});

// 7. Core Analysis Execution Function
async function runAnalysis() {
  const text = messageInput.value.trim();

  if (!text) {
    alert("Please paste a message or select one of the demo scenarios above!");
    messageInput.focus();
    return;
  }

  // Set button loading state
  analyzeBtn.disabled = true;
  tryExampleBtn.disabled = true;
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
    // Restore button states
    analyzeBtn.disabled = false;
    tryExampleBtn.disabled = false;
    if (btnTextSpan) btnTextSpan.textContent = originalText;
  }
}

// 8. Grounded Evidence Rendering & Safe DOM Updates
function validateAndRenderAssessment(data) {
  if (!data || typeof data !== 'object') {
    throw new Error("Invalid response format received from server.");
  }

  // Validate risk level
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

  // Safe DOM updates
  riskBadge.className = `risk-badge ${level}`;
  riskLabel.textContent = label;
  riskSummary.textContent = summary;

  if (riskScorePill) {
    riskScorePill.textContent = score !== null ? `Score: ${score} / 100` : 'Score: N/A';
  }

  if (enginePill) {
    const rawEngine = (typeof data.engine === 'string' && data.engine.trim()) ? data.engine.trim() : 'ScamLens';
    enginePill.textContent = rawEngine;
    if (rawEngine.toLowerCase().includes('gemma')) {
      enginePill.className = 'engine-pill ai';
    } else {
      enginePill.className = 'engine-pill heuristic';
    }
  }

  // 1. Contradictions Section
  const contradictions = Array.isArray(data.contradictions) ? data.contradictions : [];
  if (contradictions.length > 0) {
    contradictionBlock.classList.remove('hidden');
    contradictionText.textContent = contradictions.join(' ');
  } else {
    contradictionBlock.classList.add('hidden');
  }

  // 2. Grounded Findings Section
  const findings = Array.isArray(data.findings) ? data.findings : [];
  clearChildren(findingsContainer);

  let groundedCountNum = 0;

  if (findings.length > 0) {
    findings.forEach(finding => {
      const card = document.createElement('div');
      card.className = 'finding-card';

      // Category Pill
      const categoryRow = document.createElement('div');
      categoryRow.className = 'finding-header';

      const catBadge = document.createElement('span');
      catBadge.className = 'finding-category-pill';
      catBadge.textContent = finding.category || 'Observed Indicator';
      categoryRow.appendChild(catBadge);

      if (finding.isGrounded !== false && finding.quote) {
        groundedCountNum++;
        const groundedPill = document.createElement('span');
        groundedPill.className = 'grounded-verified-badge';
        groundedPill.textContent = '✓ Directly Observed in Message';
        categoryRow.appendChild(groundedPill);
      }

      card.appendChild(categoryRow);

      // Verbatim Quote Block
      if (finding.quote && String(finding.quote).trim()) {
        const quoteBox = document.createElement('blockquote');
        quoteBox.className = 'finding-quote';
        quoteBox.textContent = `"${String(finding.quote).trim()}"`;
        card.appendChild(quoteBox);
      }

      // Plain-English Explanation
      if (finding.explanation && String(finding.explanation).trim()) {
        const expP = document.createElement('p');
        expP.className = 'finding-explanation';
        expP.innerHTML = `<strong>Why this matters:</strong> ${escapeHtml(finding.explanation)}`;
        card.appendChild(expP);
      }

      // Actionable Safe Step
      if (finding.action && String(finding.action).trim()) {
        const actDiv = document.createElement('div');
        actDiv.className = 'finding-action';
        actDiv.innerHTML = `<strong>Actionable Step:</strong> ${escapeHtml(finding.action)}`;
        card.appendChild(actDiv);
      }

      findingsContainer.appendChild(card);
    });
  } else {
    // Fallback to phrases array if findings are not populated
    const phrases = Array.isArray(data.phrases) ? data.phrases : [];
    if (phrases.length > 0) {
      phrases.forEach(phrase => {
        const card = document.createElement('div');
        card.className = 'finding-card simple';
        const p = document.createElement('p');
        p.textContent = String(phrase);
        card.appendChild(p);
        findingsContainer.appendChild(card);
      });
    } else {
      const emptyFinding = document.createElement('div');
      emptyFinding.className = 'finding-card empty';
      emptyFinding.textContent = 'No explicit high-risk patterns or demands identified in submitted text.';
      findingsContainer.appendChild(emptyFinding);
    }
  }

  if (groundedCount) {
    groundedCount.textContent = `${groundedCountNum} quote${groundedCountNum === 1 ? '' : 's'} grounded`;
  }

  // 3. Explanation Section
  explanationText.textContent = explanation;

  // 4. Uncertainty & Limitations Section
  if (data.uncertainty && String(data.uncertainty).trim()) {
    uncertaintyBox.classList.remove('hidden');
    uncertaintyText.textContent = String(data.uncertainty).trim();
  } else {
    uncertaintyBox.classList.remove('hidden');
    uncertaintyText.textContent = "Analytical risk assessment based on textual patterns. Not legal proof or a calibrated mathematical probability.";
  }

  // 5. Actions Section
  const actions = Array.isArray(data.actions) ? data.actions : [];
  clearChildren(actionList);
  if (actions.length === 0) {
    const li = document.createElement('li');
    li.textContent = 'Always exercise caution and independently verify the sender through official directories.';
    actionList.appendChild(li);
  } else {
    actions.forEach(action => {
      const li = document.createElement('li');
      li.textContent = String(action);
      actionList.appendChild(li);
    });
  }

  // Reveal results
  showState('results');

  // Smoothly scroll to results if on smaller screens
  if (window.innerWidth <= 860) {
    const resultsCard = document.getElementById('results-card');
    if (resultsCard) {
      resultsCard.scrollIntoView({ behavior: 'smooth' });
    }
  }
}

// 9. Display Friendly Error State
function displayFriendlyError(err) {
  showState('error');
  if (errorMessage) {
    errorMessage.textContent = err.message || "Could not connect to the ScamLens Java server.";
  }
}

// Helper: Clear element children
function clearChildren(elem) {
  if (!elem) return;
  while (elem.firstChild) {
    elem.removeChild(elem.firstChild);
  }
}

// Helper: Safe HTML entity escaping
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
