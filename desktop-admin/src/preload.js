'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('admin', {
  onState: (callback) => ipcRenderer.on('state', (_event, state) => callback(state)),
  ready: () => ipcRenderer.send('ready'),
  select: (id) => ipcRenderer.send('select', id),
  reload: () => ipcRenderer.send('reload'),
  setup: (settings) => ipcRenderer.invoke('setup', settings),
  changeServer: () => ipcRenderer.send('change-server'),
});
