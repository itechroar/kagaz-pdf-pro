'use strict';
const { app, BrowserWindow, dialog, shell } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { startServer } = require('./server');
const { serverEnv, missingTools, installCommand } = require('./tools');

/** Tells the user once which optional tools are missing and how to install them. */
async function warnAboutMissingTools(serverDir) {
  const missing = missingTools(serverEnv(process.platform, process.env, serverDir), process.platform);
  const flag = path.join(app.getPath('userData'), 'tools-warning-dismissed');
  if (missing.length === 0 || fs.existsSync(flag)) return;
  const command = installCommand(process.platform);
  const list = missing.map((t) => `${t.label}: ${t.features}`).join('\n');
  const { response, checkboxChecked } = await dialog.showMessageBox({
    type: 'info',
    title: 'Some PDF tools need helper programs',
    message: 'Some tools will not work until helper programs are installed.',
    detail: `${list}\n\nInstall them with:\n${command}\n\nThen restart Kagaz PDF. The Docker version includes all of them.`,
    buttons: ['Copy install command', 'Continue'],
    defaultId: 1,
    checkboxLabel: "Don't show this again",
  });
  if (response === 0) require('electron').clipboard.writeText(command);
  if (checkboxChecked) fs.writeFileSync(flag, '1');
}

let server = null;

async function createWindow() {
  const serverDir = app.isPackaged
    ? path.join(process.resourcesPath, 'server')
    : path.join(__dirname, '..', 'resources');
  try {
    server = await startServer({ serverDir, dataDir: path.join(app.getPath('userData'), 'data') });
  } catch (e) {
    dialog.showErrorBox('Kagaz PDF could not start', String(e.message || e));
    app.quit();
    return;
  }
  const origin = `http://127.0.0.1:${server.port}`;
  const win = new BrowserWindow({
    width: 1400, height: 900, title: 'Kagaz PDF',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  // Anything that leaves the local server opens in the system browser, never in this window.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (!url.startsWith(origin)) shell.openExternal(url);
    return { action: url.startsWith(origin) ? 'allow' : 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(origin)) { event.preventDefault(); shell.openExternal(url); }
  });
  await win.loadURL(origin);
  await warnAboutMissingTools(serverDir);
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { if (server) server.stop(); });
