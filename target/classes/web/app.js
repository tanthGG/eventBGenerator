const patternList = document.getElementById('pattern-list');
const generateBtn = document.getElementById('generate-btn');
const statusEl = document.getElementById('status');
const refinementCountEl = document.getElementById('refinement-count');
const resetRefinementBtn = document.getElementById('reset-refinement-btn');
const selected = new Set();
let nextRefinementIndex = 1;
try {
  const stored = localStorage.getItem('nextRefinementIndex');
  if (stored) {
    const parsed = parseInt(stored, 10);
    if (!Number.isNaN(parsed) && parsed > 0) nextRefinementIndex = parsed;
  }
} catch (_) {
  nextRefinementIndex = 1;
}

async function loadPatterns() {
  try {
    const res = await fetch('/api/patterns');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const patterns = await res.json();
    selected.clear();
    patternList.innerHTML = '';
    if (!patterns.length) {
      patternList.innerHTML = '<p>No pattern XML files found.</p>';
      updateSelectionState();
      return;
    }
    patterns.forEach((name) => patternList.appendChild(createItem(name)));
    updateSelectionState();
  } catch (err) {
    showStatus(`Failed to load patterns: ${err.message}`, true);
  }
}

function createItem(name) {
  const wrapper = document.createElement('label');
  wrapper.className = 'pattern-item';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.value = name;
  checkbox.addEventListener('change', () => {
    if (checkbox.checked) {
      selected.add(name);
    } else {
      selected.delete(name);
    }
    updateSelectionState();
  });
  const span = document.createElement('span');
  span.textContent = name;
  wrapper.append(checkbox, span);
  return wrapper;
}

function showStatus(message, isError = false) {
  statusEl.textContent = message;
  statusEl.classList.toggle('error', isError);
}

async function handleGenerate() {
  if (!selected.size) return;
  const currentRefinement = nextRefinementIndex;
  generateBtn.disabled = true;
  showStatus('Generating…');
  try {
    const res = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        refinement: currentRefinement,
        patterns: Array.from(selected),
      }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `HTTP ${res.status}`);
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'eventb-pattern.zip';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showStatus(`Refinement ${currentRefinement} generated. Combined Event-B artefact ready for download.`);
    nextRefinementIndex += 1;
    try {
      localStorage.setItem('nextRefinementIndex', String(nextRefinementIndex));
    } catch (_) {
      // ignore persistence errors
    }
    selected.clear();
    patternList.querySelectorAll('input[type="checkbox"]').forEach((checkbox) => {
      checkbox.checked = false;
    });
  } catch (err) {
    showStatus(`Generation failed: ${err.message}`, true);
  } finally {
    updateSelectionState();
  }
}

generateBtn.addEventListener('click', handleGenerate);
if (resetRefinementBtn) {
  resetRefinementBtn.addEventListener('click', resetRefinementCounter);
}
updateSelectionState();
loadPatterns();

function updateSelectionState() {
  generateBtn.disabled = selected.size === 0;
  if (refinementCountEl) {
    refinementCountEl.textContent = `Next refinement: ${nextRefinementIndex}`;
  }
}

function resetRefinementCounter() {
  nextRefinementIndex = 1;
  try {
    localStorage.setItem('nextRefinementIndex', String(nextRefinementIndex));
  } catch (_) {
    // ignore persistence errors
  }
  updateSelectionState();
  showStatus('Refinement counter reset to 1.');
}
