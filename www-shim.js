// Mist media-action shim - include AFTER index.html loads if notification buttons don't respond.
// Maps MainActivity's window event to the same handlers index.html already uses.
window.addEventListener('mist-media-action', (e) => {
  const a = e.detail;
  if (a === 'play') { try { window.mediaToggle && window.mediaToggle(); } catch(_){} }
  if (a === 'pause') { try { window.mistPause && window.mistPause(); } catch(_){} }
  if (a === 'next') { try { window.mistNext && window.mistNext(); } catch(_){} }
  if (a === 'prev') { try { window.mistPrev && window.mistPrev(); } catch(_){} }
});
