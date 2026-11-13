const { contextBridge, ipcRenderer } = require('electron');
const { pathToFileURL } = require('url');
contextBridge.exposeInMainWorld('mistDesk', {
  pick: () => ipcRenderer.invoke('mist:pick'),
  scan: (d) => ipcRenderer.invoke('mist:scan', d),
  read: (p) => ipcRenderer.invoke('mist:read', p),
  head: async (p, s, l) => {
    const u = await ipcRenderer.invoke('mist:head', p, s, l);
    return u.buffer.slice(u.byteOffset, u.byteOffset + u.byteLength);
  },
  url: (p) => pathToFileURL(p).href,
  watch: (dirs) => ipcRenderer.invoke('mist:watch', dirs),
  onChange: (cb) => ipcRenderer.on('mist:changed', () => cb()),
  toggleFull: () => ipcRenderer.invoke('mist:fullscreen')
});
