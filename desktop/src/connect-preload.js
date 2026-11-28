'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('connectApi', {
  submit: (url) => ipcRenderer.invoke('connect-server', url),
});
