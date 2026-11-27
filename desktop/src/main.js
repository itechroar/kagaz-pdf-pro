'use strict';
const { app, BrowserWindow, Menu, clipboard, dialog, shell } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { startServer } = require('./server');
const { serverEnv, missingTools, installCommand, installInTerminal } = require('./tools');

const PHONE_PORT = 8080;
let server = null;
let win = null;
let serverDir = '';
let phoneMode = false;

const userFile = (name) => path.join(app.getPath('userData'), name);
const dataDir = () => path.join(app.getPath('userData'), 'data');

/** Password for the shared (phone) mode, generated once and kept so the admin account stays reachable. */
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
    adminPassword: lan ? phonePassword() : '',
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
      label: 'Phone',
      submenu: [{ label: phoneMode ? 'Stop sharing with phone' : 'Use with phone…', click: togglePhoneMode }],
    },
    { role: 'windowMenu' },
  ]));
}

async function createWindow() {
  serverDir = app.isPackaged ? path.join(process.resourcesPath, 'server') : path.join(__dirname, '..', 'resources');
  let origin;
  try {
    origin = await launch(false);
  } catch (e) {
    dialog.showErrorBox('Kagaz PDF could not start', String(e.message || e));
    app.quit();
    return;
  }
  win = new BrowserWindow({
    width: 1400, height: 900, title: 'Kagaz PDF',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  const isLocal = (url) => server && url.startsWith(`http://127.0.0.1:${server.port}`);
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (!isLocal(url)) shell.openExternal(url);
    return { action: isLocal(url) ? 'allow' : 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (!isLocal(url)) { event.preventDefault(); shell.openExternal(url); }
  });
  await win.loadURL(origin);
  await warnAboutMissingTools();
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { if (server) server.stop(); });
