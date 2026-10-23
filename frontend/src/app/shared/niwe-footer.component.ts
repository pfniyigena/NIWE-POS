import { Component, Input } from '@angular/core';

/** The copyright line shown at the bottom of every screen and public page. */
@Component({
  selector: 'app-niwe-footer',
  standalone: true,
  template: `<div class="niwe-footer" [class.niwe-footer-fixed]="fixed">&copy; 2026 NIWE GROUP</div>`,
  styles: [`
    .niwe-footer { text-align: center; font-size: 0.75rem; color: var(--muted); padding: 0.75rem 0.5rem; }
    .niwe-footer-fixed { position: fixed; left: 0; right: 0; bottom: 0; padding: 0.35rem 0.5rem; pointer-events: none; }
    @media print { .niwe-footer { display: none; } }
  `],
})
export class NiweFooterComponent {
  /** Pinned to the bottom of the window (for the centred sign-in and public pages) instead of following the page content. */
  @Input() fixed = false;
}
