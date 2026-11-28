'use strict';
const net = require('node:net');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { serverEnv } = require('./tools');

/** Asks the OS for a free loopback port. */
function freePort(preferred = 0) {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.once('error', (e) => (preferred ? freePort(0).then(resolve, reject) : reject(e)));
    srv.listen(preferred, '127.0.0.1', () => {
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

/** Last part of the server log, for showing why startup failed. */
function logTail(logPath, maxChars = 1200) {
  try {
    const text = fs.readFileSync(logPath, 'utf8');
    return text.slice(-maxChars).trim();
  } catch {
    return '';
  }
}

/**
 * JVM arguments. Default is loopback-only with login off (single local user). With `lan` set the
 * server listens on all interfaces, so login is on with the given admin password. `login` alone
 * keeps the loopback binding but turns login on, for the administrator console.
 */
function buildArgs({ jar, port, dataDir, lan = false, login = lan, signup = login, adminPassword = '' }) {
  return [
    '-Xmx1g',
    `-Dserver.port=${port}`,
    `-Dserver.address=${lan ? '0.0.0.0' : '127.0.0.1'}`,
    `-Dsecurity.enableLogin=${login ? 'true' : 'false'}`,
    ...(login ? [`-Dkagaz.auth.signup.enabled=${signup}`] : []),
    ...(login && adminPassword ? [`-Dkagaz.auth.admin-password=${adminPassword}`] : []),
    `-Dkagaz.base-path=${dataDir}${path.sep}`,
    '-jar',
    jar,
  ];
}

/** Resolves once the server answers any HTTP status below 500 (a login-protected server answers 401), rejects after timeoutMs. */
function waitForServer(port, timeoutMs = 240000, intervalMs = 500, isCancelled = () => false) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const req = http.get({ host: '127.0.0.1', port, path: '/api/v1/info/status', timeout: 2000 }, (res) => {
        res.resume();
        if (res.statusCode < 500) return resolve();
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

async function startServer({ serverDir, dataDir, lan = false, login = lan, signup = login, adminPassword = '', preferredPort = 0 }) {
  const jar = path.join(serverDir, 'kagaz-pdf.jar');
  if (!fs.existsSync(jar)) throw new Error(`Server jar not found: ${jar}`);
  fs.mkdirSync(dataDir, { recursive: true });
  const port = await freePort(lan ? preferredPort : 0);
  const logPath = path.join(dataDir, 'server.log');
  const logFd = fs.openSync(logPath, 'w');
  const child = spawn(javaBinary(serverDir), buildArgs({ jar, port, dataDir, lan, login, signup, adminPassword }), {
    cwd: dataDir,
    env: serverEnv(process.platform, process.env, serverDir),
    stdio: ['ignore', logFd, logFd],
    windowsHide: true,
  });
  const withLog = (message) => {
    const tail = logTail(logPath);
    return `${message}\n\nLog file: ${logPath}${tail ? `\n\n${tail}` : ''}`;
  };
  let dead = false;
  const exited = new Promise((_, reject) => {
    child.once('exit', (code) => { dead = true; reject(new Error(withLog(`Server exited early (code ${code}).`))); });
    child.once('error', (e) => { dead = true; reject(new Error(withLog(`Cannot start Java: ${e.message}.`))); });
  });
  try {
    await Promise.race([waitForServer(port, 240000, 500, () => dead), exited]);
  } catch (e) {
    child.kill();
    throw e.message.includes('Log file:') ? e : new Error(withLog(e.message));
  }
  return { port, stop: () => child.kill() };
}

module.exports = { freePort, javaBinary, buildArgs, waitForServer, startServer, logTail };
