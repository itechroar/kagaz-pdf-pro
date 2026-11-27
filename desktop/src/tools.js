'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

/**
 * Helper programs the server shells out to. Each feature silently disables itself when its tool is
 * not on PATH, so the desktop app checks for them at launch and tells the user what is missing.
 */
const TOOLS = [
  { id: 'gs', names: { win32: ['gswin64c', 'gs'], other: ['gs'] }, label: 'Ghostscript', features: 'Repair, Replace & invert colour, Scanner effect, Crop' },
  { id: 'qpdf', names: { win32: ['qpdf'], other: ['qpdf'] }, label: 'qpdf', features: 'Repair, Compress' },
  { id: 'tesseract', names: { win32: ['tesseract'], other: ['tesseract'] }, label: 'Tesseract', features: 'OCR, Auto rotate' },
  { id: 'ocrmypdf', names: { win32: ['ocrmypdf'], other: ['ocrmypdf'] }, label: 'OCRmyPDF', features: 'OCR / Cleanup files' },
  { id: 'unpaper', names: { win32: [], other: ['unpaper'] }, label: 'unpaper', features: 'Cleanup files' },
];

const INSTALL_COMMANDS = {
  win32: 'winget install ArtifexSoftware.GhostScript QPDF.QPDF UB-Mannheim.TesseractOCR Python.Python.3.12 && pip install ocrmypdf',
  darwin: 'brew install ghostscript qpdf tesseract tesseract-lang ocrmypdf unpaper',
  linux: 'sudo apt install ghostscript qpdf tesseract-ocr ocrmypdf unpaper',
};

/**
 * PATH entries to add so tools installed in their usual places are found. A GUI app started from
 * Finder or the Start menu does not inherit the shell PATH, which hides Homebrew and Program Files tools.
 */
function extraPathDirs(platform, env, serverDir) {
  const dirs = [];
  if (serverDir) dirs.push(path.join(serverDir, 'shims'));
  if (platform === 'darwin') dirs.push('/opt/homebrew/bin', '/opt/homebrew/sbin', '/usr/local/bin', '/usr/local/sbin');
  if (platform === 'win32') {
    const pf = env.ProgramFiles || 'C:\\Program Files';
    dirs.push(path.join(pf, 'Tesseract-OCR'), path.join(pf, 'qpdf', 'bin'));
    try {
      for (const v of fs.readdirSync(path.join(pf, 'gs'))) dirs.push(path.join(pf, 'gs', v, 'bin'));
    } catch { /* Ghostscript not installed */ }
    if (env.LOCALAPPDATA) dirs.push(path.join(env.LOCALAPPDATA, 'Programs', 'Python', 'Python312', 'Scripts'));
  }
  return dirs;
}

/** Environment for the server process with the extra tool directories appended to PATH. */
function serverEnv(platform, env, serverDir) {
  const key = Object.keys(env).find((k) => k.toLowerCase() === 'path') || 'PATH';
  const merged = [env[key], ...extraPathDirs(platform, env, serverDir)].filter(Boolean).join(path.delimiter);
  return { ...env, [key]: merged };
}

function findOnPath(names, env, platform) {
  const key = Object.keys(env).find((k) => k.toLowerCase() === 'path') || 'PATH';
  const exts = platform === 'win32' ? ['.exe', '.cmd', '.bat', ''] : [''];
  for (const dir of (env[key] || '').split(path.delimiter).filter(Boolean)) {
    for (const name of names) {
      for (const ext of exts) {
        if (fs.existsSync(path.join(dir, name + ext))) return true;
      }
    }
  }
  return false;
}

/** Tools from TOOLS that cannot be found in the given (already extended) environment. */
function missingTools(env, platform) {
  return TOOLS.filter((t) => {
    const names = t.names[platform] || t.names.other;
    return names.length > 0 && !findOnPath(names, env, platform);
  });
}

function installCommand(platform) {
  return INSTALL_COMMANDS[platform] || INSTALL_COMMANDS.linux;
}

const WINDOWS_INSTALL_SCRIPT = [
  'winget install -e --id ArtifexSoftware.GhostScript --accept-package-agreements --accept-source-agreements',
  'winget install -e --id QPDF.QPDF --accept-package-agreements --accept-source-agreements',
  'winget install -e --id UB-Mannheim.TesseractOCR --accept-package-agreements --accept-source-agreements',
  'winget install -e --id Python.Python.3.12 --accept-package-agreements --accept-source-agreements',
  'py -3.12 -m pip install ocrmypdf',
  'Write-Host "Done. Restart Kagaz PDF."',
].join('; ');

/** Opens a terminal that runs the platform's install command so the user sees progress and can answer password prompts. */
function installInTerminal(platform, env) {
  const detach = { detached: true, stdio: 'ignore' };
  if (platform === 'win32') {
    spawn('cmd.exe', ['/c', 'start', 'Kagaz PDF helper install', 'powershell', '-NoExit', '-Command', WINDOWS_INSTALL_SCRIPT], detach).unref();
    return true;
  }
  const command = `${installCommand(platform)}; echo; echo "Done. Restart Kagaz PDF."; read -r -p "Press Enter to close"`;
  if (platform === 'darwin') {
    const script = `tell application "Terminal" to do script "${command.replace(/"/g, '\\"')}"`;
    spawn('osascript', ['-e', script, '-e', 'tell application "Terminal" to activate'], detach).unref();
    return true;
  }
  const terminals = [['x-terminal-emulator', ['-e']], ['gnome-terminal', ['--']], ['konsole', ['-e']], ['xterm', ['-e']]];
  for (const [bin, args] of terminals) {
    if (findOnPath([bin], env, platform)) {
      spawn(bin, [...args, 'bash', '-c', command], detach).unref();
      return true;
    }
  }
  return false;
}

module.exports = { installInTerminal, TOOLS, extraPathDirs, serverEnv, missingTools, installCommand, findOnPath };
