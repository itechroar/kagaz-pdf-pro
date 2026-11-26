'use strict';
const net = require('node:net');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

/** Asks the OS for a free loopback port. */
function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.once('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

/** Bundled JRE if present, else `java` from PATH. */
function javaBinary(serverDir) {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const bundled = path.join(serverDir, 'jre', 'bin', exe);
  return fs.existsSync(bundled) ? bundled : 'java';
}

function buildArgs({ jar, port, dataDir }) {
  return [
    '-Xmx1g',
    `-Dserver.port=${port}`,
    '-Dserver.address=127.0.0.1',
    `-Dkagaz.base-path=${dataDir}${path.sep}`,
    '-jar',
    jar,
  ];
}

/** Resolves once GET /api/v1/info/status answers 200, rejects after timeoutMs. */
function waitForServer(port, timeoutMs = 120000, intervalMs = 500, isCancelled = () => false) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const req = http.get({ host: '127.0.0.1', port, path: '/api/v1/info/status', timeout: 2000 }, (res) => {
        res.resume();
        if (res.statusCode === 200) return resolve();
        retry();
      });
      req.on('error', retry);
      req.on('timeout', () => req.destroy());
    };
    const retry = () => {
      if (isCancelled()) return reject(new Error('cancelled'));
      if (Date.now() > deadline) return reject(new Error('Server did not start in time'));
      setTimeout(attempt, intervalMs);
    };
    attempt();
  });
}

async function startServer({ serverDir, dataDir }) {
  const jar = path.join(serverDir, 'kagaz-pdf.jar');
  if (!fs.existsSync(jar)) throw new Error(`Server jar not found: ${jar}`);
  fs.mkdirSync(dataDir, { recursive: true });
  const port = await freePort();
  const child = spawn(javaBinary(serverDir), buildArgs({ jar, port, dataDir }), {
    cwd: dataDir,
    env: { ...process.env },
    stdio: 'ignore',
    windowsHide: true,
  });
  let dead = false;
  const exited = new Promise((_, reject) => {
    child.once('exit', (code) => { dead = true; reject(new Error(`Server exited early (code ${code}). Is Java 25 installed?`)); });
    child.once('error', (e) => { dead = true; reject(new Error(`Cannot start Java: ${e.message}. Install Java 25 or bundle a JRE.`)); });
  });
  await Promise.race([waitForServer(port, 120000, 500, () => dead), exited]);
  return { port, stop: () => child.kill() };
}

module.exports = { freePort, javaBinary, buildArgs, waitForServer, startServer };
