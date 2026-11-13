import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShopPayProvider } from '../core/api/online-store-api.service';

/**
 * Lets the customer choose which provider to pay with (MTN, Airtel, ...). Shows nothing when there is only one: the choice is then made for them.
 * A logo file named assets/payment/<code in lower case>.png (for example mtn_momo.png, airtel_money.png) is shown when it exists; without it only
 * the name is shown. The official logos belong to MTN and Airtel: put your own copies in frontend/src/assets/payment/.
 */
@Component({
  selector: 'app-pay-provider-picker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pay-picker" *ngIf="providers.length > 1" role="radiogroup">
      <button type="button" *ngFor="let p of providers" class="pay-chip" [class.on]="p.code === value" role="radio" [attr.aria-checked]="p.code === value" [attr.data-provider]="p.code" (click)="pick(p.code)">
        <img *ngIf="!broken[p.code]" class="pay-logo" [src]="logo(p.code)" alt="" (error)="broken[p.code] = true" />
        <span>{{ p.label }}</span>
      </button>
    </div>
  `,
  styles: [`
    .pay-picker { display: flex; gap: 0.5rem; flex-wrap: wrap; margin: 0.35rem 0; justify-content: inherit; }
    .pay-chip { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0.75rem; border-radius: 0.5rem; cursor: pointer; font: inherit; color: inherit;
      background: transparent; border: 2px solid rgba(148, 163, 184, 0.45); }
    .pay-chip.on { border-color: #2dbf72; background: rgba(45, 191, 114, 0.12); font-weight: 600; }
    .pay-logo { height: 26px; max-width: 70px; object-fit: contain; }
  `],
})
export class PayProviderPickerComponent implements OnChanges {
  @Input() providers: ShopPayProvider[] = [];
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();
  broken: Record<string, boolean> = {};

  /** The first provider is chosen at once, so a customer who does not look still pays with something valid. */
  ngOnChanges(): void {
    if (this.providers.length > 0 && !this.providers.some((p) => p.code === this.value)) {
      this.value = this.providers[0].code;
      this.valueChange.emit(this.value);
    }
  }

  pick(code: string): void {
    this.value = code;
    this.valueChange.emit(code);
  }

  logo(code: string): string {
    return 'assets/payment/' + code.toLowerCase() + '.png';
  }
}
