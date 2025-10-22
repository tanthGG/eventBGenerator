const generateBtn = document.getElementById('generate-btn');
const statusEl = document.getElementById('status');
const refinementForm = document.getElementById('refinement-form');
const refinementInput = document.getElementById('refinement-count-input');
const refinementGroupsEl = document.getElementById('refinement-groups');
const patternContainer = document.getElementById('pattern-container');
const clearRefinementBtn = document.getElementById('clear-refinement-btn');

let availablePatterns = [];
let refinementSelections = [];

refinementForm.addEventListener('submit', (event) => {
  event.preventDefault();
  const rawValue = refinementInput.value ? refinementInput.value.trim() : '';
  const count = Number.parseInt(rawValue, 10);
  if (Number.isNaN(count) || count < 1) {
    showStatus('Enter a refinement count of at least 1.', true);
    return;
  }
  if (count > 20) {
    showStatus('Please choose 20 refinements or fewer to keep things manageable.', true);
    return;
  }
  applyRefinementCount(count);
});

if (clearRefinementBtn) {
  clearRefinementBtn.addEventListener('click', () => {
    refinementInput.value = '';
    clearRefinements();
  });
}

generateBtn.addEventListener('click', handleGenerate);

loadPatterns();
updateGenerateState();

async function loadPatterns() {
  try {
    const res = await fetch('/api/patterns');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!Array.isArray(data)) {
      availablePatterns = [];
      throw new Error('Unexpected response while loading patterns.');
    }
    availablePatterns = data;
    renderRefinementGroups();
  } catch (err) {
    availablePatterns = [];
    renderRefinementGroups();
    showStatus(`Failed to load patterns: ${err.message}`, true);
  } finally {
    updateGenerateState();
  }
}

function applyRefinementCount(count) {
  refinementSelections = Array.from({ length: count }, () => new Set());
  renderRefinementGroups();
  updateGenerateState();
  showStatus(
    `Configured ${count} refinement${count === 1 ? '' : 's'}. Select patterns for each step.`,
  );
}

function renderRefinementGroups() {
  refinementGroupsEl.innerHTML = '';
  const hasSelections = refinementSelections.length > 0;
  patternContainer.hidden = !hasSelections;
  if (!hasSelections) return;

  if (!availablePatterns.length) {
    const empty = document.createElement('p');
    empty.className = 'empty-state';
    empty.textContent = 'No pattern XML files found in the node_Structure folder.';
    refinementGroupsEl.appendChild(empty);
    return;
  }

  refinementSelections.forEach((selection, index) => {
    refinementGroupsEl.appendChild(createRefinementGroup(selection, index));
  });
}

function createRefinementGroup(selection, index) {
  const wrapper = document.createElement('div');
  wrapper.className = 'refinement-group';

  const heading = document.createElement('h3');
  heading.textContent = `Refinement ${index + 1}`;
  wrapper.appendChild(heading);

  const list = document.createElement('div');
  list.className = 'pattern-list';

  availablePatterns.forEach((name) => {
    const label = document.createElement('label');
    label.className = 'pattern-item';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.value = name;
    checkbox.checked = selection.has(name);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) {
        selection.add(name);
      } else {
        selection.delete(name);
      }
      updateGenerateState();
    });

    const span = document.createElement('span');
    span.textContent = name;

    label.append(checkbox, span);
    list.appendChild(label);
  });

  wrapper.appendChild(list);
  return wrapper;
}

function updateGenerateState() {
  const hasPatterns = availablePatterns.length > 0;
  const ready =
    hasPatterns &&
    refinementSelections.length > 0 &&
    refinementSelections.every((set) => set.size > 0);
  generateBtn.disabled = !ready;
}

function clearRefinements() {
  refinementSelections = [];
  refinementGroupsEl.innerHTML = '';
  patternContainer.hidden = true;
  updateGenerateState();
  showStatus('Refinements cleared.');
}

function showStatus(message, isError = false) {
  statusEl.textContent = message;
  statusEl.classList.toggle('error', isError);
}

async function handleGenerate() {
  const ready =
    refinementSelections.length > 0 &&
    refinementSelections.every((set) => set.size > 0) &&
    availablePatterns.length > 0;
  if (!ready) {
    showStatus('Select at least one pattern for each refinement before generating.', true);
    return;
  }

  const payload = {
    refinements: refinementSelections.map((set) => Array.from(set)),
  };

  generateBtn.disabled = true;
  showStatus('Generating files…');

  try {
    const res = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `HTTP ${res.status}`);
    }
    const projectName = res.headers.get('X-Project-Name') || '';
    const projectPath = res.headers.get('X-Project-Path') || projectName || '';
    const filesHeader = res.headers.get('X-Generated-Files') || '';
    const files = parseFilesHeader(filesHeader);
    const disposition = res.headers.get('Content-Disposition') || '';
    const downloadName =
      parseFileNameFromDisposition(disposition) ||
      (projectName ? `${projectName}.zip` : 'eventb-artifacts.zip');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = downloadName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);

    const filesCount = files.length || refinementSelections.length * 2;
    const targetPath = projectPath || 'workspace';
    showStatus(
      `Generated ${filesCount} file${filesCount === 1 ? '' : 's'} in ${targetPath}. Download saved as ${downloadName}.`,
    );
  } catch (err) {
    showStatus(`Generation failed: ${err.message}`, true);
  } finally {
    updateGenerateState();
  }
}

function parseFilesHeader(header) {
  if (!header) return [];
  return header
    .split(';')
    .map((item) => item.trim())
    .filter(Boolean);
}

function parseFileNameFromDisposition(disposition) {
  if (!disposition) return '';
  const utfMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utfMatch && utfMatch[1]) {
    try {
      return decodeURIComponent(utfMatch[1]);
    } catch (_) {
      // fall back to next strategy
    }
  }
  const quotedMatch = disposition.match(/filename="([^"]+)"/i);
  if (quotedMatch && quotedMatch[1]) {
    return quotedMatch[1];
  }
  const bareMatch = disposition.match(/filename=([^;]+)/i);
  if (bareMatch && bareMatch[1]) {
    return bareMatch[1].trim();
  }
  return '';
}
