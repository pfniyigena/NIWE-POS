import { Injectable } from '@angular/core';
import { OnlineStoreApiService } from '../core/api/online-store-api.service';

const KEY = 'niwe.shop.wish';
const MAX = 100;

function readLocal(): number[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(v) ? v.filter((x) => Number.isInteger(x) && x > 0).slice(0, MAX) : [];
  } catch {
    return [];
  }
}

function writeLocal(ids: number[]): void {
  try {
    if (ids.length) localStorage.setItem(KEY, JSON.stringify(ids));
    else localStorage.removeItem(KEY);
  } catch {
    // browser storage can be blocked: the hearts then last only until the page is closed
  }
}

/**
 * The wishlist (the heart on a product). A guest keeps it in this browser only; a signed-in customer keeps it in the account, and the hearts given
 * as a guest join the account at sign-in. Newest first. A heart is shown at once and sent to the shop afterwards.
 */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private list: number[] = readLocal();
  private signedIn = false;

  constructor(private api: OnlineStoreApiService) {}

  get count(): number {
    return this.list.length;
  }

  ids(): number[] {
    return this.list;
  }

  has(id: number): boolean {
    return this.list.includes(id);
  }

  /** Called when the signed-in state changes: the guest's hearts move to the account, or the guest list comes back after sign-out. */
  setSignedIn(on: boolean): void {
    if (on === this.signedIn) return;
    this.signedIn = on;
    if (on) {
      const local = readLocal();
      const done = (ids: number[]) => { this.list = ids; writeLocal([]); };
      (local.length ? this.api.wishMerge(local) : this.api.wishIds()).subscribe({ next: done, error: () => {} });
    } else {
      this.list = readLocal();
    }
  }

  toggle(id: number): void {
    const had = this.has(id);
    this.list = had ? this.list.filter((x) => x !== id) : [id, ...this.list].slice(0, MAX);
    if (!this.signedIn) {
      writeLocal(this.list);
      return;
    }
    (had ? this.api.wishRemove(id) : this.api.wishAdd(id)).subscribe({
      next: (ids) => (this.list = ids),
      error: () => (this.list = had ? [id, ...this.list] : this.list.filter((x) => x !== id)),
    });
  }
}
