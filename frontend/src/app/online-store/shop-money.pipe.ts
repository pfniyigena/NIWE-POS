import { formatNumber } from '@angular/common';
import { Inject, LOCALE_ID, Pipe, PipeTransform } from '@angular/core';

/** An amount with the shop's currency after it: 1,500 RWF. With no currency known it is just the number. */
@Pipe({ name: 'shopMoney', standalone: true })
export class ShopMoneyPipe implements PipeTransform {
  constructor(@Inject(LOCALE_ID) private locale: string) {}

  transform(value: number | string | null | undefined, currency?: string | null): string {
    if (value === null || value === undefined || value === '') return '';
    const n = formatNumber(Number(value), this.locale, '1.0-2');
    return currency ? `${n} ${currency}` : n;
  }
}
