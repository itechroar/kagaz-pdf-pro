'use strict';
const { app, BrowserWindow, Menu, clipboard, dialog, ipcMain, net, shell } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { startServer } = require('./server');
const { serverEnv, missingTools, installCommand, installInTerminal } = require('./tools');
const { normalizeServerUrl, isCleartextRemote, loadConfig, saveConfig } = require('./config');

const PHONE_PORT = 8080;
let server = null;
let win = null;
let serverDir = '';
let phoneMode = false;
let remoteUrl = null;
let connectWin = null;

const userFile = (name) => path.join(app.getPath('userData'), name);
const dataDir = () => path.join(app.getPath('userData'), 'data');

/** Password of the server's built-in administrator, generated once and kept so that account stays reachable. */
function phonePassword() {
  const file = userFile('phone-password');
  if (!fs.existsSync(file)) fs.writeFileSync(file, crypto.randomBytes(9).toString('base64url'), { mode: 0o600 });
  return fs.readFileSync(file, 'utf8').trim();
}

function lanAddress() {
  for (const list of Object.values(os.networkInterfaces())) {
    for (const nic of list || []) if (nic.family === 'IPv4' && !nic.internal) return nic.address;
  }
  return null;
}

/** Tells the user which optional tools are missing and offers to install them. */
async function warnAboutMissingTools() {
  const missing = missingTools(serverEnv(process.platform, process.env, serverDir), process.platform);
  const flag = userFile('tools-warning-dismissed');
  if (missing.length === 0 || fs.existsSync(flag)) return;
  const command = installCommand(process.platform);
  const list = missing.map((t) => `${t.label}: ${t.features}`).join('\n');
  const { response, checkboxChecked } = await dialog.showMessageBox({
    type: 'info',
    title: 'Some PDF tools need helper programs',
    message: 'Some tools will not work until helper programs are installed.',
    detail: `${list}\n\nInstall them with:\n${command}\n\nThen restart Kagaz PDF.`,
    buttons: ['Install now', 'Copy install command', 'Continue'],
    defaultId: 0,
    cancelId: 2,
    checkboxLabel: "Don't show this again",
  });
  if (response === 0) {
    const opened = installInTerminal(process.platform, process.env);
    await dialog.showMessageBox({
      type: 'info',
      message: opened ? 'The installer is running in a new window.' : 'Could not open a terminal.',
      detail: opened ? 'When it finishes, close and reopen Kagaz PDF.' : `Run this yourself:\n${command}`,
    });
  }
  if (response === 1) clipboard.writeText(command);
  if (checkboxChecked) fs.writeFileSync(flag, '1');
}

async function launch(lan) {
  if (server) { server.stop(); server = null; }
  server = await startServer({
    serverDir,
    dataDir: dataDir(),
    lan,
    login: true,
    signup: true,
    adminPassword: phonePassword(),
    preferredPort: PHONE_PORT,
  });
  phoneMode = lan;
  const origin = `http://127.0.0.1:${server.port}`;
  server.origin = origin;
  buildMenu();
  return origin;
}

async function togglePhoneMode() {
  if (phoneMode) {
    try {
      win.loadURL(await launch(false));
    } catch (e) {
      dialog.showErrorBox('Could not restart the server', String(e.message || e));
    }
    return;
  }
  const ip = lanAddress();
  if (!ip) {
    dialog.showErrorBox('No network found', 'Connect this computer to Wi-Fi or a network first.');
    return;
  }
  const { response } = await dialog.showMessageBox(win, {
    type: 'question',
    title: 'Use Kagaz PDF with a phone',
    message: 'Share Kagaz PDF with devices on your Wi-Fi?',
    detail: 'The server will accept connections from your network and require a sign-in. Windows may ask you to allow Java through the firewall: choose Allow. Choose "Stop sharing" in the Phone menu to go back to local-only.',
    buttons: ['Share', 'Cancel'],
    defaultId: 0,
    cancelId: 1,
  });
  if (response !== 0) return;
  try {
    const origin = await launch(true);
    win.loadURL(origin);
    const address = `${ip}:${server.port}`;
    const password = phonePassword();
    const { response: next } = await dialog.showMessageBox(win, {
      type: 'info',
      title: 'Ready for your phone',
      message: `On your phone (same Wi-Fi) open the Kagaz PDF app and enter:\n${address}`,
      detail: `Sign in with\nUsername: admin\nPassword: ${password}`,
      buttons: ['Copy address', 'Copy password', 'Close'],
      defaultId: 2,
    });
    if (next === 0) clipboard.writeText(address);
    if (next === 1) clipboard.writeText(password);
  } catch (e) {
    dialog.showErrorBox('Could not share', String(e.message || e));
    win.loadURL(await launch(false));
  }
}

function buildMenu() {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' }] : [{ label: 'File', submenu: [{ role: 'quit' }] }]),
    { role: 'editMenu' },
    { role: 'viewMenu' },
    {
      label: 'Account',
      submenu: [
        { label: 'Connect to a hosted server…', click: openConnectWindow },
        ...(remoteUrl ? [{ label: 'Use this computer instead', click: useThisComputer }] : []),
      ],
    },
    ...(remoteUrl
      ? []
      : [{
          label: 'Phone',
          submenu: [{ label: phoneMode ? 'Stop sharing with phone' : 'Use with phone…', click: togglePhoneMode }],
        }]),
    { role: 'windowMenu' },
  ]));
}

function currentOrigin() {
  if (remoteUrl) return remoteUrl;
  return server ? `http://127.0.0.1:${server.port}` : '';
}

function openConnectWindow() {
  if (connectWin) { connectWin.focus(); return; }
  connectWin = new BrowserWindow({
    width: 480, height: 300, parent: win, modal: true, resizable: false, minimizable: false,
    title: 'Connect to a server', autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'connect-preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  connectWin.removeMenu();
  connectWin.on('closed', () => { connectWin = null; });
  connectWin.loadFile(path.join(__dirname, 'connect.html'));
}

async function probeLogin(origin) {
  let res;
  try {
    res = await net.fetch(`${origin}/api/v1/auth/status`);
  } catch (e) {
    return `Cannot reach ${origin}.\n${e.message}`;
  }
  const body = await res.json().catch(() => null);
  return res.ok && body && body.loginEnabled === true
    ? null
    : 'That address is not a Kagaz PDF server with sign-in enabled.';
}

ipcMain.handle('connect-server', async (event, input) => {
  if (!connectWin || event.sender !== connectWin.webContents) return { error: 'Not allowed' };
  const url = normalizeServerUrl(input);
  if (!url) return { error: 'Enter a valid address, for example https://pdf.example.com' };
  if (isCleartextRemote(url)) {
    const { response } = await dialog.showMessageBox(connectWin, {
      type: 'warning',
      message: 'This connection is not encrypted.',
      detail: 'Your password would travel over the network in plain text. Prefer an https address.',
      buttons: ['Connect anyway', 'Cancel'],
      defaultId: 1,
      cancelId: 1,
    });
    if (response !== 0) return { error: '' };
  }
  const problem = await probeLogin(url);
  if (problem) return { error: problem };
  saveConfig(userFile('server.json'), { mode: 'remote', url });
  if (server) { server.stop(); server = null; }
  phoneMode = false;
  remoteUrl = url;
  buildMenu();
  const closing = connectWin;
  connectWin = null;
  closing.close();
  win.loadURL(url);
  return {};
});

async function useThisComputer() {
  saveConfig(userFile('server.json'), { mode: 'local' });
  remoteUrl = null;
  try {
    win.loadURL(await launch(false));
  } catch (e) {
    dialog.showErrorBox('Could not start the local server', String(e.message || e));
  }
}

async function createWindow() {
  serverDir = app.isPackaged ? path.join(process.resourcesPath, 'server') : path.join(__dirname, '..', 'resources');
  const saved = loadConfig(userFile('server.json'));
  remoteUrl = saved && saved.mode === 'remote' ? saved.url : null;
  let origin;
  try {
    if (remoteUrl) { origin = remoteUrl; buildMenu(); } else { origin = await launch(false); }
  } catch (e) {
    dialog.showErrorBox('Kagaz PDF could not start', String(e.message || e));
    app.quit();
    return;
  }
  win = new BrowserWindow({
    width: 1400, height: 900, title: 'Kagaz PDF',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  const isLocal = (url) => { const o = currentOrigin(); return o !== '' && new URL(url).origin === o; };
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (!isLocal(url)) shell.openExternal(url);
    return { action: isLocal(url) ? 'allow' : 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (!isLocal(url)) { event.preventDefault(); shell.openExternal(url); }
  });
  await win.loadURL(origin);
  if (!remoteUrl) await warnAboutMissingTools();
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { if (server) server.stop(); });
