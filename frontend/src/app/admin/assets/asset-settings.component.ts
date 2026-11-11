import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AssetApiService, AssetSettings } from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
/** Assets > Settings: how early expiry warnings start, what counts as "ending soon", and the disposal approval limit. */
@Component({
  selector: 'app-asset-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'Asset settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form (ngSubmit)="save()" *ngIf="s">
        <div class="form-group">
          <label class="form-label">{{ 'Warn before a warranty or insurance ends (days)' | translate }}</label>
          <input class="form-control" type="number" min="1" max="365" name="alertDays" [(ngModel)]="s.alertDays" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ '"Fully depreciated soon" means within (months)' | translate }}</label>
          <input class="form-control" type="number" min="1" max="60" name="soonMonths" [(ngModel)]="s.soonMonths" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Disposal approval limit (book value)' | translate }}</label>
          <input class="form-control" type="number" min="0" step="0.01" name="limit" [(ngModel)]="s.disposalApprovalLimit" />
          <div class="text-muted" style="font-size:0.8rem;">
            {{ 'Disposing of an asset whose book value is above this needs an administrator or a user with the permission' | translate }} <em>{{ 'Approve large asset disposals' | translate }}</em>{{ '. 0 means no limit.' | translate }}
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" routerLink="/admin/assets/overview">{{ 'Back' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class AssetSettingsComponent implements OnInit {
  s: AssetSettings | null = null;
  error: string | null = null;
  saved = false;
  busy = false;

  constructor(private api: AssetApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the settings')) });
  }

  save(): void {
    if (!this.s) return;
    this.error = null;
    this.saved = false;
    this.busy = true;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => {
        this.busy = false;
        this.s = s;
        this.saved = true;
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not save the settings');
      },
    });
  }
}
