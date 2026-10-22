import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ShellComponent } from '../../shared/shell.component';
import { CONDITIONS, INSPECTION_KIND_LABEL, Inspection, InspectionItem, ItemCondition, PmDepositApiService, conditionBadge, conditionLabel } from '../../core/api/pm-deposit-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';

interface RoomGroup { room: string; items: InspectionItem[]; }

/** Property Management > an inspection: room by room, item by item, what condition it is in; photos; sign-off with the tenant; a printed copy. */
@Component({
  selector: 'app-pm-inspection-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PmDocumentsPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!ins && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="ins">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a [routerLink]="['/admin/propertymgmt/leases', ins.leaseId]" style="font-size:0.85rem;">← Lease {{ ins.leaseCode }}</a>
            <h3>{{ kindName(ins.kind) }} inspection {{ ins.code }}
              <span class="badge" [ngClass]="ins.status === 'SIGNED_OFF' ? 'badge-success' : 'badge-warning'" style="font-size:0.8rem; vertical-align:middle;">{{ ins.status === 'SIGNED_OFF' ? 'Signed off' : 'In progress' }}</span></h3>
            <div class="text-muted">{{ ins.propertyName }} · unit {{ ins.unitNo }} · <a [routerLink]="['/admin/propertymgmt/tenants', ins.tenantId]">{{ ins.tenantName }}</a></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-secondary" type="button" (click)="print('pdf')">Print (PDF)</button>
            <button class="btn btn-outline-secondary" type="button" (click)="print('xlsx')">Excel</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="ins.canManage && ins.status === 'SIGNED_OFF'" [disabled]="busy" (click)="reopen()">Reopen</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="ins.canManage && ins.status === 'DRAFT'" [disabled]="busy" (click)="remove()">Delete</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">Date</label>
              <input class="form-control" type="date" name="dt" [(ngModel)]="h.date" [max]="today" [disabled]="!editable" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:11rem;"><label class="form-label">Inspector</label><input class="form-control" name="in" [(ngModel)]="h.inspectorName" maxlength="200" [disabled]="!editable" /></div>
            <div class="form-group mb-2" style="min-width:9rem;"><label class="form-label">Electricity meter</label><input class="form-control" name="el" [(ngModel)]="h.electricityReading" maxlength="40" [disabled]="!editable" /></div>
            <div class="form-group mb-2" style="min-width:9rem;"><label class="form-label">Water meter</label><input class="form-control" name="wa" [(ngModel)]="h.waterReading" maxlength="40" [disabled]="!editable" /></div>
            <div class="form-group mb-2" style="min-width:7rem;"><label class="form-label">Keys handed</label><input class="form-control" type="number" min="0" max="99" name="ky" [(ngModel)]="h.keysHanded" [disabled]="!editable" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="nt" [(ngModel)]="h.notes" maxlength="2000" [disabled]="!editable"></textarea></div>
          <div *ngIf="editable"><button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy" (click)="saveHeader()">Save these details</button></div>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="ins.status === 'SIGNED_OFF'">
            Signed off on {{ ins.signedOffOn | date: 'd MMM y' }} by {{ ins.signedOffByName }}.
            <span *ngIf="ins.tenantAcknowledged">Acknowledged for the tenant by {{ ins.tenantSignedName }}.</span>
            <span *ngIf="!ins.tenantAcknowledged">Not acknowledged by the tenant.</span>
          </div>
        </div>

        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">Looked at</div><div class="kpi">{{ ins.assessed }} / {{ ins.items.length }}</div></div>
          <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">Problems</div><div class="kpi" [style.color]="ins.problems ? '#b91c1c' : ''">{{ ins.problems }}</div></div>
          <div class="card" style="padding:0.7rem 1rem;" *ngIf="ins.kind === 'MOVE_OUT'"><div class="text-muted kpi-label">Worse than at move-in</div><div class="kpi" [style.color]="ins.worse ? '#b91c1c' : ''">{{ ins.worse }}</div></div>
          <div class="card" style="padding:0.7rem 1rem;" *ngIf="ins.repairCost"><div class="text-muted kpi-label">Repairs</div><div class="kpi">{{ ins.repairCost | number: '1.0-0' }}</div></div>
          <div style="align-self:center;" *ngIf="editable && untouched">
            <button class="btn btn-sm btn-outline-secondary" type="button" [disabled]="busy" (click)="allGood()">Mark the {{ untouched }} not yet looked at as Good</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngFor="let g of groups; trackBy: trackRoom">
          <h4 class="mb-2">{{ g.room }}</h4>
          <table class="stack table-sm">
            <thead><tr><th>Item</th><th *ngIf="ins.kind === 'MOVE_OUT'">At move-in</th><th>Condition</th><th>Notes</th><th class="text-end" *ngIf="ins.kind !== 'MOVE_IN'">Repair cost</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let it of g.items; trackBy: trackItem">
                <td data-label="Item">{{ it.item }}</td>
                <td data-label="At move-in" *ngIf="ins.kind === 'MOVE_OUT'"><span class="badge" [ngClass]="badge(it.baseline)">{{ label(it.baseline) }}</span></td>
                <td data-label="Condition">
                  <select class="form-select" style="min-width:8.5rem;" *ngIf="editable" [name]="'c' + it.id" (change)="setCondition(it, $any($event.target).value)">
                    <option value="" [selected]="!it.condition">Not looked at</option>
                    <option *ngFor="let c of conditions" [value]="c.value" [selected]="it.condition === c.value">{{ c.label }}</option>
                  </select>
                  <span *ngIf="!editable" class="badge" [ngClass]="badge(it.condition)">{{ label(it.condition) }}</span>
                  <span class="badge badge-danger" *ngIf="it.worse" style="margin-left:0.3rem;">Worse</span>
                </td>
                <td data-label="Notes">
                  <input class="form-control" *ngIf="editable" [name]="'n' + it.id" [value]="it.notes || ''" maxlength="500" (change)="setNotes(it, $any($event.target).value)" />
                  <span *ngIf="!editable">{{ it.notes || '—' }}</span>
                </td>
                <td class="text-end" data-label="Repair cost" *ngIf="ins.kind !== 'MOVE_IN'">
                  <input class="form-control text-end" style="width:7.5rem; display:inline-block;" type="number" min="0" step="0.01" *ngIf="editable" [name]="'r' + it.id" [value]="it.repairCost ?? ''" (change)="setCost(it, $any($event.target).value)" />
                  <span *ngIf="!editable">{{ it.repairCost ? (it.repairCost | number: '1.0-0') : '—' }}</span>
                </td>
                <td style="text-align:right;" data-label=""><button class="btn btn-sm btn-outline-danger" type="button" *ngIf="editable" [disabled]="busy" (click)="removeItem(it)">Remove</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="editable">
          <h4 class="mb-2">Add an item</h4>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="min-width:12rem;"><label class="form-label">Room</label>
              <input class="form-control" name="ar" list="rooms" [(ngModel)]="add.room" maxlength="80" /><datalist id="rooms"><option *ngFor="let g of groups" [value]="g.room"></option></datalist></div>
            <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">Item</label><input class="form-control" name="ai" [(ngModel)]="add.item" maxlength="120" /></div>
            <button class="btn btn-outline-primary" type="button" [disabled]="busy || !add.room.trim() || !add.item.trim()" (click)="addItem()">Add</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="editable">
          <h4 class="mb-2">Sign off</h4>
          <p class="text-muted" style="font-size:0.9rem;">Signing off locks the list. Print it first if the tenant signs on paper, then tick below.</p>
          <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ack" [(ngModel)]="sign.ack" /> The tenant has seen this list and agrees with it</label>
          <div class="form-group mb-2 mt-2" *ngIf="sign.ack" style="max-width:24rem;"><label class="form-label">Name of the person who signed for the tenant *</label><input class="form-control" name="sn" [(ngModel)]="sign.name" maxlength="200" /></div>
          <button class="btn btn-primary" type="button" [disabled]="busy" (click)="signOff()">Sign off</button>
        </div>

        <app-pm-documents-panel subjectType="INSPECTION" [subjectId]="ins.id" [canManage]="ins.canManage" defaultCategory="PHOTO" title="Photos and papers"></app-pm-documents-panel>
      </ng-container>
    </app-shell>
  `,
})
export class PmInspectionDetailComponent implements OnInit {
  ins: Inspection | null = null;
  groups: RoomGroup[] = [];
  error: string | null = null;
  busy = false;
  today = new Date().toISOString().slice(0, 10);
  conditions = CONDITIONS;
  h: { date: string; inspectorName: string; electricityReading: string; waterReading: string; keysHanded: number | null; notes: string } = { date: '', inspectorName: '', electricityReading: '', waterReading: '', keysHanded: null, notes: '' };
  add = { room: '', item: '' };
  sign = { ack: false, name: '' };

  constructor(private api: PmDepositApiService, private route: ActivatedRoute, private router: Router) {}

  get editable(): boolean { return !!this.ins && this.ins.canManage && this.ins.status === 'DRAFT'; }
  get untouched(): number { return this.ins ? this.ins.items.filter((i) => !i.condition).length : 0; }

  ngOnInit(): void {
    this.api.inspection(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (i) => this.set(i, true), error: (e) => (this.error = e?.error?.message ?? 'Could not load the inspection') });
  }

  private set(i: Inspection, header: boolean): void {
    this.ins = i;
    const map = new Map<string, InspectionItem[]>();
    for (const it of i.items) { if (!map.has(it.room)) map.set(it.room, []); map.get(it.room)!.push(it); }
    this.groups = Array.from(map.entries()).map(([room, items]) => ({ room, items }));
    if (header) this.h = { date: i.date, inspectorName: i.inspectorName ?? '', electricityReading: i.electricityReading ?? '', waterReading: i.waterReading ?? '', keysHanded: i.keysHanded, notes: i.notes ?? '' };
  }

  trackRoom(_: number, g: RoomGroup): string { return g.room; }
  trackItem(_: number, it: InspectionItem): number { return it.id; }
  kindName(k: string): string { return (INSPECTION_KIND_LABEL as Record<string, string>)[k] ?? k; }
  label(c: string | null): string { return conditionLabel(c); }
  badge(c: string | null): string { return conditionBadge(c); }

  private fail(e: any, fallback: string): void { this.busy = false; this.error = e?.error?.message ?? fallback; }

  private run(call: ReturnType<PmDepositApiService['inspection']>, header = false): void {
    this.busy = true;
    this.error = null;
    call.subscribe({ next: (i) => { this.busy = false; this.set(i, header); }, error: (e) => this.fail(e, 'Could not save') });
  }

  saveHeader(): void {
    if (!this.ins) return;
    this.run(this.api.updateInspection(this.ins.id, {
      date: this.h.date || null, inspectorName: this.h.inspectorName, electricityReading: this.h.electricityReading, waterReading: this.h.waterReading,
      keysHanded: this.h.keysHanded === null || (this.h.keysHanded as unknown) === '' ? null : Number(this.h.keysHanded), notes: this.h.notes,
    }), true);
  }

  setCondition(it: InspectionItem, value: string): void {
    if (!this.ins) return;
    this.run(value ? this.api.updateItem(this.ins.id, it.id, { condition: value as ItemCondition }) : this.api.updateItem(this.ins.id, it.id, { clearCondition: true }));
  }
  setNotes(it: InspectionItem, value: string): void { if (this.ins && value !== (it.notes ?? '')) this.run(this.api.updateItem(this.ins.id, it.id, { notes: value })); }
  setCost(it: InspectionItem, value: string): void {
    if (!this.ins) return;
    const t = String(value).trim();
    this.run(t === '' ? this.api.updateItem(this.ins.id, it.id, { clearCost: true }) : this.api.updateItem(this.ins.id, it.id, { repairCost: Number(t) }));
  }

  allGood(): void {
    if (!this.ins) return;
    const id = this.ins.id;
    const todo = this.ins.items.filter((i) => !i.condition);
    this.busy = true;
    this.error = null;
    forkJoin(todo.map((i) => this.api.updateItem(id, i.id, { condition: 'GOOD' }))).subscribe({
      next: () => this.api.inspection(id).subscribe({ next: (i) => { this.busy = false; this.set(i, false); }, error: (e) => this.fail(e, 'Could not reload') }),
      error: (e) => this.fail(e, 'Could not save'),
    });
  }

  addItem(): void {
    if (!this.ins) return;
    const room = this.add.room.trim(), item = this.add.item.trim();
    this.busy = true;
    this.error = null;
    this.api.addItem(this.ins.id, { room, item }).subscribe({ next: (i) => { this.busy = false; this.add.item = ''; this.set(i, false); }, error: (e) => this.fail(e, 'Could not add') });
  }

  removeItem(it: InspectionItem): void { if (this.ins && confirm('Remove ' + it.item + ' from ' + it.room + '?')) this.run(this.api.deleteItem(this.ins.id, it.id)); }

  signOff(): void {
    if (!this.ins) return;
    if (this.sign.ack && !this.sign.name.trim()) { this.error = 'Enter the name of the person who signed for the tenant.'; return; }
    this.run(this.api.signOff(this.ins.id, { tenantAcknowledged: this.sign.ack, tenantSignedName: this.sign.name.trim() }));
  }

  reopen(): void { if (this.ins && confirm('Reopen this inspection so it can be changed?')) this.run(this.api.reopen(this.ins.id)); }

  remove(): void {
    if (!this.ins || !confirm('Delete this inspection and its list?')) return;
    const leaseId = this.ins.leaseId;
    this.busy = true;
    this.api.deleteInspection(this.ins.id).subscribe({ next: () => this.router.navigate(['/admin/propertymgmt/leases', leaseId]), error: (e) => this.fail(e, 'Could not delete') });
  }

  print(format: 'pdf' | 'xlsx'): void {
    if (!this.ins) return;
    this.api.print(this.ins.id, this.ins.code, format).subscribe({ error: (e) => this.fail(e, 'Could not print') });
  }
}
