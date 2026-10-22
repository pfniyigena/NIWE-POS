import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MAINTENANCE_PRIORITIES, MAINTENANCE_STATUS_LABEL, MaintenanceRow, maintLabel, maintenancePriorityBadge } from '../../core/api/pm-maintenance-api.service';

/** The overview card: open requests that are urgent, high priority or overdue. */
@Component({
  selector: 'app-maintenance-attention',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem;">
      <div class="flex justify-between align-center mb-2"><h4>Maintenance needing attention</h4><a routerLink="/admin/propertymgmt/maintenance" style="font-size:0.85rem;">All requests</a></div>
      <table class="table-sm" *ngIf="rows.length; else none"><tbody>
        <tr *ngFor="let r of rows">
          <td><a [routerLink]="['/admin/propertymgmt/maintenance', r.id]"><strong>{{ r.code }}</strong> · {{ r.title }}</a><div class="text-muted" style="font-size:0.8rem;">{{ r.propertyName }} · {{ r.unitNo }}{{ r.tenantName ? ' · ' + r.tenantName : '' }}</div></td>
          <td style="text-align:right; white-space:nowrap;"><span class="badge" [ngClass]="pbadge(r.priority)">{{ plabel(r.priority) }}</span>
            <div class="text-muted" style="font-size:0.8rem;">{{ slabel(r.status) }}{{ r.daysOpen != null ? ' · ' + r.daysOpen + ' days' : '' }}</div>
            <div style="font-size:0.8rem; color:#b91c1c;" *ngIf="r.overdue">overdue since {{ r.dueDate | date: 'd MMM' }}</div></td>
        </tr>
      </tbody></table>
      <ng-template #none><div class="text-muted">Nothing urgent or overdue.</div></ng-template>
    </div>
  `,
})
export class MaintenanceAttentionComponent {
  @Input() rows: MaintenanceRow[] = [];
  pbadge(s: string): string { return maintenancePriorityBadge(s); }
  plabel(s: string): string { return maintLabel(MAINTENANCE_PRIORITIES, s); }
  slabel(s: string): string { return (MAINTENANCE_STATUS_LABEL as Record<string, string>)[s] ?? s; }
}
