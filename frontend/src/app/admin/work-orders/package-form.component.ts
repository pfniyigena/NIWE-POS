import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { Product } from '../../core/models';
import { PackagePayload, WorkOrderApiService } from '../../core/api/work-order-api.service';

/** Work Orders > a service package: name and price, the checklist steps and the usual parts. */
@Component({
  selector: 'app-service-package-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Service package' : 'New service package' }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/work-orders/packages">Back to packages</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form class="card" style="padding:1rem; max-width:44rem;" (ngSubmit)="save()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">Name</label><input class="form-control" name="n" [(ngModel)]="f.name" required /></div>
          <div class="form-group" style="flex:1; min-width:8rem;"><label class="form-label">Code (optional)</label><input class="form-control" name="c" [(ngModel)]="f.code" /></div>
        </div>
        <div class="form-group"><label class="form-label">Description</label><textarea class="form-control" rows="2" name="d" [(ngModel)]="f.description"></textarea></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Fixed price</label><input class="form-control" type="number" min="0" step="0.01" name="p" [(ngModel)]="f.fixedPrice" placeholder="0 = bill hours and parts" /></div>
          <div class="form-group"><label class="form-label">Estimated hours</label><input class="form-control" type="number" min="0" step="0.25" name="h" [(ngModel)]="f.estimatedHours" /></div>
          <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center; margin-top:1.8rem;"><input type="checkbox" name="a" [(ngModel)]="f.active" /> Active</label></div>
        </div>
        <div class="form-group"><label class="form-label">Checklist (one step per line)</label><textarea class="form-control" rows="6" name="s" [(ngModel)]="steps"></textarea></div>

        <strong class="mb-2">Usual parts</strong>
        <table class="table-sm" *ngIf="f.parts.length">
          <thead><tr><th>Product</th><th style="width:6rem;">Qty</th><th>In the price</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let p of f.parts; let i = index">
              <td>{{ names[p.productId] }}</td>
              <td><input class="form-control" type="number" min="1" [name]="'q' + i" [(ngModel)]="p.quantity" /></td>
              <td><input type="checkbox" [name]="'i' + i" [(ngModel)]="p.included" /></td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="f.parts.splice(i, 1)">Remove</button></td>
            </tr>
          </tbody>
        </table>
        <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
          <input class="form-control" style="width:14rem;" placeholder="Search a product" name="ps" [(ngModel)]="query" (keyup.enter)="search()" />
          <button class="btn btn-outline-secondary btn-sm" type="button" (click)="search()">Search</button>
          <select class="form-select" style="width:auto; max-width:18rem;" name="pp" [(ngModel)]="pick" *ngIf="products.length">
            <option [ngValue]="null">Choose…</option>
            <option *ngFor="let p of products" [ngValue]="p.id">{{ p.name }}</option>
          </select>
          <button class="btn btn-outline-primary btn-sm" type="button" *ngIf="pick" (click)="add()">Add</button>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="busy">Save</button>
          <button class="btn btn-outline-danger" type="button" *ngIf="id" (click)="remove()">Delete</button>
        </div>
      </form>
    </app-shell>
  `,
})
export class ServicePackageFormComponent implements OnInit {
  id: number | null = null;
  f: PackagePayload = { code: '', name: '', description: '', fixedPrice: null, estimatedHours: null, active: true, items: [], parts: [] };
  steps = '';
  names: Record<number, string> = {};
  products: Product[] = [];
  query = '';
  pick: number | null = null;
  busy = false;
  saved = false;
  error: string | null = null;

  constructor(private api: WorkOrderApiService, private catalogApi: CatalogApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      this.id = Number(idParam);
      this.api.getPackage(this.id).subscribe({
        next: (p) => {
          this.f = {
            code: p.code ?? '', name: p.name, description: p.description ?? '', fixedPrice: p.fixedPrice || null, estimatedHours: p.estimatedHours, active: p.active,
            items: [], parts: p.parts.map((x) => ({ productId: x.productId, quantity: x.quantity, included: x.included })),
          };
          p.parts.forEach((x) => (this.names[x.productId] = x.productName));
          this.steps = p.items.map((i) => i.text).join('\n');
        },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the package'),
      });
    }
  }

  search(): void {
    this.catalogApi.listAllProducts(this.query.trim() || null, 0, 30).subscribe({ next: (r) => (this.products = r.content), error: () => {} });
  }

  add(): void {
    const p = this.products.find((x) => x.id === this.pick);
    if (!p) return;
    this.names[p.id] = p.name;
    if (!this.f.parts.some((x) => x.productId === p.id)) this.f.parts.push({ productId: p.id, quantity: 1, included: true });
    this.pick = null;
  }

  save(): void {
    this.error = null;
    this.saved = false;
    this.busy = true;
    const body: PackagePayload = { ...this.f, items: this.steps.split('\n').map((s) => s.trim()).filter((s) => s) };
    const call = this.id ? this.api.updatePackage(this.id, body) : this.api.createPackage(body);
    call.subscribe({
      next: (p) => {
        this.busy = false;
        if (!this.id) {
          this.router.navigate(['/admin/work-orders/packages', p.id]);
          return;
        }
        this.saved = true;
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save'; },
    });
  }

  remove(): void {
    if (!this.id || !confirm('Delete this package? Packages already used on work orders can only be made inactive.')) return;
    this.api.deletePackage(this.id).subscribe({ next: () => this.router.navigate(['/admin/work-orders/packages']), error: (e) => (this.error = e?.error?.message ?? 'Could not delete') });
  }
}
