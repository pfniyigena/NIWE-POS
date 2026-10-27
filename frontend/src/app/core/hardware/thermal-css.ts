/**
 * Print rules for a roll of thermal paper, used when a receipt is printed through the Windows (Epson) driver:
 * the page is exactly the roll width, has no margin and no minimum height (no blank paper after the receipt),
 * and everything is black and bold, because grey text prints faint on thermal paper.
 */
export function thermalPrintCss(paper: '80' | '58', host: string, box = '.receipt'): string {
  const page = paper === '58' ? '58mm' : '80mm';
  const w = paper === '58' ? '50mm' : '72mm';
  const fs = paper === '58' ? '11px' : '12px';
  return (
    `@media print {` +
    ` @page { size: ${page} auto; margin: 0; }` +
    ` html, body { margin: 0 !important; padding: 0 !important; background: #fff !important; width: ${page} !important; }` +
    ` ${host} { display: block !important; min-height: 0 !important; background: #fff !important; }` +
    ` ${host} ${box} { width: ${w} !important; max-width: ${w} !important; margin: 0 auto !important; padding: 1mm 1mm 10mm !important; font-size: ${fs} !important; line-height: 1.25 !important; font-weight: 700 !important; color: #000 !important; }` +
    ` ${host} ${box} * { color: #000 !important; font-size: inherit; }` +
    ` ${host} ${box} h3 { font-size: 1.25em !important; }` +
    ` ${host} ${box} table { width: 100% !important; border-collapse: collapse; }` +
    ` ${host} ${box} img { filter: grayscale(1) contrast(1.6); image-rendering: pixelated; }` +
    ` ${host} ${box} hr { border-top: 1px dashed #000 !important; margin: 4px 0 !important; }` +
    ` .no-print { display: none !important; }` +
    ` }`
  );
}

/** Adds (or replaces) the print style of the current page and returns the element so the page can remove it again. */
export function applyThermalPrintStyle(paper: '80' | '58', host: string, previous: HTMLStyleElement | null = null, box = '.receipt'): HTMLStyleElement {
  previous?.remove();
  const el = document.createElement('style');
  el.setAttribute('data-thermal-print', paper);
  el.textContent = thermalPrintCss(paper, host, box);
  document.head.appendChild(el);
  return el;
}
