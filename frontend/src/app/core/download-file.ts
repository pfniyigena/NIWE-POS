/**
 * Triggers the browser's own "save as" for a blob already fetched over HTTP — shared by every
 * report's "Export Excel"/"Export PDF" buttons (Fourier: "Export to Excel and Pdf") rather than
 * copy-pasted into each report component.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Opens an already-fetched PDF blob in a new browser tab — Fourier: "add print (PDF with A4)" on
 * the Sale Order and Invoice detail screens. Deliberately a tab, not a download: the browser's own
 * PDF viewer opens straight to the real, A4-paginated document (built server-side — see the
 * backend's DocumentPdfService), and its own print control/Ctrl+P prints exactly what's on screen,
 * same as opening any other PDF. The object URL is intentionally not revoked immediately — the new
 * tab is still reading it; letting it leak for the life of that tab is the standard tradeoff here,
 * same as every other "preview a blob in a new tab" flow.
 */
export function openPdfForPrint(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
}
