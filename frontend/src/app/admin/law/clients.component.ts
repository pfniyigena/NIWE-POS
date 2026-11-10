import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { LawExportComponent } from './law-export.component';
import { LawImportComponent } from './law-import.component';
import { AuthService } from '../../core/auth.service';
import { PAGE_SIZE } from '../../core/api/paged';
import { ClientRow, LawApiService } from '../../core/api/law-api.service';

/** Law Firm > Clients: customers who have at least one matter. A client is an ordinary customer, so invoices and statements work as usual. */
@Component({
  selector: 'app-law-clients',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, PagerComponent, LawExportComponent, LawImportComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Clients' | translate }}</h3>
        <app-law-export kind="clients"></app-law-export>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary imp-open" type="button" *ngIf="canManage" (click)="importOpen = !importOpen">{{ 'Import' | translate }}</button>
          <a class="btn btn-primary" routerLink="/admin/law/matters/new">{{ '+ Open matter' | translate }}</a>
        </div>
      </div>
      <app-law-import kind="clients" *ngIf="importOpen" (imported)="load()" (closed)="importOpen = false"></app-law-import>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Client' | translate }}</th><th>{{ 'Phone' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th class="text-end">{{ 'Matters' | translate }}</th><th class="text-end">{{ 'Open' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of list">
            <td data-label="Client"><a [routerLink]="['/admin/customers', c.id]">{{ c.name }}</a></td><td data-label="Phone">{{ c.phone || '—' }}</td><td data-label="E-mail">{{ c.email || '—' }}</td>
            <td class="text-end" data-label="Matters">{{ c.matters }}</td><td class="text-end" data-label="Open">{{ c.openMatters }}</td>
            <td><a class="btn btn-sm btn-outline-secondary" routerLink="/admin/law/matters" [queryParams]="{ clientId: c.id }">{{ 'Matters' | translate }}</a>
              <a class="btn btn-sm btn-outline-primary" style="margin-left:0.3rem;" routerLink="/admin/law/matters/new" [queryParams]="{ clientId: c.id }">{{ '+ Matter' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length">{{ 'No clients yet. A client appears here as soon as a matter is opened for a customer.' | translate }}</p>
    </app-shell>
  `,
})
export class LawClientsComponent implements OnInit {
  list: ClientRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  error: string | null = null;
  canManage: boolean;
  importOpen = false;

  constructor(private api: LawApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }
  ngOnInit(): void { this.load(); }
  goPage(p: number): void { this.page = p; this.load(); }
  load(): void {
    this.api.clientsPage({ page: this.page, size: this.size }).subscribe({ next: (r) => { this.list = r.items; this.total = r.total; }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the clients') });
  }
}
