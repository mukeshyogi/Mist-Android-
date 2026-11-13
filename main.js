const { app, BrowserWindow, Menu, globalShortcut, dialog, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

let win;
const AUDIO = /\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma|weba)$/i;

function create() {
  win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 380, minHeight: 560, show: false,
    icon: path.join(__dirname, 'icon.png'), backgroundColor: '#f4f1ea',
    webPreferences: { backgroundThrottling: false, preload: path.join(__dirname, 'preload.js') }
  });
  Menu.setApplicationMenu(null);
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  // F11 = full screen
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
}

/* ---- file access for linked folders ---- */
async function walk(dir, out, depth) {
  let ents;
  try { ents = await fs.promises.readdir(dir, { withFileTypes: true }); } catch (e) { return; }
  for (const e of ents) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { if (depth < 12 && !e.name.startsWith('.')) await walk(p, out, depth + 1); }
    else if (AUDIO.test(e.name)) {
      try { const st = await fs.promises.stat(p); out.push({ path: p, name: e.name, size: st.size, mtime: Math.floor(st.mtimeMs) }); } catch (x) {}
    }
  }
}
ipcMain.handle('mist:pick', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Choose your music folder', properties: ['openDirectory'] });
  return r.canceled || !r.filePaths.length ? null : r.filePaths[0];
});
ipcMain.handle('mist:scan', async (_e, dir) => {
  let ok = false;
  try { ok = fs.statSync(dir).isDirectory(); } catch (e) {}
  const files = [];
  if (ok) await walk(dir, files, 0);
  return { ok, files };
});
ipcMain.handle('mist:read', (_e, p) => fs.promises.readFile(p));
ipcMain.handle('mist:head', async (_e, p, start, len) => {
  const fh = await fs.promises.open(p, 'r');
  try {
    const buf = Buffer.alloc(Math.max(0, len));
    const { bytesRead } = await fh.read(buf, 0, buf.length, start);
    return buf.subarray(0, bytesRead);
  } finally { await fh.close(); }
});
let watchers = [], wt = null;
ipcMain.handle('mist:watch', (_e, dirs) => {
  watchers.forEach(w => { try { w.close(); } catch (e) {} });
  watchers = [];
  (dirs || []).forEach(d => {
    try {
      const w = fs.watch(d, { recursive: true, persistent: true }, () => {
        clearTimeout(wt);
        wt = setTimeout(() => { if (win && !win.isDestroyed()) win.webContents.send('mist:changed'); }, 2500);
      });
      w.on('error', () => {});
      watchers.push(w);
    } catch (e) {}
  });
  return true;
});
ipcMain.handle('mist:fullscreen', () => { win.setFullScreen(!win.isFullScreen()); return win.isFullScreen(); });

const run = (js) => { if (win && !win.isDestroyed()) win.webContents.executeJavaScript(js).catch(() => {}); };
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();
else {
  app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(() => {
    create();
    // play/pause, next and previous keys work even when Mist is in the background
    globalShortcut.register('MediaPlayPause', () => run('mediaToggle()'));
    globalShortcut.register('MediaNextTrack', () => run('mediaGate()&&mistNext()'));
    globalShortcut.register('MediaPreviousTrack', () => run('mediaGate()&&mistPrev()'));
    globalShortcut.register('MediaStop', () => run('mistPause()'));
  });
}
app.on('will-quit', () => globalShortcut.unregisterAll());
app.on('window-all-closed', () => app.quit());
