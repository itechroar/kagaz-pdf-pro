'use strict';
const { app, BrowserWindow, dialog, shell } = require('electron');
const path = require('node:path');
const { startServer } = require('./server');

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
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { if (server) server.stop(); });
