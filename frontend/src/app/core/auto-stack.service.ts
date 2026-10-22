import { Injectable, NgZone } from '@angular/core';

/**
 * Turns every plain data table in the app into phone cards (see "phone cards for tables" in
 * styles.scss) without each screen having to opt in: it copies each column title onto its cells as
 * `data-label` and adds the `stack` class. The CSS only acts on a phone-width screen, so on a
 * computer nothing changes.
 *
 * <p>A table is left alone when it opts out (`no-stack`), sits inside the till, checkout, receipt or
 * customer display, has no single header row, holds another table, or has data rows whose cell count
 * does not match its header (it keeps scrolling sideways instead). Re-checked whenever the page
 * changes, so rows added later (a list loading, the next page) are labelled too.
 */
@Injectable({ providedIn: 'root' })
export class AutoStackService {
  private started = false;
  private scheduled = false;

  private static readonly SKIP_INSIDE = [
    '.no-stack',
    '.pos-terminal-layout',
    '.checkout-shell',
    '.checkout-card',
    '.receipt',
    '.receipt-print',
    '.receipt-print',
    '.customer-display',
    '.print-area',
  ].join(',');

  constructor(private zone: NgZone) {}

  start(): void {
    if (this.started || typeof document === 'undefined' || typeof MutationObserver === 'undefined') return;
    this.started = true;
    this.zone.runOutsideAngular(() => {
      const observer = new MutationObserver(() => this.schedule());
      observer.observe(document.body, { childList: true, subtree: true });
      this.schedule();
    });
  }

  private schedule(): void {
    if (this.scheduled) return;
    this.scheduled = true;
    requestAnimationFrame(() => {
      this.scheduled = false;
      document.querySelectorAll('table').forEach((t) => this.refresh(t as HTMLTableElement));
    });
  }

  /** Labels one table, or takes the class off again when it no longer fits the pattern. */
  refresh(table: HTMLTableElement): void {
    if (table.classList.contains('no-stack') || table.closest(AutoStackService.SKIP_INSIDE)) return;
    // a screen that built its own phone cards (class "stack" written in its template) keeps its own labels
    if (table.classList.contains('stack') && table.getAttribute('data-auto-stack') !== '1') return;
    const labels = this.headerLabels(table);
    const rows = Array.from(table.tBodies).flatMap((b) => Array.from(b.rows)).concat(table.tFoot ? Array.from(table.tFoot.rows) : []);
    if (!labels || table.querySelector('table')) {
      this.release(table);
      return;
    }
    let consistent = true;
    for (const row of rows) {
      const cells = Array.from(row.cells);
      // a single wide cell (the "nothing here" row) is fine
      if (cells.length === 1 && cells[0].colSpan > 1) continue;
      if (cells.length !== labels.length || cells.some((c) => c.colSpan > 1)) {
        consistent = false;
        break;
      }
    }
    if (!consistent) {
      this.release(table);
      return;
    }
    for (const row of rows) {
      const cells = Array.from(row.cells);
      if (cells.length === 1 && cells[0].colSpan > 1) {
        if (cells[0].getAttribute('data-label') !== '') cells[0].setAttribute('data-label', '');
        continue;
      }
      cells.forEach((c, i) => {
        if (c.getAttribute('data-label') !== labels[i]) c.setAttribute('data-label', labels[i]);
      });
    }
    if (!table.classList.contains('stack')) {
      table.classList.add('stack');
      table.setAttribute('data-auto-stack', '1');
    }
  }

  private release(table: HTMLTableElement): void {
    if (table.getAttribute('data-auto-stack') === '1') {
      table.classList.remove('stack');
      table.removeAttribute('data-auto-stack');
    }
  }

  /** Column titles from the one header row; null when there is no single, plain header row. */
  private headerLabels(table: HTMLTableElement): string[] | null {
    const head = table.tHead;
    if (!head || head.rows.length !== 1) return null;
    const cells = Array.from(head.rows[0].cells);
    if (cells.length === 0 || cells.some((c) => c.colSpan > 1)) return null;
    return cells.map((c) => (c.textContent ?? '').replace(/\s+/g, ' ').trim());
  }
}
