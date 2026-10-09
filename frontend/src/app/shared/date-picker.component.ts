import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, forwardRef, HostListener, Input, Output } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

interface DayCell {
  iso: string; // 'YYYY-MM-DD'
  day: number;
  otherMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
  disabled: boolean;
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const MONTH_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAY_SHORT = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

/**
 * Fourier: "Can we have a good input date UI for all date input? This one is hard to use the one
 * we have now" — a drop-in replacement for every `<input type="date">` across the app. Wraps the
 * same plain 'YYYY-MM-DD' string value the native date input used (via ngModel, exactly like
 * before — `[(ngModel)]`, `name`, and `required` on this element all keep working unchanged),
 * so swapping the tag is the only change needed anywhere a date field already exists; nothing
 * downstream (submit payloads, `new Date(this.expectedDate)` conversions, etc.) needed to change.
 *
 * Displays the date written out ("25 Sep 2026" — Fourier's chosen format) instead of the native
 * control's cramped, easy-to-mis-click segments, and opens a proper calendar dropdown: month/year
 * quick-jump selects (for flipping years back on accounting reports without dozens of clicks),
 * a day grid, and Today/Clear shortcuts. Implements ControlValueAccessor so it's a real Angular
 * form control, not a lookalike.
 */
@Component({
  selector: 'app-date-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DatePickerComponent),
      multi: true,
    },
  ],
  template: `
    <div class="dp-wrap">
      <div
        class="form-control dp-trigger"
        [class.dp-disabled]="disabled"
        tabindex="0"
        (click)="toggle()"
        (keydown.enter)="toggle()"
        (keydown.space)="$event.preventDefault(); toggle()"
      >
        <span [class.dp-placeholder]="!value">{{ displayText() }}</span>
        <span class="dp-icon">📅</span>
      </div>

      <div class="dp-panel" *ngIf="open" role="dialog" aria-label="Choose a date">
        <div class="dp-header">
          <button type="button" class="dp-nav" (click)="shiftMonth(-1)" aria-label="Previous month">‹</button>
          <div class="dp-header-selects">
            <select class="dp-select" [ngModel]="viewMonth" (ngModelChange)="onMonthSelect($event)">
              <option *ngFor="let m of monthNames; let i = index" [ngValue]="i">{{ m }}</option>
            </select>
            <select class="dp-select" [ngModel]="viewYear" (ngModelChange)="onYearSelect($event)">
              <option *ngFor="let y of yearOptions" [ngValue]="y">{{ y }}</option>
            </select>
          </div>
          <button type="button" class="dp-nav" (click)="shiftMonth(1)" aria-label="Next month">›</button>
        </div>

        <div class="dp-weekdays">
          <span *ngFor="let w of weekdayNames">{{ w }}</span>
        </div>

        <div class="dp-grid">
          <button
            type="button"
            *ngFor="let cell of cells"
            class="dp-day"
            [class.dp-other-month]="cell.otherMonth"
            [class.dp-today]="cell.isToday"
            [class.dp-selected]="cell.isSelected"
            [disabled]="cell.disabled"
            (click)="pick(cell)"
          >
            {{ cell.day }}
          </button>
        </div>

        <div class="dp-footer">
          <button type="button" class="dp-link" (click)="goToday()">Today</button>
          <button type="button" class="dp-link" (click)="clear()">Clear</button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .dp-wrap {
        position: relative;
      }
      .dp-trigger {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        cursor: pointer;
        user-select: none;
      }
      .dp-trigger:focus {
        outline: none;
        border-color: var(--accent);
        box-shadow: 0 0 0 0.2rem rgba(47, 190, 114, 0.25);
      }
      .dp-disabled {
        opacity: 0.6;
        cursor: not-allowed;
        pointer-events: none;
      }
      .dp-placeholder {
        color: var(--muted);
      }
      .dp-icon {
        font-size: 0.9rem;
        opacity: 0.8;
      }
      .dp-panel {
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        z-index: 30;
        width: 280px;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
        padding: 0.6rem;
      }
      .dp-header {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        margin-bottom: 0.5rem;
      }
      .dp-header-selects {
        flex: 1;
        display: flex;
        gap: 0.35rem;
      }
      .dp-select {
        flex: 1;
        min-width: 0;
        padding: 0.3rem 0.35rem;
        border-radius: 0.375rem;
        border: 1px solid var(--border);
        background: var(--bg);
        color: var(--text);
        font-size: 0.85rem;
      }
      .dp-nav {
        background: var(--surface-alt);
        border: 1px solid var(--border);
        color: var(--text);
        border-radius: 0.375rem;
        width: 1.8rem;
        height: 1.8rem;
        line-height: 1;
        font-size: 1rem;
        cursor: pointer;
      }
      .dp-nav:hover {
        background: var(--accent);
        color: var(--accent-contrast);
        border-color: var(--accent);
      }
      .dp-weekdays {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        text-align: center;
        color: var(--muted);
        font-size: 0.75rem;
        margin-bottom: 0.2rem;
      }
      .dp-grid {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        gap: 2px;
      }
      .dp-day {
        background: transparent;
        border: none;
        color: var(--text);
        border-radius: 0.375rem;
        padding: 0.35rem 0;
        font-size: 0.85rem;
        cursor: pointer;
      }
      .dp-day:hover:not(:disabled) {
        background: var(--surface-alt);
      }
      .dp-day:disabled {
        color: var(--muted);
        opacity: 0.4;
        cursor: not-allowed;
      }
      .dp-other-month {
        color: var(--muted);
      }
      .dp-today {
        box-shadow: inset 0 0 0 1px var(--accent);
      }
      .dp-selected {
        background: var(--accent) !important;
        color: var(--accent-contrast) !important;
      }
      .dp-footer {
        display: flex;
        justify-content: space-between;
        margin-top: 0.5rem;
        padding-top: 0.4rem;
        border-top: 1px solid var(--border);
      }
      .dp-link {
        background: none;
        border: none;
        color: var(--accent);
        font-size: 0.85rem;
        cursor: pointer;
        padding: 0.2rem 0.3rem;
      }
      .dp-link:hover {
        text-decoration: underline;
      }
    `,
  ],
})
export class DatePickerComponent implements ControlValueAccessor {
  @Input() placeholder = 'Select date';
  @Input() min: string | null = null;
  @Input() max: string | null = null;
  @Output() dateChange = new EventEmitter<string | null>();

  value: string | null = null;
  disabled = false;
  open = false;

  viewYear = new Date().getFullYear();
  viewMonth = new Date().getMonth();
  cells: DayCell[] = [];

  readonly monthNames = MONTH_NAMES;
  readonly weekdayNames = WEEKDAY_SHORT;
  readonly yearOptions: number[];

  private onChange: (v: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private elementRef: ElementRef<HTMLElement>) {
    const thisYear = new Date().getFullYear();
    this.yearOptions = [];
    for (let y = thisYear - 20; y <= thisYear + 5; y++) this.yearOptions.push(y);
    this.buildGrid();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open && !this.elementRef.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open) this.close();
  }

  writeValue(value: string | null): void {
    this.value = value || null;
    const parsed = this.parseIso(this.value);
    const today = new Date();
    this.viewYear = parsed ? parsed.y : today.getFullYear();
    this.viewMonth = parsed ? parsed.m : today.getMonth();
    this.buildGrid();
  }

  registerOnChange(fn: (v: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  toggle(): void {
    if (this.disabled) return;
    this.open ? this.close() : this.openPanel();
  }

  openPanel(): void {
    const parsed = this.parseIso(this.value);
    const today = new Date();
    this.viewYear = parsed ? parsed.y : today.getFullYear();
    this.viewMonth = parsed ? parsed.m : today.getMonth();
    this.buildGrid();
    this.open = true;
  }

  close(): void {
    this.open = false;
    this.onTouched();
  }

  shiftMonth(delta: number): void {
    let m = this.viewMonth + delta;
    let y = this.viewYear;
    if (m < 0) {
      m = 11;
      y--;
    } else if (m > 11) {
      m = 0;
      y++;
    }
    this.viewMonth = m;
    this.viewYear = y;
    this.buildGrid();
  }

  onMonthSelect(m: number): void {
    this.viewMonth = m;
    this.buildGrid();
  }

  onYearSelect(y: number): void {
    this.viewYear = y;
    this.buildGrid();
  }

  pick(cell: DayCell): void {
    if (cell.disabled) return;
    this.value = cell.iso;
    this.onChange(this.value);
    this.dateChange.emit(this.value);
    this.onTouched();
    this.close();
  }

  goToday(): void {
    const today = new Date();
    const iso = this.formatIso(today.getFullYear(), today.getMonth(), today.getDate());
    this.value = iso;
    this.viewYear = today.getFullYear();
    this.viewMonth = today.getMonth();
    this.buildGrid();
    this.onChange(this.value);
    this.dateChange.emit(this.value);
    this.onTouched();
    this.close();
  }

  clear(): void {
    this.value = null;
    this.onChange(null);
    this.dateChange.emit(null);
    this.onTouched();
    this.close();
  }

  displayText(): string {
    const parsed = this.parseIso(this.value);
    if (!parsed) return this.placeholder;
    return `${parsed.d} ${MONTH_SHORT[parsed.m]} ${parsed.y}`;
  }

  private buildGrid(): void {
    const firstOfMonth = new Date(this.viewYear, this.viewMonth, 1);
    const startWeekday = firstOfMonth.getDay(); // 0 = Sunday
    const daysInMonth = new Date(this.viewYear, this.viewMonth + 1, 0).getDate();
    const daysInPrevMonth = new Date(this.viewYear, this.viewMonth, 0).getDate();

    const today = new Date();
    const todayIso = this.formatIso(today.getFullYear(), today.getMonth(), today.getDate());
    const minIso = this.min;
    const maxIso = this.max;

    const cells: DayCell[] = [];

    // Leading days from the previous month, so week rows always line up under the right weekday.
    for (let i = 0; i < startWeekday; i++) {
      const day = daysInPrevMonth - startWeekday + 1 + i;
      const m = this.viewMonth === 0 ? 11 : this.viewMonth - 1;
      const y = this.viewMonth === 0 ? this.viewYear - 1 : this.viewYear;
      const iso = this.formatIso(y, m, day);
      cells.push({ iso, day, otherMonth: true, isToday: iso === todayIso, isSelected: iso === this.value, disabled: this.outOfRange(iso, minIso, maxIso) });
    }

    for (let day = 1; day <= daysInMonth; day++) {
      const iso = this.formatIso(this.viewYear, this.viewMonth, day);
      cells.push({ iso, day, otherMonth: false, isToday: iso === todayIso, isSelected: iso === this.value, disabled: this.outOfRange(iso, minIso, maxIso) });
    }

    // Trailing days from the next month, padded to full weeks (max 6 rows covers every month).
    const remainder = cells.length % 7;
    if (remainder !== 0) {
      const toAdd = 7 - remainder;
      const m = this.viewMonth === 11 ? 0 : this.viewMonth + 1;
      const y = this.viewMonth === 11 ? this.viewYear + 1 : this.viewYear;
      for (let day = 1; day <= toAdd; day++) {
        const iso = this.formatIso(y, m, day);
        cells.push({ iso, day, otherMonth: true, isToday: iso === todayIso, isSelected: iso === this.value, disabled: this.outOfRange(iso, minIso, maxIso) });
      }
    }

    this.cells = cells;
  }

  private outOfRange(iso: string, min: string | null, max: string | null): boolean {
    if (min && iso < min) return true;
    if (max && iso > max) return true;
    return false;
  }

  private parseIso(v: string | null): { y: number; m: number; d: number } | null {
    if (!v) return null;
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v);
    if (!match) return null;
    return { y: +match[1], m: +match[2] - 1, d: +match[3] };
  }

  private formatIso(y: number, m: number, d: number): string {
    const mm = String(m + 1).padStart(2, '0');
    const dd = String(d).padStart(2, '0');
    return `${y}-${mm}-${dd}`;
  }
}
