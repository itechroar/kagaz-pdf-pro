'use strict';
const { app, BaseWindow, WebContentsView, Menu, clipboard, dialog, ipcMain, session, shell } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { startServer } = require('./server');
const { TABS, normalizeServerUrl, isCleartextRemote, loadConfig, saveConfig } = require('./config');

const BAR_HEIGHT = 44;
const PARTITION = 'persist:kagaz-admin';

let win = null;
let shellView = null;
let server = null;
let origin = '';
let config = null;
let active = 'dashboard';
let fullScreen = true;
const views = new Map();

const userFile = (name) => path.join(app.getPath('userData'), name);
const adminSession = () => session.fromPartition(PARTITION);

/** Kept so the local server's administrator account stays reachable between launches. */
function localPassword() {
  const file = userFile('admin-password');
  if (!fs.existsSync(file)) fs.writeFileSync(file, crypto.randomBytes(18).toString('base64url'), { mode: 0o600 });
  return fs.readFileSync(file, 'utf8').trim();
}

function send(state) {
  if (shellView && !shellView.webContents.isDestroyed()) shellView.webContents.send('state', state);
}

function layout() {
  if (!win) return;
  const { width, height } = win.getContentBounds();
  shellView.setBounds({ x: 0, y: 0, width, height: fullScreen ? height : BAR_HEIGHT });
  const view = views.get(active);
  if (view) view.setBounds({ x: 0, y: BAR_HEIGHT, width, height: Math.max(0, height - BAR_HEIGHT) });
}

function showFull(state) {
  fullScreen = true;
  for (const view of views.values()) view.setVisible(false);
  layout();
  send(state);
}

function viewFor(tab) {
  let view = views.get(tab.id);
  if (view) return view;
  view = new WebContentsView({
    webPreferences: { session: adminSession(), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  const sameOrigin = (url) => origin && new URL(url).origin === origin;
  view.webContents.setWindowOpenHandler(({ url }) => {
    if (!sameOrigin(url) && /^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  view.webContents.on('will-navigate', (event, url) => {
    if (!sameOrigin(url)) {
      event.preventDefault();
      if (/^https?:/i.test(url)) shell.openExternal(url);
    }
  });
  win.contentView.addChildView(view);
  views.set(tab.id, view);
  view.webContents.loadURL(origin + tab.path);
  return view;
}

function select(id) {
  const tab = TABS.find((t) => t.id === id);
  if (!tab || !origin) return;
  active = id;
  fullScreen = false;
  const view = viewFor(tab);
  for (const [other, v] of views) v.setVisible(other === id);
  // A section left on the sign-in page stays there after signing in elsewhere; reopen it.
  if (view.webContents.getURL().includes('/login.html') && id !== 'dashboard') view.webContents.loadURL(origin + tab.path);
  layout();
  send({ screen: 'tabs', tabs: TABS, active, where: config.mode === 'local' ? 'This computer' : new URL(origin).host });
}

function dropViews() {
  for (const view of views.values()) {
    win.contentView.removeChildView(view);
    view.webContents.close();
  }
  views.clear();
}

async function api(pathname, options) {
  return adminSession().fetch(origin + pathname, options);
}

async function signInLocal() {
  const res = await api('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: localPassword() }),
  });
  if (!res.ok) throw new Error(`The local server refused the administrator sign-in (HTTP ${res.status}). Delete the "data" folder in the app's settings directory to start fresh.`);
}

async function checkRemote() {
  let res;
  try {
    res = await api('/api/v1/auth/status');
  } catch (e) {
    throw new Error(`Cannot reach ${origin}.\n${e.message}`);
  }
  const body = await res.json().catch(() => null);
  if (!res.ok || !body || body.loginEnabled !== true) {
    throw new Error('This server does not have login enabled, so it has no administrator console.\nStart it with security.enableLogin=true.');
  }
}

function serverDir() {
  return app.isPackaged ? path.join(process.resourcesPath, 'server') : path.join(__dirname, '..', 'resources');
}

async function connect(next) {
  dropViews();
  if (server) { server.stop(); server = null; }
  origin = '';
  config = next;
  try {
    if (next.mode === 'local') {
      showFull({ screen: 'busy', message: 'Starting the local server… the first start can take a minute.' });
      server = await startServer({ serverDir: serverDir(), dataDir: path.join(app.getPath('userData'), 'data'), login: true, adminPassword: localPassword() });
      origin = `http://127.0.0.1:${server.port}`;
      await signInLocal();
    } else {
      showFull({ screen: 'busy', message: `Connecting to ${new URL(next.url).host}…` });
      origin = next.url;
      await checkRemote();
    }
  } catch (e) {
    if (server) { server.stop(); server = null; }
    origin = '';
    showFull({ screen: 'setup', error: String(e.message || e), url: next.mode === 'remote' ? next.url : '' });
    return { error: String(e.message || e) };
  }
  select('dashboard');
  buildMenu();
  return {};
}

async function setup(settings) {
  let next;
  if (settings && settings.mode === 'local') {
    next = { mode: 'local' };
  } else {
    const url = normalizeServerUrl(settings && settings.url);
    if (!url) return { error: 'Enter a valid address, for example https://pdf.example.com' };
    if (isCleartextRemote(url)) {
      const { response } = await dialog.showMessageBox(win, {
        type: 'warning',
        message: 'This connection is not encrypted.',
        detail: 'Your administrator password would travel over the network in plain text. Use an https address unless this is a network you fully trust.',
        buttons: ['Connect anyway', 'Cancel'],
        defaultId: 1,
        cancelId: 1,
      });
      if (response !== 0) return { error: '' };
    }
    next = { mode: 'remote', url };
  }
  saveConfig(userFile('server.json'), next);
  return connect(next);
}

function buildMenu() {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' }] : [{ label: 'File', submenu: [{ role: 'quit' }] }]),
    { role: 'editMenu' },
    { role: 'viewMenu' },
    {
      label: 'Server',
      submenu: [
        { label: 'Change server…', click: () => showFull({ screen: 'setup', error: '', url: config && config.mode === 'remote' ? config.url : '' }) },
        ...(config && config.mode === 'local'
          ? [{ label: 'Copy local administrator password', click: () => clipboard.writeText(localPassword()) }]
          : []),
      ],
    },
    { role: 'windowMenu' },
  ]));
}

function createWindow() {
  win = new BaseWindow({ width: 1440, height: 920, title: 'Kagaz PDF Admin' });
  shellView = new WebContentsView({
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  win.contentView.addChildView(shellView);
  win.on('resize', layout);
  shellView.webContents.loadFile(path.join(__dirname, 'shell.html'));

  const fromShell = (event) => event.sender === shellView.webContents;
  ipcMain.on('ready', async (event) => {
    if (!fromShell(event)) return;
    buildMenu();
    const saved = loadConfig(userFile('server.json'));
    if (saved) await connect(saved);
    else showFull({ screen: 'setup', error: '', url: '' });
  });
  ipcMain.on('select', (event, id) => { if (fromShell(event)) select(id); });
  ipcMain.on('reload', (event) => {
    if (fromShell(event) && views.has(active)) views.get(active).webContents.reload();
  });
  ipcMain.on('change-server', (event) => {
    if (fromShell(event)) showFull({ screen: 'setup', error: '', url: config && config.mode === 'remote' ? config.url : '' });
  });
  ipcMain.handle('setup', (event, settings) => (fromShell(event) ? setup(settings) : { error: 'Not allowed' }));
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { if (server) server.stop(); });
