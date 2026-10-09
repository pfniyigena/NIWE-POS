import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { LocationType, Warehouse, WarehouseLocation } from '../models';
import { WarehouseApiService } from './warehouse-api.service';

/** One selectable location, with a "Warehouse / Location (TYPE)" label. */
export interface LocationChoice {
  id: number;
  label: string;
  type: LocationType | null;
  warehouseId: number;
  primaryWarehouse: boolean;
}

/**
 * Multi-warehouse phase 4 — the shared "which location?" list used by the Manufacturing order,
 * Packaging breakdown and Inventory settings screens: every active location of every active
 * warehouse (Damaged locations left out, since none of those operations may use one). Never
 * errors: if the warehouse list can't be read (it is admin-only) the result is simply empty and
 * the calling screen hides its picker, keeping the old behaviour.
 *
 * Screens show their picker only when there are more than two choices — a fresh warehouse comes
 * with just Receiving + Main Stock, so more than that means a second warehouse or an extra
 * location exists.
 */
@Injectable({ providedIn: 'root' })
export class LocationChoicesService {
  constructor(private warehouseApi: WarehouseApiService) {}

  load(): Observable<LocationChoice[]> {
    return this.warehouseApi.listAll().pipe(
      catchError(() => of([] as Warehouse[])),
      switchMap((all) => {
        const active = all.filter((w) => w.active);
        if (active.length === 0) return of([] as LocationChoice[]);
        return forkJoin(
          active.map((w) => this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[]))))
        ).pipe(
          map((perWarehouse) => {
            const choices: LocationChoice[] = [];
            active.forEach((w, i) => {
              for (const l of perWarehouse[i]) {
                if (!l.active || l.type === 'DAMAGED') continue;
                choices.push({
                  id: l.id,
                  label: `${w.name} / ${l.name}${l.type ? ' (' + l.type + ')' : ''}`,
                  type: l.type,
                  warehouseId: w.id,
                  primaryWarehouse: w.primary,
                });
              }
            });
            return choices;
          })
        );
      })
    );
  }
}
