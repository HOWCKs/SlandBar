/* ============================================================
   SlandBar — Preview do frontend (lógica interativa)
   Espelha o comportamento do app Android (Kotlin/Compose).
   ============================================================ */

'use strict';

// ---------------- Estado ----------------

const state = {
  enabled: true,
  scale: 1,
  opacity: 1,
  theme: 'system',        // system | light | dark
  accent: null,           // hex ou null (padrão)
  autoHide: true,
  autoHideSec: 12,
  haptics: true,
  gestures: { tap: 'expand', double: 'none', long: 'flashlight', swipe: 'expand' },
  shortcuts: [
    { id: 's1', label: 'Lanterna', icon: '🔦', value: 'flashlight' },
    { id: 's2', label: 'WhatsApp', icon: '💬', value: 'app:com.whatsapp' },
    { id: 's3', label: 'Spotify', icon: '🎵', value: 'app:com.spotify' },
    { id: 's4', label: 'Câmera', icon: '📷', value: 'camera' },
  ],
  widgets: ['clock', 'toggles', 'shortcuts', 'timer', 'brightness'],
  premium: false,
  // toggles do painel
  torch: false, dnd: false, rot: true, ring: true,
  brightness: 70, volume: 60,
  // mídia
  playing: false, mediaTitle: 'Neon Horizon', mediaArtist: 'Aurora Waves',
  // timer
  timerMode: 'stopwatch', timerRunning: false, timerMs: 0, timerTargetMs: 0,
};

const FREE_LIMIT = 6;

// ---------------- Elementos ----------------

const $ = (id) => document.getElementById(id);
const screen = $('screen'), bar = $('floatbar'), panelOverlay = $('panelOverlay'),
      panel = $('panel'), phone = $('phone');

const QUICK_ICONS = {
  flashlight: '🔦', screenshot: '📸', camera: '📷', dnd: '🌙',
  rotation: '🔄', ringer: '🔔', power: '⚡', settings: '⚙️', expand: '⤢',
};
const QUICK_LABELS = {
  flashlight: 'Lanterna', screenshot: 'Captura', camera: 'Câmera', dnd: 'Não perturbe',
  rotation: 'Rotação', ringer: 'Modo de som', power: 'Energia', settings: 'Config.', expand: 'Painel',
};
const APP_ICONS = {
  'com.whatsapp': '💬', 'com.spotify': '🎵', 'com.instagram': '📷',
  'com.youtube': '▶', 'com.camera': '📷', 'com.maps': '🗺️',
};
const APP_LABELS = {
  'com.whatsapp': 'WhatsApp', 'com.spotify': 'Spotify', 'com.instagram': 'Instagram',
  'com.youtube': 'YouTube', 'com.camera': 'Câmera', 'com.maps': 'Mapas',
};

// ---------------- Relógio ----------------

function pad(n) { return String(n).padStart(2, '0'); }
function nowHM() { const d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
function nowHMS() { const d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()); }

setInterval(() => {
  $('fbTime').textContent = nowHM();
  $('sbTime').textContent = nowHM();
  $('wallTime').textContent = nowHM();
  $('pClock').textContent = nowHMS();
}, 1000);

function refreshDate() {
  const d = new Date();
  const dateLong = d.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });
  const dateShort = d.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  $('pDate').textContent = dateLong;
  $('wallDate').textContent = dateShort;
}
refreshDate();

// ---------------- Aplicar estado na UI ----------------

function applyTheme() {
  const dark = state.theme === 'dark' || (state.theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.body.dataset.theme = dark ? 'dark' : '';
}
applyTheme();

function applyAccent() {
  const a = state.accent || '#6366f1';
  document.documentElement.style.setProperty('--accent', a);
  document.documentElement.style.setProperty('--accent-2', state.accent ? a : '#22d3ee');
  document.querySelectorAll('.dot').forEach((d) => d.classList.toggle('on', (d.dataset.v || null) === state.accent));
}

function applyBar() {
  bar.style.display = state.enabled ? 'flex' : 'none';
  bar.style.setProperty('--scale', state.scale);
  bar.style.opacity = state.opacity;
}

function renderShortcuts() {
  const grid = $('shortcutGrid');
  grid.innerHTML = '';
  state.shortcuts.forEach((sc) => {
    const el = document.createElement('div');
    el.className = 'sc-item';
    const isApp = sc.value.startsWith('app:');
    const ic = isApp ? (APP_ICONS[sc.value.slice(4)] || '📱') : (QUICK_ICONS[sc.value] || '⚡');
    el.innerHTML = `<button class="sc-ic ${isApp ? 'app-ic2' : ''}">${ic}</button><span>${sc.label}</span>`;
    el.querySelector('.sc-ic').onclick = () => runAction(sc.value, true);
    grid.appendChild(el);
  });
}

function renderShortcutList() {
  const list = $('scList');
  list.innerHTML = '';
  state.shortcuts.forEach((sc) => {
    const isApp = sc.value.startsWith('app:');
    const ic = isApp ? (APP_ICONS[sc.value.slice(4)] || '📱') : (QUICK_ICONS[sc.value] || '⚡');
    const row = document.createElement('div');
    row.className = 'sc-entry';
    row.innerHTML = `<span>${ic}</span><span>${sc.label}</span>
      <button class="sc-del" title="Remover">✕</button>`;
    row.querySelector('.sc-del').onclick = () => {
      state.shortcuts = state.shortcuts.filter((x) => x.id !== sc.id);
      renderShortcutList(); renderShortcuts();
    };
    list.appendChild(row);
  });
  $('scLimitNote').textContent = state.premium
    ? 'Premium ativo: atalhos ilimitados.'
    : `Grátis: até ${FREE_LIMIT} atalhos. Premium: ilimitados.`;
}

function renderWidgets() {
  document.querySelectorAll('.widget').forEach((w) => {
    const key = w.dataset.w;
    const locked = !state.premium && (key === 'media' || key === 'volume');
    w.style.display = state.widgets.includes(key) ? '' : 'none';
    if (key === 'media' && locked) w.style.display = 'none';
    if (key === 'volume' && locked) w.style.display = 'none';
  });
  const anyLockedHidden = (state.widgets.includes('media') || state.widgets.includes('volume')) && !state.premium;
  $('premiumHint').hidden = !anyLockedHidden;
}

function renderToggles() {
  $('togTorch').classList.toggle('on', state.torch);
  $('togDnd').classList.toggle('on', state.dnd);
  $('togRot').classList.toggle('on', state.rot);
  $('togRing').classList.toggle('on', state.ring);
  $('fbTorchIco').style.display = state.torch ? '' : 'none';
  $('fbMusicIco').style.display = state.playing ? '' : 'none';
  $('mPlay').textContent = state.playing ? '⏸' : '▶';
}

function renderMedia() {
  $('mTitle').textContent = state.mediaTitle;
  $('mArtist').textContent = state.mediaArtist;
}

function renderTimer() {
  const total = state.timerMs;
  const h = Math.floor(total / 3600000), m = Math.floor((total % 3600000) / 60000), s = Math.floor((total % 60000) / 1000);
  $('tDisplay').textContent = h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
  $('chipStopwatch').classList.toggle('on', state.timerMode === 'stopwatch');
  $('chipTimer').classList.toggle('on', state.timerMode === 'timer');
  $('timerPresets').style.display = state.timerMode === 'timer' ? 'flex' : 'none';
  $('tStart').textContent = state.timerRunning ? '⏸ Pausar' : '▶ Iniciar';
}

function renderPremium() {
  $('premiumCard').style.display = state.premium ? 'none' : '';
  document.querySelectorAll('.prem-lock').forEach((cb) => {
    cb.disabled = !state.premium;
    if (!state.premium && cb.checked) cb.checked = false;
  });
  document.querySelectorAll('.dot').forEach((d) => {
    if (d.dataset.v) d.style.opacity = state.premium ? 1 : 0.4;
  });
  renderWidgets();
}

function renderAll() {
  applyTheme(); applyAccent(); applyBar();
  renderShortcuts(); renderShortcutList(); renderWidgets();
  renderToggles(); renderMedia(); renderTimer(); renderPremium();
}

// ---------------- Brilho do "aparelho" ----------------

function applyBrightness() {
  const v = state.brightness;
  screen.style.filter = `brightness(${0.35 + (v / 100) * 0.85})`;
  $('brightSlider').value = v;
}
applyBrightness();

// ---------------- Ações ----------------

function haptic() {
  if (state.haptics && navigator.vibrate) { try { navigator.vibrate(18); } catch (e) {} }
}

function toast(msg) {
  const t = document.createElement('div');
  t.className = 'toast-msg';
  t.textContent = msg;
  t.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#1e293b;color:#fff;padding:10px 18px;border-radius:999px;font-size:13px;z-index:999;box-shadow:0 10px 30px rgba(0,0,0,.5);animation:fadeIn .2s;';
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2200);
}

function runAction(value, fromPanel) {
  haptic();
  switch (value) {
    case 'expand': openPanel(); break;
    case 'flashlight':
      state.torch = !state.torch;
      renderToggles();
      toast(state.torch ? '🔦 Lanterna ligada' : '🔦 Lanterna desligada');
      break;
    case 'camera': toast('📷 Abrindo câmera…'); break;
    case 'screenshot': toast('📸 Captura salva na galeria'); break;
    case 'dnd':
      state.dnd = !state.dnd;
      renderToggles();
      toast(state.dnd ? '🌙 Não perturbe ativado' : '🔔 Não perturbe desativado');
      break;
    case 'rotation':
      state.rot = !state.rot;
      renderToggles();
      toast(state.rot ? '🔄 Rotação automática' : '🔒 Rotação travada');
      break;
    case 'ringer':
      state.ring = !state.ring;
      renderToggles();
      toast(state.ring ? '🔔 Modo de som: normal' : '🔕 Modo de som: silencioso');
      break;
    case 'power': toast('⚡ Menu de energia (via acessibilidade no app real)'); break;
    case 'settings': toast('⚙️ Abrindo configurações do SlandBar…'); break;
    default:
      if (value.startsWith('app:')) {
        const pkg = value.slice(4);
        toast(`📱 Abrindo ${APP_LABELS[pkg] || pkg}…`);
      } else {
        runAction('expand', true);
      }
  }
  if (fromPanel !== true) maybeAutoHide();
}

// ---------------- Painel ----------------

function openPanel() {
  if (!state.enabled) return;
  panelOverlay.classList.add('open');
  refreshTogglesFromState();
  renderWidgets();
  stopAutoHide();
  requestAnimationFrame(() => setTimeout(positionPanel, 30));
}

function closePanel() {
  panelOverlay.classList.remove('open');
  maybeAutoHide();
}

function refreshTogglesFromState() {
  renderToggles();
  renderMedia();
}

// posição do painel perto da barra
function positionPanel() {
  const barRect = bar.getBoundingClientRect();
  const screenRect = screen.getBoundingClientRect();
  const ph = phone.getBoundingClientRect();
  // distância da barra em relação ao topo da tela do celular
  const barTopInPhone = barRect.top - screenRect.top + screenRect.top - ph.top;
  const barTop = barRect.top - ph.top;
  const panelH = panel.offsetHeight;
  const phoneH = ph.height;
  let top = barTop - panelH - 10;
  if (top < 34) top = barTop + 58;
  top = Math.max(30, Math.min(top, phoneH - panelH - 20));
  panel.style.marginTop = top + 'px';
}

// ---------------- Gestos na barra ----------------

let downX = 0, downY = 0, startLeft = 0, startTop = 0, dragging = false;
let lastTap = 0, longPressTimer = null, tapTimer = null;

function resetAutoHide() {
  bar.style.opacity = state.opacity;
  stopAutoHide();
  if (state.autoHide && !panelOverlay.classList.contains('open')) {
    autoHideTimer = setTimeout(() => { bar.style.opacity = '0.3'; }, state.autoHideSec * 1000);
  }
}
let autoHideTimer = null;
function stopAutoHide() { clearTimeout(autoHideTimer); }
function maybeAutoHide() {
  if (panelOverlay.classList.contains('open')) return;
  if (state.autoHide) autoHideTimer = setTimeout(() => { bar.style.opacity = '0.3'; }, state.autoHideSec * 1000);
  else bar.style.opacity = state.opacity;
}

bar.addEventListener('pointerdown', (e) => {
  if (panelOverlay.classList.contains('open')) return;
  dragging = false;
  downX = e.clientX; downY = e.clientY;
  startLeft = bar.offsetLeft; startTop = bar.offsetTop;
  bar.style.opacity = state.opacity;
  stopAutoHide();
  clearTimeout(longPressTimer);
  longPressTimer = setTimeout(() => {
    haptic();
    if (navigator.vibrate) { try { navigator.vibrate([0, 25, 40, 30]); } catch (err) {} }
    runAction(state.gestures.long, false);
  }, 420);
  bar.setPointerCapture(e.pointerId);
});

bar.addEventListener('pointermove', (e) => {
  const dx = e.clientX - downX, dy = e.clientY - downY;
  if (!dragging && Math.hypot(dx, dy) > 6) {
    dragging = true;
    clearTimeout(longPressTimer);
  }
  if (dragging) {
    const maxL = screen.clientWidth - bar.offsetWidth - 10;
    const maxT = screen.clientHeight - bar.offsetHeight - 10;
    bar.style.left = Math.max(4, Math.min(maxL, startLeft + dx)) + 'px';
    bar.style.top = Math.max(4, Math.min(maxT, startTop + dy)) + 'px';
  }
});

bar.addEventListener('pointerup', (e) => {
  clearTimeout(longPressTimer);
  const dx = e.clientX - downX, dy = e.clientY - downY;
  if (dragging) {
    // snap para a borda mais próxima
    const maxL = screen.clientWidth - bar.offsetWidth - 10;
    const cur = bar.offsetLeft;
    bar.style.left = (cur < screen.clientWidth / 2 ? 10 : maxL) + 'px';
    maybeAutoHide();
    return;
  }
  if (dy < -50) { runAction(state.gestures.swipe, false); return; }
  if (dy > 50) { runAction(state.gestures.swipe, false); return; }
  // tap / double tap
  const now = Date.now();
  if (now - lastTap < 300) {
    lastTap = 0;
    clearTimeout(tapTimer);
    if (state.gestures.double !== 'none') runAction(state.gestures.double, false);
    return;
  }
  lastTap = now;
  tapTimer = setTimeout(() => runAction(state.gestures.tap, false), 240);
});

// ---------------- Painel: eventos ----------------

panelOverlay.addEventListener('click', (e) => {
  if (e.target === panelOverlay) closePanel();
});
$('btnCollapse').onclick = closePanel;
$('btnClose').onclick = () => { closePanel(); toast('Barra flutuante desativada (no app real, encerra o serviço)'); };

['togTorch', 'togDnd', 'togRot', 'togRing'].forEach((id) => {
  $(id).onclick = () => {
    const map = { togTorch: 'flashlight', togDnd: 'dnd', togRot: 'rotation', togRing: 'ringer' };
    runAction(map[id], true);
  };
});

$('brightSlider').oninput = (e) => { state.brightness = +e.target.value; applyBrightness(); };
$('volSlider').oninput = (e) => {
  state.volume = +e.target.value;
  toast(`🔊 Volume: ${state.volume}%`);
};

// mídia (demo)
let mediaTimer = null, mediaPos = 0;
$('mPlay').onclick = () => {
  state.playing = !state.playing;
  renderToggles();
  if (state.playing) {
    mediaTimer = setInterval(() => { mediaPos = (mediaPos + 1) % 100; $('mBar').style.width = mediaPos + '%'; }, 500);
  } else {
    clearInterval(mediaTimer);
  }
};
$('mNext').onclick = () => { state.mediaTitle = 'Midnight Drive'; state.mediaArtist = 'The Neon Club'; mediaPos = 0; renderMedia(); toast('⏭ Próxima faixa'); };
$('mPrev').onclick = () => { state.mediaTitle = 'Neon Horizon'; state.mediaArtist = 'Aurora Waves'; mediaPos = 0; renderMedia(); toast('⏮ Faixa anterior'); };

// timer (real)
let tickTimer = null, tickBase = 0, tickStart = 0;
function timerTick() {
  state.timerMs = tickBase + (Date.now() - tickStart);
  if (state.timerMode === 'timer' && state.timerMs >= state.timerTargetMs) {
    state.timerMs = state.timerTargetMs;
    pauseTimer();
    toast('⏰ Tempo esgotado!');
  }
  renderTimer();
}
function pauseTimer() {
  state.timerRunning = false;
  clearInterval(tickTimer); tickTimer = null;
}
$('tStart').onclick = () => {
  if (state.timerRunning) { pauseTimer(); renderTimer(); return; }
  if (state.timerMode === 'timer' && state.timerMs >= state.timerTargetMs) state.timerMs = 0;
  tickBase = state.timerMs;
  tickStart = Date.now();
  state.timerRunning = true;
  tickTimer = setInterval(timerTick, 100);
  renderTimer();
};
$('tReset').onclick = () => { pauseTimer(); state.timerMs = 0; renderTimer(); };
$('chipStopwatch').onclick = () => { pauseTimer(); state.timerMode = 'stopwatch'; state.timerMs = 0; renderTimer(); };
$('chipTimer').onclick = () => { pauseTimer(); state.timerMode = 'timer'; state.timerMs = 0; state.timerTargetMs = 5 * 60000; renderTimer(); };
$('timerPresets').querySelectorAll('.mini').forEach((b) => {
  b.onclick = () => {
    const min = parseInt(b.textContent, 10);
    pauseTimer();
    state.timerMode = 'timer';
    state.timerTargetMs = min * 60000;
    state.timerMs = 0;
    renderTimer();
    toast(`⏱ Temporizador: ${min} min`);
  };
});

// ---------------- Configurações ----------------

$('cfgEnabled').onchange = (e) => { state.enabled = e.target.checked; applyBar(); if (!state.enabled) closePanel(); };

document.querySelectorAll('#segSize button').forEach((b) => {
  b.onclick = () => {
    state.scale = parseFloat(b.dataset.v);
    document.querySelectorAll('#segSize button').forEach((x) => x.classList.toggle('on', x === b));
    applyBar();
  };
});

$('cfgOpacity').oninput = (e) => { state.opacity = e.target.value / 100; $('cfgOpacityVal').textContent = e.target.value + '%'; applyBar(); };

document.querySelectorAll('#segTheme button').forEach((b) => {
  b.onclick = () => {
    state.theme = b.dataset.v;
    document.querySelectorAll('#segTheme button').forEach((x) => x.classList.toggle('on', x === b));
    applyTheme();
  };
});

document.querySelectorAll('#colorRow .dot').forEach((d) => {
  d.onclick = () => {
    if (d.dataset.v && !state.premium) {
      toast('⭐ Cor personalizada é Premium — desbloqueie abaixo.');
      return;
    }
    state.accent = d.dataset.v || null;
    applyAccent();
  };
});

$('cfgAutoHide').onchange = (e) => { state.autoHide = e.target.checked; resetAutoHide(); };
$('cfgHideSec').oninput = (e) => { state.autoHideSec = +e.target.value; $('cfgHideVal').textContent = e.target.value + 's'; resetAutoHide(); };
$('cfgHaptics').onchange = (e) => { state.haptics = e.target.checked; };

['gTap', 'gDouble', 'gLong', 'gSwipe'].forEach((id) => {
  $(id).onchange = (e) => {
    const key = { gTap: 'tap', gDouble: 'double', gLong: 'long', gSwipe: 'swipe' }[id];
    state.gestures[key] = e.target.value;
  };
});

$('scAddBtn').onclick = () => {
  if (!state.premium && state.shortcuts.length >= FREE_LIMIT) {
    toast(`⭐ Grátis permite até ${FREE_LIMIT} atalhos. Premium: ilimitados.`);
    return;
  }
  const value = $('scAdd').value;
  let label, icon;
  if (value.startsWith('app:')) {
    const pkg = value.slice(4);
    label = APP_LABELS[pkg] || pkg;
    icon = APP_ICONS[pkg] || '📱';
  } else {
    label = QUICK_LABELS[value] || value;
    icon = QUICK_ICONS[value] || '⚡';
  }
  state.shortcuts.push({ id: 'sc' + Date.now(), label, icon, value });
  renderShortcuts(); renderShortcutList();
  toast(`✔ ${label} adicionado aos atalhos`);
};

document.querySelectorAll('.wchk').forEach((cb) => {
  cb.onchange = () => {
    const w = cb.dataset.w;
    const locked = !state.premium && (w === 'media' || w === 'volume');
    if (locked) {
      cb.checked = false;
      toast('⭐ Widget Premium — desbloqueie abaixo.');
      return;
    }
    if (cb.checked) state.widgets.push(w);
    else state.widgets = state.widgets.filter((x) => x !== w);
    renderWidgets();
  };
});

$('btnPremium').onclick = () => {
  state.premium = true;
  renderPremium(); renderWidgets(); renderShortcutList();
  document.querySelectorAll('.prem-lock').forEach((cb) => { cb.disabled = false; cb.checked = true; state.widgets.push(cb.dataset.w); });
  state.widgets = [...new Set(state.widgets)];
  renderWidgets();
  toast('⭐ Premium ativo! (no app real: Google Play Billing)');
};
$('btnRestore').onclick = () => toast('Restaurando compra… (Google Play Billing no app real)');

// ---------------- Init ----------------

renderAll();
applyBrightness();

window.addEventListener('resize', positionPanel);
