const patternList = document.getElementById('pattern-list');
const generateBtn = document.getElementById('generate-btn');
const statusEl = document.getElementById('status');
const selected = new Set();

async function loadPatterns() {
  try {
    const res = await fetch('/api/patterns');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const patterns = await res.json();
    patternList.innerHTML = '';
    if (!patterns.length) {
      patternList.innerHTML = '<p>No pattern XML files found.</p>';
      return;
    }
    patterns.forEach((name) => patternList.appendChild(createItem(name)));
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
    generateBtn.disabled = selected.size === 0;
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
  generateBtn.disabled = true;
  showStatus('Generating…');
  try {
    const res = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Array.from(selected)),
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
    showStatus('Combined Event-B artefact ready for download.');
  } catch (err) {
    showStatus(`Generation failed: ${err.message}`, true);
  } finally {
    generateBtn.disabled = selected.size === 0;
  }
}

generateBtn.addEventListener('click', handleGenerate);
loadPatterns();
