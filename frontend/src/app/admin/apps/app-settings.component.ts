import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AppsApiService } from '../../core/api/apps-api.service';
import { EfrisApiService } from '../../core/api/efris-api.service';
import { EfrisChadApiService } from '../../core/api/efris-chad-api.service';
import { AppSummary, EfrisChadTerminal, EfrisTerminal } from '../../core/models';

/**
 * Admin > Settings > Apps > Configure (`/admin/apps/:key`) — Task: "This will work as a plugin
 * (or apps) to be installed and configured."
 *
 * EFRIS specifically gets its own block — a per-terminal EFRIS URL table (Task: "EFRIS_setting
 * for one terminal... every terminal should be connected to the external EFRIS") — since that's
 * structured, one-row-per-Register data. A future app in the catalog with its own structured
 * needs would get its own `*ngIf="key === '...'"` block the same way.
 *
 * <p>Task: "Then can we remove this? ... Frontend + backend, fully" — this screen used to also
 * carry a generic key/value settings table below the EFRIS block, for whatever an app needed that
 * had no structured shape yet. Removed: EFRIS (the only app in the catalog) never ended up using
 * it once its real per-terminal fields were built above, no other app exists to use it, and
 * nothing in the backend ever read the rows back — see AppsService/InstalledApp's own javadoc for
 * the full removal writeup.</p>
 *
 * <p>Task: "Now I want to add new app (EFRIS Chad) with will need the following settings: url,
 * basic_auth_username, basic_password, sdc_number, branch_code, mrc, integration_key." Gets its
 * own `*ngIf="key === 'EFRIS_TD'"` block below, same per-terminal-table shape as EFRIS Rwanda's,
 * with Chad's own distinct connection fields plus, since Task 3 ("Add also Chad Revenue Authority
 * logo and EFRIS logo on EFRIS Chad setting"), its own two letterhead logo columns mirroring
 * Rwanda's.</p>
 */
@Component({
  selector: 'app-app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-1">
        <h3>{{ app?.name ?? key }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/apps">&larr; Back to Apps</a>
      </div>
      <p class="text-muted mb-3" *ngIf="app">{{ app.description }}</p>

      <div class="card mb-3" style="padding:12px;" *ngIf="app">
        <div class="flex justify-between align-center">
          <div>
            <span class="badge badge-warning" *ngIf="!app.enabled">Installed — disabled</span>
            <span class="badge badge-success" *ngIf="app.enabled">Enabled</span>
          </div>
          <button
            class="btn btn-sm btn-outline-secondary"
            (click)="app.enabled ? disable() : enable()"
          >
            {{ app.enabled ? 'Disable' : 'Enable' }}
          </button>
        </div>
      </div>

      <div class="card mb-3" style="padding:12px;" *ngIf="key === 'EFRIS_RW'">
        <h4 class="mb-1">Terminal EFRIS URLs</h4>
        <p class="text-muted mb-2" style="font-size:0.85rem;">
          Each till talks to its own local EFRIS device — set the URL that terminal should submit
          fiscal receipts to. Task: "every terminal should be connected to the external EFRIS."
        </p>
        <div class="alert alert-danger" *ngIf="terminalsError">{{ terminalsError }}</div>
        <p class="text-muted mb-2" style="font-size:0.8rem;">
          URL/DB/UID/Password are the JSON-RPC connection details ("db"/"uid"/"password" in EFRIS's own
          {{ "object.execute" }} call) a real submission sends — required for that terminal to actually
          submit invoices, not just to store a URL.
        </p>
        <div class="table-responsive" style="overflow-x:auto;">
          <table class="table-sm">
            <thead>
              <tr>
                <th>Terminal</th>
                <th>Location</th>
                <th>EFRIS URL</th>
                <th>DB</th>
                <th>UID</th>
                <th>Password</th>
                <th>RRA Logo</th>
                <th>EFRIS Logo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let t of efrisTerminals">
                <td>{{ t.registerName }}</td>
                <td>{{ t.location || '—' }}</td>
                <td>
                  <input
                    class="form-control form-control-sm"
                    [(ngModel)]="t.efrisUrl"
                    placeholder="e.g. http://192.168.1.50:9090/efris"
                    style="min-width:220px;"
                  />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.efrisDb" placeholder="e.g. RRA_VSDC" style="min-width:110px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" type="number" [(ngModel)]="t.efrisUid" style="min-width:80px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" type="password" [(ngModel)]="t.efrisPassword" style="min-width:110px;" />
                </td>
                <!-- Task: "add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
                     printout" — same data: URL / FileReader pattern as BusinessDetailsComponent's
                     logo upload, just per-terminal instead of store-wide. -->
                <td>
                  <img *ngIf="t.rraLogoDataUrl" [src]="t.rraLogoDataUrl" alt="RRA logo" style="max-height:32px; max-width:70px; display:block; margin-bottom:4px;" />
                  <input class="form-control form-control-sm" type="file" accept="image/*" (change)="onLogoSelected($event, t, 'rraLogoDataUrl')" style="min-width:150px;" />
                </td>
                <td>
                  <img *ngIf="t.efrisLogoDataUrl" [src]="t.efrisLogoDataUrl" alt="EFRIS logo" style="max-height:32px; max-width:70px; display:block; margin-bottom:4px;" />
                  <input class="form-control form-control-sm" type="file" accept="image/*" (change)="onLogoSelected($event, t, 'efrisLogoDataUrl')" style="min-width:150px;" />
                </td>
                <td>
                  <div class="flex gap-1">
                    <button
                      class="btn btn-sm btn-outline-secondary"
                      (click)="saveTerminalSettings(t)"
                      [disabled]="savingTerminalId === t.registerId || clearingTerminalId === t.registerId"
                    >
                      {{ savingTerminalId === t.registerId ? 'Saving…' : 'Save' }}
                    </button>
                    <!-- Task: "One Terminal can have only one EFRIS mapped with" — the way an admin
                         unmaps a terminal from EFRIS Rwanda, either to stop using it on that till or
                         to free it up to be mapped to EFRIS Chad instead (Save on the Chad table
                         below refuses while this terminal still has a Rwanda row). -->
                    <button
                      class="btn btn-sm btn-outline-danger"
                      (click)="clearTerminalSettings(t)"
                      [disabled]="savingTerminalId === t.registerId || clearingTerminalId === t.registerId"
                    >
                      {{ clearingTerminalId === t.registerId ? 'Clearing…' : 'Clear' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr *ngIf="efrisTerminals.length === 0">
                <td colspan="9" class="text-muted">No terminals yet — add one under Registers & Terminal first.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card mb-3" style="padding:12px;" *ngIf="key === 'EFRIS_TD'">
        <h4 class="mb-1">Terminal EFRIS Chad Settings</h4>
        <p class="text-muted mb-2" style="font-size:0.85rem;">
          Each till talks to EFRIS Chad over HTTP(S) with basic authentication — set the connection
          details that terminal should submit fiscal receipts with.
        </p>
        <div class="alert alert-danger" *ngIf="chadTerminalsError">{{ chadTerminalsError }}</div>
        <div class="table-responsive" style="overflow-x:auto;">
          <table class="table-sm">
            <thead>
              <tr>
                <th>Terminal</th>
                <th>Location</th>
                <th>URL</th>
                <th>Basic Auth Username</th>
                <th>Basic Password</th>
                <th>SDC Number</th>
                <th>Branch Code</th>
                <th>MRC</th>
                <th>Integration Key</th>
                <th>CRA Logo</th>
                <th>EFRIS Logo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let t of efrisChadTerminals">
                <td>{{ t.registerName }}</td>
                <td>{{ t.location || '—' }}</td>
                <td>
                  <input
                    class="form-control form-control-sm"
                    [(ngModel)]="t.url"
                    placeholder="e.g. https://192.168.1.50:9090/efris-chad"
                    style="min-width:220px;"
                  />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.basicAuthUsername" style="min-width:130px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" type="password" [(ngModel)]="t.basicPassword" style="min-width:110px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.sdcNumber" placeholder="e.g. SCV000000001" style="min-width:130px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.branchCode" placeholder="e.g. 004" style="min-width:90px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.mrc" placeholder="e.g. CPF00000002" style="min-width:120px;" />
                </td>
                <td>
                  <input class="form-control form-control-sm" [(ngModel)]="t.integrationKey" style="min-width:160px;" />
                </td>
                <!-- Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad
                     setting" — same data: URL / FileReader pattern as the Rwanda table's own two
                     logo columns above. -->
                <td>
                  <img *ngIf="t.craLogoDataUrl" [src]="t.craLogoDataUrl" alt="CRA logo" style="max-height:32px; max-width:70px; display:block; margin-bottom:4px;" />
                  <input class="form-control form-control-sm" type="file" accept="image/*" (change)="onChadLogoSelected($event, t, 'craLogoDataUrl')" style="min-width:150px;" />
                </td>
                <td>
                  <img *ngIf="t.efrisLogoDataUrl" [src]="t.efrisLogoDataUrl" alt="EFRIS logo" style="max-height:32px; max-width:70px; display:block; margin-bottom:4px;" />
                  <input class="form-control form-control-sm" type="file" accept="image/*" (change)="onChadLogoSelected($event, t, 'efrisLogoDataUrl')" style="min-width:150px;" />
                </td>
                <td>
                  <div class="flex gap-1">
                    <button
                      class="btn btn-sm btn-outline-secondary"
                      (click)="saveChadTerminalSettings(t)"
                      [disabled]="savingChadTerminalId === t.registerId || clearingChadTerminalId === t.registerId"
                    >
                      {{ savingChadTerminalId === t.registerId ? 'Saving…' : 'Save' }}
                    </button>
                    <!-- Task: "One Terminal can have only one EFRIS mapped with" — mirrors the
                         Rwanda table's Clear button: unmaps this terminal from EFRIS Chad so it can
                         be mapped to EFRIS Rwanda instead. -->
                    <button
                      class="btn btn-sm btn-outline-danger"
                      (click)="clearChadTerminalSettings(t)"
                      [disabled]="savingChadTerminalId === t.registerId || clearingChadTerminalId === t.registerId"
                    >
                      {{ clearingChadTerminalId === t.registerId ? 'Clearing…' : 'Clear' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr *ngIf="efrisChadTerminals.length === 0">
                <td colspan="12" class="text-muted">No terminals yet — add one under Registers & Terminal first.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </app-shell>
  `,
})
export class AppSettingsComponent implements OnInit {
  key!: string;
  app: AppSummary | null = null;
  error: string | null = null;

  /** EFRIS-only — see the template's `*ngIf="key === 'EFRIS_RW'"` block. */
  efrisTerminals: EfrisTerminal[] = [];
  terminalsError: string | null = null;
  savingTerminalId: number | null = null;
  /** Task: "One Terminal can have only one EFRIS mapped with" — busy state for the Rwanda table's Clear button. */
  clearingTerminalId: number | null = null;

  /** EFRIS Chad-only — see the template's `*ngIf="key === 'EFRIS_TD'"` block. */
  efrisChadTerminals: EfrisChadTerminal[] = [];
  chadTerminalsError: string | null = null;
  savingChadTerminalId: number | null = null;
  /** Task: "One Terminal can have only one EFRIS mapped with" — busy state for the Chad table's Clear button. */
  clearingChadTerminalId: number | null = null;

  /** Keeps a huge base64 payload out of the request when a logo file is unreasonably large — same limit/reasoning as BusinessDetailsComponent's logo upload; there's no server-side resizing here either. */
  private readonly maxLogoBytes = 2 * 1024 * 1024;

  constructor(
    private route: ActivatedRoute,
    private appsApi: AppsApiService,
    private efrisApi: EfrisApiService,
    private efrisChadApi: EfrisChadApiService
  ) {}

  ngOnInit(): void {
    this.key = this.route.snapshot.paramMap.get('key')!;
    this.appsApi.list().subscribe({
      next: (apps) => {
        this.app = apps.find((a) => a.key === this.key) ?? null;
        if (!this.app?.installed) {
          this.error = 'This app is not installed — go back to Apps and install it first.';
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load app'),
    });
    if (this.key === 'EFRIS_RW') {
      this.loadTerminals();
    }
    if (this.key === 'EFRIS_TD') {
      this.loadChadTerminals();
    }
  }

  private loadTerminals(): void {
    this.efrisApi.listTerminals().subscribe({
      next: (terminals) => (this.efrisTerminals = terminals),
      error: (err) => (this.terminalsError = err?.error?.message ?? 'Failed to load terminals'),
    });
  }

  private loadChadTerminals(): void {
    this.efrisChadApi.listTerminals().subscribe({
      next: (terminals) => (this.efrisChadTerminals = terminals),
      error: (err) => (this.chadTerminalsError = err?.error?.message ?? 'Failed to load terminals'),
    });
  }

  /**
   * Task: "add two logo (URA LOGO, EFRIS LOGO)" — same FileReader.readAsDataURL pattern as
   * BusinessDetailsComponent.onLogoSelected, generalized to whichever of the two logo columns the
   * cashier picked a file for. Only updates the in-memory `terminal` row; nothing is persisted
   * until "Save" is clicked for that row, same as every other field in this table.
   */
  onLogoSelected(event: Event, terminal: EfrisTerminal, field: 'rraLogoDataUrl' | 'efrisLogoDataUrl'): void {
    this.terminalsError = null;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > this.maxLogoBytes) {
      this.terminalsError = 'Logo image is too large (max 2MB) — choose a smaller file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => (terminal[field] = reader.result as string);
    reader.onerror = () => (this.terminalsError = 'Failed to read the selected file');
    reader.readAsDataURL(file);
  }

  saveTerminalSettings(terminal: EfrisTerminal): void {
    this.savingTerminalId = terminal.registerId;
    this.terminalsError = null;
    this.efrisApi
      .saveTerminalSettings(terminal.registerId, {
        efrisUrl: terminal.efrisUrl,
        efrisDb: terminal.efrisDb,
        efrisUid: terminal.efrisUid,
        efrisPassword: terminal.efrisPassword,
        rraLogoDataUrl: terminal.rraLogoDataUrl,
        efrisLogoDataUrl: terminal.efrisLogoDataUrl,
      })
      .subscribe({
        next: (saved) => {
          this.savingTerminalId = null;
          const i = this.efrisTerminals.findIndex((t) => t.registerId === saved.registerId);
          if (i >= 0) this.efrisTerminals[i] = saved;
        },
        error: (err) => {
          this.savingTerminalId = null;
          this.terminalsError = err?.error?.message ?? 'Failed to save terminal settings';
        },
      });
  }

  /**
   * Task: "One Terminal can have only one EFRIS mapped with" — unmaps this terminal from EFRIS
   * Rwanda (deletes its EfrisSetting row) so it's either left unmapped or free to be mapped to
   * EFRIS Chad instead. Confirms first since, unlike Save, this is destructive.
   */
  clearTerminalSettings(terminal: EfrisTerminal): void {
    if (!confirm(`Clear EFRIS Rwanda settings for ${terminal.registerName}? This removes its URL/DB/UID/password and logos.`)) return;
    this.clearingTerminalId = terminal.registerId;
    this.terminalsError = null;
    this.efrisApi.clearTerminalSettings(terminal.registerId).subscribe({
      next: (cleared) => {
        this.clearingTerminalId = null;
        const i = this.efrisTerminals.findIndex((t) => t.registerId === cleared.registerId);
        if (i >= 0) this.efrisTerminals[i] = cleared;
      },
      error: (err) => {
        this.clearingTerminalId = null;
        this.terminalsError = err?.error?.message ?? 'Failed to clear terminal settings';
      },
    });
  }

  /**
   * Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — same
   * FileReader.readAsDataURL pattern as onLogoSelected (Rwanda's own handler), generalized to
   * whichever of EFRIS Chad's two logo columns the cashier picked a file for. Only updates the
   * in-memory `terminal` row; nothing is persisted until "Save" is clicked for that row.
   */
  onChadLogoSelected(event: Event, terminal: EfrisChadTerminal, field: 'craLogoDataUrl' | 'efrisLogoDataUrl'): void {
    this.chadTerminalsError = null;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > this.maxLogoBytes) {
      this.chadTerminalsError = 'Logo image is too large (max 2MB) — choose a smaller file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => (terminal[field] = reader.result as string);
    reader.onerror = () => (this.chadTerminalsError = 'Failed to read the selected file');
    reader.readAsDataURL(file);
  }

  saveChadTerminalSettings(terminal: EfrisChadTerminal): void {
    this.savingChadTerminalId = terminal.registerId;
    this.chadTerminalsError = null;
    this.efrisChadApi
      .saveTerminalSettings(terminal.registerId, {
        url: terminal.url,
        basicAuthUsername: terminal.basicAuthUsername,
        basicPassword: terminal.basicPassword,
        sdcNumber: terminal.sdcNumber,
        branchCode: terminal.branchCode,
        mrc: terminal.mrc,
        integrationKey: terminal.integrationKey,
        craLogoDataUrl: terminal.craLogoDataUrl,
        efrisLogoDataUrl: terminal.efrisLogoDataUrl,
      })
      .subscribe({
        next: (saved) => {
          this.savingChadTerminalId = null;
          const i = this.efrisChadTerminals.findIndex((t) => t.registerId === saved.registerId);
          if (i >= 0) this.efrisChadTerminals[i] = saved;
        },
        error: (err) => {
          this.savingChadTerminalId = null;
          this.chadTerminalsError = err?.error?.message ?? 'Failed to save terminal settings';
        },
      });
  }

  /**
   * Task: "One Terminal can have only one EFRIS mapped with" — mirrors clearTerminalSettings
   * (Rwanda's own Clear handler): unmaps this terminal from EFRIS Chad so it's either left
   * unmapped or free to be mapped to EFRIS Rwanda instead.
   */
  clearChadTerminalSettings(terminal: EfrisChadTerminal): void {
    if (!confirm(`Clear EFRIS Chad settings for ${terminal.registerName}? This removes its URL/credentials, fiscal identifiers, and logos.`)) return;
    this.clearingChadTerminalId = terminal.registerId;
    this.chadTerminalsError = null;
    this.efrisChadApi.clearTerminalSettings(terminal.registerId).subscribe({
      next: (cleared) => {
        this.clearingChadTerminalId = null;
        const i = this.efrisChadTerminals.findIndex((t) => t.registerId === cleared.registerId);
        if (i >= 0) this.efrisChadTerminals[i] = cleared;
      },
      error: (err) => {
        this.clearingChadTerminalId = null;
        this.chadTerminalsError = err?.error?.message ?? 'Failed to clear terminal settings';
      },
    });
  }

  enable(): void {
    this.appsApi.enable(this.key).subscribe({
      next: (a) => (this.app = a),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to enable app'),
    });
  }

  disable(): void {
    this.appsApi.disable(this.key).subscribe({
      next: (a) => (this.app = a),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to disable app'),
    });
  }
}
