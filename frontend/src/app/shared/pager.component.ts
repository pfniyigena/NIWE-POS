import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

/** Previous / next for a paged list. Shows nothing when everything fits on one page. {@code page} counts from 0. */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pager" *ngIf="total > 0">
      <span class="text-muted">{{ from }}–{{ to }} of {{ total }}</span>
      <span class="flex gap-2" *ngIf="total > size">
        <button class="btn btn-sm btn-outline-secondary" type="button" [disabled]="page <= 0" (click)="go(page - 1)">‹ Previous</button>
        <span class="text-muted" style="align-self:center;">Page {{ page + 1 }} of {{ pages }}</span>
        <button class="btn btn-sm btn-outline-secondary" type="button" [disabled]="page + 1 >= pages" (click)="go(page + 1)">Next ›</button>
      </span>
    </div>
  `,
})
export class PagerComponent {
  @Input() page = 0;
  @Input() size = 25;
  @Input() total = 0;
  @Output() pageChange = new EventEmitter<number>();

  get pages(): number { return Math.max(1, Math.ceil(this.total / this.size)); }
  get from(): number { return this.total === 0 ? 0 : this.page * this.size + 1; }
  get to(): number { return Math.min(this.total, (this.page + 1) * this.size); }

  go(p: number): void { this.pageChange.emit(Math.max(0, Math.min(this.pages - 1, p))); }
}
