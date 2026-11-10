import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** The row of links shared by the Work Orders screens: list, board, calendar, planned maintenance and settings. */
@Component({
  selector: 'app-work-order-tabs',
  standalone: true,
  imports: [TranslatePipe, RouterLink, RouterLinkActive],
  template: `
    <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
      <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/work-orders" routerLinkActive="btn-primary" [routerLinkActiveOptions]="{ exact: true }">{{ 'List' | translate }}</a>
      <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/work-orders/board" routerLinkActive="btn-primary">{{ 'Board' | translate }}</a>
      <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/work-orders/calendar" routerLinkActive="btn-primary">{{ 'Calendar' | translate }}</a>
      <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/work-orders/schedules" routerLinkActive="btn-primary">{{ 'Planned maintenance' | translate }}</a>
      <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/work-orders/settings" routerLinkActive="btn-primary">{{ 'Settings' | translate }}</a>
    </div>
  `,
})
export class WorkOrderTabsComponent {}
