// Krelzox Remote web client. Talks only to the TV that served this page.
'use strict';

const I18N = {
  en: {
    connecting: 'Connecting…', connected: 'Connected', disconnected: 'Disconnected — retrying…',
    fullscreen: 'Full screen', logout: 'Disconnect',
    pair_title: 'Connect to your TV',
    pair_text: 'Type the PIN shown on the TV. It appears in a corner of the screen and in Settings › Krelzox Remote.',
    pair_button: 'Connect', lan_only: 'Local network only. Nothing leaves your home network.',
    pin_wrong: 'Wrong PIN. Check the TV and try again.', pin_locked: 'Too many attempts. Wait a minute.',
    pin_full: 'Too many devices are connected. Disconnect one on the TV.', pin_error: 'Could not reach the TV.',
    waiting_image: 'Waiting for the image…', view_blocked: 'Screen viewing is blocked on the TV.',
    view_off: 'Screen capture is not running on the TV.',
    request_control: 'Request control', control_none: 'View only. Ask the TV for control to use the remote.',
    control_requested: 'Waiting for approval on the TV…', control_denied: 'Control was denied on the TV.',
    control_unavailable: 'Remote control is disabled on the TV or its control service is off.',
    mode_touch: 'Touch', mode_trackpad: 'Trackpad', mode_dpad: 'Remote',
    hint_touch: 'Tap the image to tap the TV. Drag to swipe, hold for a long press. With a mouse: click, double-click, right-click and wheel.',
    pad_text: 'Move one finger to move the cursor · tap to click · two fingers to scroll',
    left_click: 'Left click', double_click: 'Double click', right_click: 'Right click',
    back: 'Back', home: 'Home', recents: 'Recents', apps: 'Apps', enter: 'Enter',
    type_here: 'Type here to write on the TV', hint_keyboard: 'On a PC you can also use the arrow keys, Enter and Esc directly.',
  },
  es: {
    connecting: 'Conectando…', connected: 'Conectado', disconnected: 'Sin conexión — reintentando…',
    fullscreen: 'Pantalla completa', logout: 'Desconectar',
    pair_title: 'Conéctate a tu tele',
    pair_text: 'Escribe el PIN que aparece en la tele: en una esquina de la pantalla y en Configuración › Krelzox Remote.',
    pair_button: 'Conectar', lan_only: 'Solo red local. Nada sale de tu red doméstica.',
    pin_wrong: 'PIN incorrecto. Míralo en la tele y vuelve a intentarlo.', pin_locked: 'Demasiados intentos. Espera un minuto.',
    pin_full: 'Hay demasiados dispositivos conectados. Desconecta uno en la tele.', pin_error: 'No se pudo contactar con la tele.',
    waiting_image: 'Esperando la imagen…', view_blocked: 'Ver la pantalla está bloqueado en la tele.',
    view_off: 'La captura de pantalla no está activa en la tele.',
    request_control: 'Pedir el control', control_none: 'Solo ver. Pide el control a la tele para usar el mando.',
    control_requested: 'Esperando aprobación en la tele…', control_denied: 'La tele denegó el control.',
    control_unavailable: 'El control remoto está desactivado en la tele o su servicio de control no está activo.',
    mode_touch: 'Táctil', mode_trackpad: 'Trackpad', mode_dpad: 'Mando',
    hint_touch: 'Toca la imagen para tocar la tele. Arrastra para deslizar y mantén pulsado para una pulsación larga. Con ratón: clic, doble clic, clic derecho y rueda.',
    pad_text: 'Mueve un dedo para mover el cursor · toca para hacer clic · dos dedos para desplazar',
    left_click: 'Clic izquierdo', double_click: 'Doble clic', right_click: 'Clic derecho',
    back: 'Atrás', home: 'Inicio', recents: 'Recientes', apps: 'Apps', enter: 'Intro',
    type_here: 'Escribe aquí para escribir en la tele', hint_keyboard: 'En un PC también puedes usar las flechas, Intro y Esc directamente.',
  },
};
const T = I18N[(navigator.language || 'en').slice(0, 2)] || I18N.en;

const $ = (id) => document.getElementById(id);
const state = { ws: null, retry: 0, view: false, control: 'NONE', controlEnabled: false, mode: 'touch', cursor: { x: 0.5, y: 0.5 }, url: null };

function translate() {
  document.documentElement.lang = T === I18N.es ? 'es' : 'en';
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = T[el.dataset.i18n] || el.textContent; });
  document.querySelectorAll('[data-i18n-title]').forEach((el) => { el.title = T[el.dataset.i18nTitle]; });
  document.querySelectorAll('[data-i18n-placeholder]').forEach((el) => { el.placeholder = T[el.dataset.i18nPlaceholder]; });
}

function setConn(kind, text) {
  $('conn').className = 'conn ' + kind;
  $('conn-text').textContent = text;
}

// ---- Pairing --------------------------------------------------------------------

async function start() {
  translate();
  try {
    const r = await fetch('/api/session', { credentials: 'same-origin', cache: 'no-store' });
    const s = await r.json();
    if (s.paired) showRemote(); else showPair();
  } catch (e) {
    setConn('bad', T.pin_error);
    setTimeout(start, 3000);
  }
}

function showPair() {
  $('remote').hidden = true;
  $('top-actions').hidden = true;
  $('pair').hidden = false;
  setConn('wait', T.pair_title);
  $('pin').focus();
}

$('pair-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const pin = $('pin').value.replace(/\D/g, '');
  $('pair-error').textContent = '';
  try {
    const r = await fetch('/api/pair', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'pin=' + encodeURIComponent(pin),
    });
    if (r.ok) { $('pin').value = ''; showRemote(); return; }
    const msg = { 401: T.pin_wrong, 429: T.pin_locked, 409: T.pin_full }[r.status] || T.pin_error;
    $('pair-error').textContent = msg;
    $('pin').select();
  } catch (err) {
    $('pair-error').textContent = T.pin_error;
  }
});

// ---- Remote view ----------------------------------------------------------------

function showRemote() {
  $('pair').hidden = true;
  $('remote').hidden = false;
  $('top-actions').hidden = false;
  connect();
}

function connect() {
  if (state.ws) return;
  setConn('wait', T.connecting);
  const ws = new WebSocket('ws://' + location.host + '/ws');
  ws.binaryType = 'blob';
  state.ws = ws;
  ws.onopen = () => { state.retry = 0; setConn('ok', T.connected); };
  ws.onmessage = (ev) => (typeof ev.data === 'string' ? onState(JSON.parse(ev.data)) : onFrame(ev.data));
  ws.onclose = async () => {
    state.ws = null;
    setConn('bad', T.disconnected);
    // Session revoked or expired? Then pair again instead of retrying forever.
    try {
      const s = await (await fetch('/api/session', { credentials: 'same-origin', cache: 'no-store' })).json();
      if (!s.paired) { showPair(); return; }
    } catch (e) { /* TV unreachable: keep retrying */ }
    const delay = Math.min(10000, 500 * 2 ** state.retry++);
    setTimeout(connect, delay);
  };
}

function send(obj) {
  if (state.ws && state.ws.readyState === WebSocket.OPEN) state.ws.send(JSON.stringify(obj));
}

function cmd(action, extra) { send(Object.assign({ type: 'cmd', action }, extra || {})); }

let currentUrl = null;
function onFrame(blob) {
  const url = URL.createObjectURL(blob);
  const img = $('screen');
  img.onload = () => {
    if (currentUrl) URL.revokeObjectURL(currentUrl);
    currentUrl = url;
    $('screen-msg').hidden = true;
    send({ type: 'ack' }); // ready for the next frame: keeps latency low
  };
  img.onerror = () => { URL.revokeObjectURL(url); send({ type: 'ack' }); };
  img.src = url;
}

function onState(s) {
  if (s.type !== 'state') return;
  state.view = s.view;
  state.control = s.control;
  state.controlEnabled = s.controlEnabled;
  if (s.width && s.height) $('screen-wrap').style.aspectRatio = s.width + ' / ' + s.height;
  const msg = $('screen-msg');
  if (!s.viewEnabled) { msg.hidden = false; msg.textContent = T.view_blocked; }
  else if (!s.view) { msg.hidden = false; msg.textContent = T.view_off; }

  const granted = s.control === 'GRANTED' && s.controlEnabled;
  $('controls').hidden = !granted;
  const banner = $('control-banner');
  banner.hidden = granted;
  const request = $('btn-request');
  if (!s.controlEnabled) { $('control-text').textContent = T.control_unavailable; request.hidden = true; }
  else if (s.control === 'REQUESTED') { $('control-text').textContent = T.control_requested; request.hidden = true; }
  else if (s.control === 'DENIED') { $('control-text').textContent = T.control_denied; request.hidden = false; }
  else if (!granted) { $('control-text').textContent = T.control_none; request.hidden = false; }
}

$('btn-request').addEventListener('click', () => send({ type: 'requestControl' }));

// ---- Pointer input on the image (touch mode / mouse) ------------------------------

function norm(ev) {
  const img = $('screen');
  const rect = img.getBoundingClientRect();
  // The image is letterboxed (object-fit: contain): find the drawn area.
  const ratio = (img.naturalWidth && img.naturalHeight) ? img.naturalWidth / img.naturalHeight : 16 / 9;
  let w = rect.width, h = rect.height, ox = 0, oy = 0;
  if (w / h > ratio) { w = h * ratio; ox = (rect.width - w) / 2; } else { h = w / ratio; oy = (rect.height - h) / 2; }
  const x = (ev.clientX - rect.left - ox) / w;
  const y = (ev.clientY - rect.top - oy) / h;
  return { x: Math.min(1, Math.max(0, x)), y: Math.min(1, Math.max(0, y)), inside: x >= 0 && x <= 1 && y >= 0 && y <= 1 };
}

function canControl() { return state.control === 'GRANTED' && state.controlEnabled; }

const wrap = $('screen-wrap');
let press = null;
let lastTap = null;
let tapTimer = null;

wrap.addEventListener('pointerdown', (ev) => {
  if (!canControl() || state.mode !== 'touch' || ev.button === 2) return;
  const p = norm(ev);
  if (!p.inside) return;
  wrap.setPointerCapture(ev.pointerId);
  press = { start: p, last: p, t: performance.now(), long: setTimeout(() => { cmd('longpress', p); press.done = true; }, 650) };
});
wrap.addEventListener('pointermove', (ev) => {
  if (ev.pointerType === 'mouse' && canControl()) moveCursorTo(norm(ev), true);
  if (!press) return;
  press.last = norm(ev);
  if (Math.hypot(press.last.x - press.start.x, press.last.y - press.start.y) > 0.02) clearTimeout(press.long);
});
wrap.addEventListener('pointerup', () => {
  if (!press) return;
  clearTimeout(press.long);
  const p = press;
  press = null;
  if (p.done) return;
  const moved = Math.hypot(p.last.x - p.start.x, p.last.y - p.start.y) > 0.02;
  if (moved) {
    cmd('swipe', { x1: p.start.x, y1: p.start.y, x2: p.last.x, y2: p.last.y, ms: Math.round(Math.min(2000, Math.max(80, performance.now() - p.t))) });
    return;
  }
  // Double tap: a second tap within 260 ms near the first one.
  const now = performance.now();
  if (lastTap && now - lastTap.t < 260 && Math.hypot(p.start.x - lastTap.x, p.start.y - lastTap.y) < 0.04) {
    clearTimeout(tapTimer);
    lastTap = null;
    cmd('doubletap', { x: p.start.x, y: p.start.y });
    return;
  }
  lastTap = { x: p.start.x, y: p.start.y, t: now };
  tapTimer = setTimeout(() => { cmd('tap', { x: p.start.x, y: p.start.y }); lastTap = null; }, 260);
});
wrap.addEventListener('pointercancel', () => { if (press) clearTimeout(press.long); press = null; });
wrap.addEventListener('contextmenu', (ev) => {
  ev.preventDefault();
  if (!canControl()) return;
  const p = norm(ev);
  if (p.inside) cmd('longpress', { x: p.x, y: p.y });
});
wrap.addEventListener('wheel', (ev) => {
  if (!canControl()) return;
  ev.preventDefault();
  const p = norm(ev);
  cmd('scroll', { x: p.x, y: p.y, dx: Math.max(-1, Math.min(1, ev.deltaX / 600)), dy: Math.max(-1, Math.min(1, ev.deltaY / 600)) });
}, { passive: false });

// ---- Cursor (shown here and on the TV) --------------------------------------------

let cursorSentAt = 0;
function moveCursorTo(p, fromMouse) {
  state.cursor = { x: p.x, y: p.y };
  const c = $('cursor');
  c.hidden = fromMouse; // a mouse already has its own pointer here
  const img = $('screen').getBoundingClientRect();
  const wrapRect = wrap.getBoundingClientRect();
  c.style.left = (img.left - wrapRect.left + p.x * img.width) + 'px';
  c.style.top = (img.top - wrapRect.top + p.y * img.height) + 'px';
  const now = performance.now();
  if (now - cursorSentAt > 33) { cursorSentAt = now; cmd('cursor', { x: p.x, y: p.y }); }
}

// ---- Trackpad --------------------------------------------------------------------

const pad = $('pad');
const touches = new Map();
let padMoved = false;
let padDownAt = 0;
pad.addEventListener('pointerdown', (ev) => {
  pad.setPointerCapture(ev.pointerId);
  touches.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
  padMoved = false;
  padDownAt = performance.now();
});
pad.addEventListener('pointermove', (ev) => {
  const prev = touches.get(ev.pointerId);
  if (!prev) return;
  const dx = ev.clientX - prev.x, dy = ev.clientY - prev.y;
  touches.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
  if (Math.abs(dx) + Math.abs(dy) > 1) padMoved = true;
  if (touches.size >= 2) {
    // Two fingers: scroll around the cursor (natural direction).
    cmd('scroll', { x: state.cursor.x, y: state.cursor.y, dx: Math.max(-1, Math.min(1, -dx / 300)), dy: Math.max(-1, Math.min(1, -dy / 300)) });
    return;
  }
  const speed = 1.6 / pad.clientWidth;
  moveCursorTo({ x: Math.min(1, Math.max(0, state.cursor.x + dx * speed)), y: Math.min(1, Math.max(0, state.cursor.y + dy * speed * 16 / 9)) }, false);
});
function padUp(ev) {
  touches.delete(ev.pointerId);
  if (touches.size === 0 && !padMoved && performance.now() - padDownAt < 300) cmd('tap', state.cursor);
}
pad.addEventListener('pointerup', padUp);
pad.addEventListener('pointercancel', (ev) => touches.delete(ev.pointerId));
document.querySelectorAll('[data-click]').forEach((b) => b.addEventListener('click', () => cmd(b.dataset.click, state.cursor)));

// ---- Keys and modes --------------------------------------------------------------

document.querySelectorAll('[data-key]').forEach((b) => {
  let repeat = null;
  const fire = () => cmd('key', { key: b.dataset.key });
  b.addEventListener('pointerdown', (ev) => {
    ev.preventDefault();
    fire();
    if (b.classList.contains('d')) repeat = setInterval(fire, 180); // hold arrows to repeat
  });
  const stop = () => { clearInterval(repeat); repeat = null; };
  b.addEventListener('pointerup', stop);
  b.addEventListener('pointerleave', stop);
  b.addEventListener('keydown', (ev) => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); fire(); } });
});

document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => {
  state.mode = t.dataset.mode;
  document.querySelectorAll('.tab').forEach((o) => o.classList.toggle('active', o === t));
  $('mode-trackpad').hidden = state.mode !== 'trackpad';
  $('mode-dpad').hidden = state.mode !== 'dpad';
  $('hint-touch').hidden = state.mode !== 'touch';
  $('cursor').hidden = state.mode !== 'trackpad';
  if (state.mode === 'trackpad') moveCursorTo(state.cursor, false);
}));

// Typing: send what changed in the field (works with phone keyboards and IMEs).
const typer = $('typer');
let typed = '';
typer.addEventListener('input', () => {
  const now = typer.value;
  let common = 0;
  while (common < typed.length && common < now.length && typed[common] === now[common]) common++;
  for (let i = common; i < typed.length; i++) cmd('backspace');
  const added = now.slice(common);
  if (added) cmd('text', { text: added });
  typed = now;
});
typer.addEventListener('keydown', (ev) => {
  if (ev.key === 'Enter') { ev.preventDefault(); cmd('enter'); typer.value = ''; typed = ''; }
  else if (ev.key === 'Backspace' && typer.value === '') cmd('backspace');
});
$('btn-enter').addEventListener('click', () => { cmd('enter'); typer.value = ''; typed = ''; });

// Physical keyboard on a PC (when not typing in the field).
document.addEventListener('keydown', (ev) => {
  if (!canControl() || ev.target === typer || ev.target.id === 'pin' || ev.ctrlKey || ev.metaKey || ev.altKey) return;
  const map = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'center', Escape: 'back', Home: 'home' };
  if (map[ev.key]) { ev.preventDefault(); cmd('key', { key: map[ev.key] }); return; }
  if (ev.key === 'Backspace') { ev.preventDefault(); cmd('backspace'); return; }
  if (ev.key.length === 1) { ev.preventDefault(); cmd('text', { text: ev.key }); }
});

// ---- Apps, full screen, logout ---------------------------------------------------

$('btn-apps').addEventListener('click', async () => {
  const grid = $('apps-grid');
  grid.textContent = '';
  $('apps').hidden = false;
  try {
    const apps = await (await fetch('/api/apps', { credentials: 'same-origin', cache: 'no-store' })).json();
    for (const a of apps) {
      const b = document.createElement('button');
      b.className = 'app';
      b.type = 'button';
      const img = document.createElement('img');
      img.src = '/api/icon?package=' + encodeURIComponent(a.package);
      img.alt = '';
      const label = document.createElement('span');
      label.textContent = a.name; // textContent: app names are never parsed as HTML
      b.append(img, label);
      b.addEventListener('click', () => { cmd('launch', { package: a.package }); $('apps').hidden = true; });
      grid.append(b);
    }
  } catch (e) { grid.textContent = T.pin_error; }
});
$('btn-apps-close').addEventListener('click', () => { $('apps').hidden = true; });

const full = $('btn-full');
if (!document.fullscreenEnabled) full.hidden = true;
full.addEventListener('click', () => {
  if (document.fullscreenElement) document.exitFullscreen();
  else wrap.requestFullscreen().catch(() => {});
});
document.addEventListener('fullscreenchange', () => document.body.classList.toggle('fullscreen', !!document.fullscreenElement));

$('btn-logout').addEventListener('click', async () => {
  await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' }).catch(() => {});
  if (state.ws) { state.ws.onclose = null; state.ws.close(); state.ws = null; }
  showPair();
});

// Keep the session alive while the page is open (the TV ends idle sessions).
setInterval(() => send({ type: 'ping' }), 20000);

start();
