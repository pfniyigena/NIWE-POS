import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { CompanyOption, GroupBilling, GroupBlock, GroupDetail, GroupRoom, HotelApiService, RoomType } from '../core/api/hotel-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';
import { downloadBlob } from '../core/download-file';
import { showPdf } from '../core/open-pdf';
import { addDays, errMsg, today } from './hotel-util';
import { billingLabel, groupBadge } from './group-list.component';

/** Hotel > Groups > one group (also "new group"): dates and billing, room blocks with release dates, the rooming list, and the master account. */
@Component({
  selector: 'app-group-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <p><a routerLink="/admin/hotel/groups">← All groups</a></p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>

      <!-- create / edit -->
      <div class="card group-form-card" style="padding:1rem; margin-bottom:1rem;" *ngIf="editing">
        <h4 class="mb-2">{{ g ? 'Edit group' : 'New group' }}</h4>
        <form (ngSubmit)="saveGroup()" class="group-form">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group" style="flex:2; min-width:220px;"><label class="form-label">Group name</label><input class="form-control gname" name="gname" [(ngModel)]="form.name" required placeholder="e.g. Uwase–Habimana wedding" /></div>
            <div class="form-group"><label class="form-label">Arrival</label><input class="form-control garrival" type="date" name="garr" [(ngModel)]="form.arrivalDate" required [min]="g ? '' : todayStr" /></div>
            <div class="form-group"><label class="form-label">Departure</label><input class="form-control gdeparture" type="date" name="gdep" [(ngModel)]="form.departureDate" required /></div>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">Contact person</label><input class="form-control gcontact" name="gcn" [(ngModel)]="form.contactName" /></div>
            <div class="form-group"><label class="form-label">Phone</label><input class="form-control" name="gcp" [(ngModel)]="form.contactPhone" /></div>
            <div class="form-group"><label class="form-label">E-mail</label><input class="form-control" type="email" name="gce" [(ngModel)]="form.contactEmail" /></div>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">Who pays</label>
              <select class="form-control gbilling" name="gbill" [(ngModel)]="form.billing">
                <option value="INDIVIDUAL">Each guest pays their own bill</option>
                <option value="MASTER_ROOMS">The group pays the rooms; guests pay extras</option>
                <option value="MASTER_ALL">The group pays everything</option>
              </select></div>
            <div class="form-group" *ngIf="companies.length"><label class="form-label">Invoice to (optional)</label>
              <select class="form-control gcompany" name="gco" [(ngModel)]="form.companyId"><option [ngValue]="null">—</option><option *ngFor="let c of companies" [ngValue]="c.id">{{ c.name }}</option></select></div>
          </div>
          <div class="form-group"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="gnotes" [(ngModel)]="form.notes"></textarea></div>
          <button class="btn btn-primary save-group" type="submit" [disabled]="busy">{{ g ? 'Save' : 'Create group' }}</button>
          <button class="btn btn-link" type="button" (click)="cancelEdit()">Cancel</button>
        </form>
      </div>

      <ng-container *ngIf="g">
        <div class="flex justify-between align-center mb-2 group-header" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h3>{{ g.groupCode }} · {{ g.name }} <span class="badge" [ngClass]="badge(g.status)">{{ g.status | titlecase }}</span></h3>
            <div class="text-muted">{{ g.arrivalDate }} → {{ g.departureDate }} · {{ billLabel(g.billing) }}<span *ngIf="g.companyName"> · invoice to {{ g.companyName }}</span></div>
            <div class="text-muted" *ngIf="g.contactName || g.contactPhone || g.contactEmail">Contact: {{ g.contactName }} {{ g.contactPhone }} {{ g.contactEmail }}</div>
            <div *ngIf="g.notes" style="margin-top:0.25rem;">{{ g.notes }}</div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-outline-secondary statement-pdf" type="button" (click)="statement()">Statement</button>
            <ng-container *ngIf="canManage && open">
              <button class="btn btn-sm btn-outline-secondary edit-group" type="button" (click)="startEdit()">Edit</button>
              <button class="btn btn-sm btn-primary confirm-group" type="button" *ngIf="g.status === 'TENTATIVE'" [disabled]="busy" (click)="confirm()">Confirm</button>
              <button class="btn btn-sm btn-outline-primary complete-group" type="button" [disabled]="busy" (click)="complete()">Complete</button>
              <button class="btn btn-sm btn-outline-danger cancel-group" type="button" (click)="cancelOpen = !cancelOpen">Cancel group</button>
            </ng-container>
          </div>
        </div>
        <div class="card cancel-panel" style="padding:1rem; margin-bottom:1rem;" *ngIf="cancelOpen">
          <p>This cancels every room that has not arrived and stops holding rooms.</p>
          <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
            <input class="form-control cancel-reason" style="max-width:320px;" name="creason" [(ngModel)]="cancelReason" placeholder="Reason" />
            <label><input type="checkbox" class="cancel-waive" name="cwaive" [(ngModel)]="cancelWaive" /> Waive cancellation fees</label>
            <button class="btn btn-danger do-cancel" type="button" [disabled]="busy" (click)="cancelGroup()">Cancel the group</button>
          </div>
        </div>

        <div class="stat-row flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="stat-tile"><div class="stat-label">Rooms held</div><div class="stat-value held-count">{{ g.roomsBlocked }}</div></div>
          <div class="stat-tile"><div class="stat-label">Rooms booked</div><div class="stat-value booked-count">{{ g.roomsBooked }}</div></div>
          <div class="stat-tile"><div class="stat-label">In house</div><div class="stat-value">{{ g.checkedIn }}</div></div>
          <div class="stat-tile"><div class="stat-label">Group owes</div><div class="stat-value owed-total">{{ g.totals.owed | number: '1.2-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">Deposit held</div><div class="stat-value held-total">{{ g.totals.held | number: '1.2-2' }}</div></div>
        </div>

        <!-- blocks -->
        <div class="card blocks-card" style="padding:1rem; margin-bottom:1rem;">
          <h4 class="mb-2">Room blocks</h4>
          <p class="text-muted" style="font-size:0.85rem;">A block keeps rooms off general sale for the group. Rooms the group has not booked go back on sale on the release date.</p>
          <table class="table-sm blocks-table" *ngIf="g.blocks.length">
            <thead><tr><th>Room type</th><th class="text-end">Rooms</th><th class="text-end">Booked</th><th class="text-end">Unbooked</th><th class="text-end">Rate</th><th>Nights</th><th>Release</th><th>Status</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let b of g.blocks" class="block-row">
                <td>{{ b.roomTypeName }}</td>
                <td class="text-end">{{ b.rooms }}</td>
                <td class="text-end">{{ b.booked }}</td>
                <td class="text-end">{{ b.unbooked }}</td>
                <td class="text-end">{{ b.rate === null ? 'hotel rate' : (b.rate | number: '1.2-2') }}</td>
                <td>{{ b.startDate }} → {{ b.endDate }}</td>
                <td>{{ b.releaseDate || '—' }}</td>
                <td><span class="badge badge-success" *ngIf="b.holding">Holding</span><span class="badge badge-secondary" *ngIf="!b.holding">{{ b.released ? 'Released' : 'Not holding' }}</span></td>
                <td class="flex gap-2" *ngIf="canManage && open">
                  <button class="btn btn-sm btn-outline-secondary edit-block" type="button" (click)="editBlock(b)">Edit</button>
                  <button class="btn btn-sm btn-outline-secondary release-block" type="button" *ngIf="b.holding" [disabled]="busy" (click)="release(b)">Release now</button>
                  <button class="btn btn-sm btn-outline-danger remove-block" type="button" [disabled]="busy" (click)="removeBlock(b)">Remove</button>
                </td>
                <td *ngIf="!(canManage && open)"></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!g.blocks.length">No rooms held yet.</p>
          <form *ngIf="canManage && open" class="hold-form" (ngSubmit)="saveBlock()" style="margin-top:0.75rem;">
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="margin:0;"><label class="form-label">Room type</label>
                <select class="form-control block-type" name="bt" [(ngModel)]="blk.roomTypeId" required><option [ngValue]="null">Choose…</option><option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option></select></div>
              <div class="form-group" style="margin:0;"><label class="form-label">Rooms</label><input class="form-control block-rooms" style="max-width:90px;" type="number" min="1" name="br" [(ngModel)]="blk.rooms" required /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">Group rate / night</label><input class="form-control block-rate" style="max-width:140px;" type="number" min="0" step="0.01" name="brate" [(ngModel)]="blk.rate" placeholder="hotel rate" /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">From</label><input class="form-control block-start" type="date" name="bs" [(ngModel)]="blk.startDate" /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">To</label><input class="form-control block-end" type="date" name="be" [(ngModel)]="blk.endDate" /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">Release unbooked rooms on</label><input class="form-control block-release" type="date" name="brel" [(ngModel)]="blk.releaseDate" /></div>
              <button class="btn btn-primary save-block" type="submit" [disabled]="busy">{{ blockId ? 'Save block' : 'Hold rooms' }}</button>
              <button class="btn btn-link" type="button" *ngIf="blockId" (click)="resetBlock()">Cancel</button>
            </div>
          </form>
        </div>

        <!-- rooming -->
        <div class="card rooming-card" style="padding:1rem; margin-bottom:1rem;">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>Rooming list</h4>
            <div class="flex gap-2">
              <button class="btn btn-sm btn-outline-secondary rooming-xlsx" type="button" (click)="exportRooming('xlsx')">Excel</button>
              <button class="btn btn-sm btn-outline-secondary rooming-pdf" type="button" (click)="exportRooming('pdf')">PDF</button>
              <button class="btn btn-sm btn-outline-secondary rooming-csv" type="button" (click)="exportRooming('csv')">CSV</button>
            </div>
          </div>
          <table class="table-sm rooming-table" *ngIf="g.rooming.length">
            <thead><tr><th>Booking</th><th>Guest</th><th>Room</th><th>Stay</th><th class="text-end">Adults</th><th>Status</th><th>Bill to</th><th class="text-end">Charges</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let r of g.rooming" class="rooming-row">
                <td><a [routerLink]="['/admin/hotel/bookings', r.bookingId]">{{ r.bookingNumber }}</a></td>
                <td>
                  <ng-container *ngIf="editRoom !== r.bookingId">
                    <em class="text-muted" *ngIf="r.unnamed">to be named</em><span *ngIf="!r.unnamed">{{ r.guestName }}</span>
                    <div class="text-muted" style="font-size:0.8rem;" *ngIf="r.guestPhone && !r.unnamed">{{ r.guestPhone }}</div>
                  </ng-container>
                  <ng-container *ngIf="editRoom === r.bookingId">
                    <input class="form-control room-name" style="min-width:150px;" name="rn" [(ngModel)]="roomEdit.name" placeholder="Guest name" />
                    <input class="form-control room-phone" style="min-width:150px; margin-top:0.25rem;" name="rp" [(ngModel)]="roomEdit.phone" placeholder="Phone" />
                  </ng-container>
                </td>
                <td>{{ r.roomTypeName }}<span *ngIf="r.roomNumber"> · {{ r.roomNumber }}</span></td>
                <td>{{ r.checkInDate }} → {{ r.checkOutDate }}</td>
                <td class="text-end">
                  <span *ngIf="editRoom !== r.bookingId">{{ r.adults }}</span>
                  <input *ngIf="editRoom === r.bookingId" class="form-control room-adults" style="max-width:70px;" type="number" min="1" name="ra" [(ngModel)]="roomEdit.adults" />
                </td>
                <td><span class="badge" [ngClass]="statusClass(r.status)">{{ r.status | titlecase }}</span></td>
                <td>{{ r.billTo }}</td>
                <td class="text-end">{{ r.charges | number: '1.2-2' }}</td>
                <td>
                  <ng-container *ngIf="canManage && (r.status === 'BOOKED' || r.status === 'CHECKED_IN') && open">
                    <button class="btn btn-sm btn-outline-secondary rename-room" type="button" *ngIf="editRoom !== r.bookingId" (click)="startRoom(r)">Edit</button>
                    <button class="btn btn-sm btn-primary save-room" type="button" *ngIf="editRoom === r.bookingId" [disabled]="busy" (click)="saveRoom(r)">Save</button>
                    <button class="btn btn-sm btn-link" type="button" *ngIf="editRoom === r.bookingId" (click)="editRoom = null">Cancel</button>
                  </ng-container>
                </td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!g.rooming.length">No rooms booked for this group yet.</p>

          <div *ngIf="canManage && open" style="margin-top:0.75rem;">
            <form class="add-rooms-form" (ngSubmit)="addRooms()">
              <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
                <div class="form-group" style="margin:0;"><label class="form-label">Room type</label>
                  <select class="form-control rooms-type" name="rt" [(ngModel)]="add.roomTypeId" required><option [ngValue]="null">Choose…</option><option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option></select></div>
                <div class="form-group" style="margin:0;"><label class="form-label">How many</label><input class="form-control rooms-count" style="max-width:90px;" type="number" min="1" max="100" name="rc" [(ngModel)]="add.count" /></div>
                <div class="form-group" style="margin:0;"><label class="form-label">Adults per room</label><input class="form-control rooms-adults" style="max-width:90px;" type="number" min="1" name="rad" [(ngModel)]="add.adults" /></div>
                <div class="form-group" style="margin:0;"><label class="form-label">Rate / night (optional)</label><input class="form-control rooms-rate" style="max-width:140px;" type="number" min="0" step="0.01" name="rrate" [(ngModel)]="add.nightlyRate" placeholder="block or hotel rate" /></div>
                <button class="btn btn-primary add-rooms" type="submit" [disabled]="busy">Add rooms</button>
              </div>
            </form>
            <form class="names-form" (ngSubmit)="applyNames()" style="margin-top:0.75rem;">
              <label class="form-label">Paste guest names, one per line — they go on the rooms still to be named</label>
              <textarea class="form-control names-text" rows="4" name="names" [(ngModel)]="namesText" placeholder="Chantal Ingabire&#10;Dieudonné Habimana"></textarea>
              <button class="btn btn-outline-primary apply-names" type="submit" style="margin-top:0.4rem;" [disabled]="busy || !namesText.trim()">Put names on rooms</button>
            </form>
          </div>
        </div>

        <!-- account -->
        <div class="card account-card" style="padding:1rem; margin-bottom:1rem;">
          <h4 class="mb-2">Group account</h4>
          <table class="table-sm no-stack totals-table" style="max-width:420px;">
            <tbody>
              <tr><td>Billed to the group</td><td class="text-end">{{ g.totals.billed | number: '1.2-2' }}</td></tr>
              <tr><td>Deposits received</td><td class="text-end">{{ g.totals.deposits | number: '1.2-2' }}</td></tr>
              <tr><td>Deposit used against bills</td><td class="text-end">{{ g.totals.applied | number: '1.2-2' }}</td></tr>
              <tr><td>Paid</td><td class="text-end">{{ g.totals.paid | number: '1.2-2' }}</td></tr>
              <tr><td>Deposit refunded</td><td class="text-end">{{ g.totals.refunded | number: '1.2-2' }}</td></tr>
              <tr><td><strong>Owed by the group</strong></td><td class="text-end"><strong>{{ g.totals.owed | number: '1.2-2' }}</strong></td></tr>
              <tr><td>Deposit still held</td><td class="text-end">{{ g.totals.held | number: '1.2-2' }}</td></tr>
            </tbody>
          </table>
          <div class="flex gap-2" style="margin:0.75rem 0;" *ngIf="canManage">
            <button class="btn btn-sm btn-outline-primary money-deposit" type="button" *ngIf="open" (click)="openMoney('deposits')">Receive deposit</button>
            <button class="btn btn-sm btn-outline-primary money-payment" type="button" *ngIf="g.totals.owed > 0" (click)="openMoney('payments')">Receive payment</button>
            <button class="btn btn-sm btn-outline-secondary money-refund" type="button" *ngIf="g.totals.held > 0" (click)="openMoney('refunds')">Refund deposit</button>
          </div>
          <form *ngIf="moneyKind" class="money-form" (ngSubmit)="saveMoney()">
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="margin:0;"><label class="form-label">Amount</label><input class="form-control money-amount" style="max-width:150px;" type="number" min="0.01" step="0.01" name="ma" [(ngModel)]="money.amount" required /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">Method</label>
                <select class="form-control money-method" name="mm" [(ngModel)]="money.method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
              <div class="form-group" style="margin:0;" *ngIf="moneyKind !== 'refunds'"><label class="form-label">Reference</label><input class="form-control money-ref" name="mr" [(ngModel)]="money.reference" /></div>
              <button class="btn btn-primary save-money" type="submit" [disabled]="busy">Record {{ moneyKind === 'deposits' ? 'deposit' : moneyKind === 'payments' ? 'payment' : 'refund' }}</button>
              <button class="btn btn-link" type="button" (click)="moneyKind = null">Cancel</button>
            </div>
          </form>
          <table class="table-sm entries-table" *ngIf="g.entries.length" style="margin-top:0.75rem;">
            <thead><tr><th>Date</th><th>Entry</th><th>Detail</th><th>Method</th><th class="text-end">Amount</th></tr></thead>
            <tbody>
              <tr *ngFor="let e of g.entries" class="entry-row">
                <td>{{ e.entryDate }}</td>
                <td>{{ entryLabel(e.kind) }}</td>
                <td>{{ e.description }}<span *ngIf="e.reference"> ({{ e.reference }})</span></td>
                <td>{{ e.method || '' }}</td>
                <td class="text-end">{{ e.kind === 'BILL' || e.kind === 'DEPOSIT' ? '' : '−' }}{{ e.amount | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class GroupDetailComponent implements OnInit {
  g: GroupDetail | null = null;
  types: RoomType[] = [];
  companies: CompanyOption[] = [];
  methods: PaymentMethodOption[] = [];
  error: string | null = null;
  message: string | null = null;
  busy = false;
  editing = false;
  todayStr = today();
  badge = groupBadge;
  billLabel = billingLabel;
  form = this.emptyForm();
  cancelOpen = false;
  cancelReason = '';
  cancelWaive = false;
  blk: { roomTypeId: number | null; rooms: number | null; rate: number | null; startDate: string; endDate: string; releaseDate: string } = { roomTypeId: null, rooms: null, rate: null, startDate: '', endDate: '', releaseDate: '' };
  blockId: number | null = null;
  add: { roomTypeId: number | null; count: number | null; adults: number | null; nightlyRate: number | null } = { roomTypeId: null, count: 1, adults: 2, nightlyRate: null };
  namesText = '';
  editRoom: number | null = null;
  roomEdit = { name: '', phone: '', adults: 1 };
  moneyKind: 'deposits' | 'payments' | 'refunds' | null = null;
  money: { amount: number | null; method: string; reference: string } = { amount: null, method: '', reference: '' };

  constructor(private api: HotelApiService, private pay: PaymentMethodApiService, private route: ActivatedRoute, private router: Router, public auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  get open(): boolean {
    return !!this.g && (this.g.status === 'TENTATIVE' || this.g.status === 'CONFIRMED');
  }

  private emptyForm() {
    return { name: '', contactName: '', contactPhone: '', contactEmail: '', companyId: null as number | null, arrivalDate: addDays(today(), 14), departureDate: addDays(today(), 17), billing: 'INDIVIDUAL' as GroupBilling, notes: '' };
  }

  ngOnInit(): void {
    this.api.roomTypes().subscribe((t) => (this.types = t.filter((x) => x.active)));
    this.api.companies().subscribe((c) => (this.companies = c), () => (this.companies = []));
    this.pay.listActive().subscribe((m) => {
      this.methods = m.filter((x) => !x.credit && !x.roomCharge);
      this.money.method = (this.methods.find((x) => x.cash) ?? this.methods[0])?.code ?? '';
    });
    this.route.paramMap.subscribe((p) => {
      const id = p.get('id');
      this.message = this.error = null;
      if (id === 'new') {
        this.g = null;
        this.editing = true;
        this.form = this.emptyForm();
      } else {
        this.editing = false;
        this.load(Number(id));
      }
    });
  }

  load(id: number): void {
    this.api.group(id).subscribe({
      next: (g) => this.show(g),
      error: (e) => (this.error = errMsg(e, 'Failed to load the group')),
    });
  }

  private show(g: GroupDetail): void {
    this.g = g;
    if (!this.blockId) this.resetBlock();
  }

  statusClass(s: string): string {
    return s === 'CHECKED_IN' ? 'badge-success' : s === 'BOOKED' ? 'badge-info' : s === 'CHECKED_OUT' ? 'badge-secondary' : 'badge-danger';
  }

  entryLabel(k: string): string {
    return { BILL: 'Billed to group', DEPOSIT: 'Deposit received', APPLIED: 'Deposit used', PAYMENT: 'Payment received', REFUND: 'Deposit refunded' }[k] ?? k;
  }

  private run(call: () => ReturnType<HotelApiService['group']>, ok: (g: GroupDetail) => void, fail: string): void {
    this.busy = true;
    this.error = this.message = null;
    call().subscribe({
      next: (g) => {
        this.busy = false;
        this.show(g);
        ok(g);
      },
      error: (e) => {
        this.busy = false;
        this.error = errMsg(e, fail);
      },
    });
  }

  // ---- group
  startEdit(): void {
    if (!this.g) return;
    this.form = { name: this.g.name, contactName: this.g.contactName ?? '', contactPhone: this.g.contactPhone ?? '', contactEmail: this.g.contactEmail ?? '', companyId: this.g.companyId, arrivalDate: this.g.arrivalDate, departureDate: this.g.departureDate, billing: this.g.billing, notes: this.g.notes ?? '' };
    this.editing = true;
  }

  cancelEdit(): void {
    this.editing = false;
    if (!this.g) this.router.navigate(['/admin/hotel/groups']);
  }

  saveGroup(): void {
    const payload = { ...this.form, contactName: this.form.contactName || null, contactPhone: this.form.contactPhone || null, contactEmail: this.form.contactEmail || null, notes: this.form.notes || null };
    if (this.g) {
      this.run(() => this.api.updateGroup(this.g!.id, payload), () => { this.editing = false; this.message = 'Group saved.'; }, 'Could not save the group');
    } else {
      this.busy = true;
      this.error = null;
      this.api.createGroup(payload).subscribe({
        next: (g) => { this.busy = false; this.router.navigate(['/admin/hotel/groups', g.id]); },
        error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not create the group'); },
      });
    }
  }

  confirm(): void {
    this.run(() => this.api.confirmGroup(this.g!.id), () => (this.message = 'Group confirmed.'), 'Could not confirm the group');
  }

  complete(): void {
    this.run(() => this.api.completeGroup(this.g!.id), () => (this.message = 'Group completed.'), 'Could not complete the group');
  }

  cancelGroup(): void {
    this.run(() => this.api.cancelGroup(this.g!.id, this.cancelReason || null, this.cancelWaive), () => { this.cancelOpen = false; this.message = 'Group cancelled.'; }, 'Could not cancel the group');
  }

  statement(): void {
    showPdf(this.api.groupStatementPdf(this.g!.id), `statement-${this.g!.groupCode}.pdf`, (err) => { if (err) this.error = err; });
  }

  // ---- blocks
  resetBlock(): void {
    this.blockId = null;
    this.blk = { roomTypeId: null, rooms: null, rate: null, startDate: this.g?.arrivalDate ?? '', endDate: this.g?.departureDate ?? '', releaseDate: '' };
  }

  editBlock(b: GroupBlock): void {
    this.blockId = b.id;
    this.blk = { roomTypeId: b.roomTypeId, rooms: b.rooms, rate: b.rate, startDate: b.startDate, endDate: b.endDate, releaseDate: b.releaseDate ?? '' };
  }

  saveBlock(): void {
    if (!this.blk.roomTypeId || !this.blk.rooms) return;
    const payload = { roomTypeId: this.blk.roomTypeId, rooms: this.blk.rooms, rate: this.blk.rate, startDate: this.blk.startDate || null, endDate: this.blk.endDate || null, releaseDate: this.blk.releaseDate || null };
    const id = this.blockId;
    this.run(() => (id ? this.api.updateGroupBlock(this.g!.id, id, payload) : this.api.addGroupBlock(this.g!.id, payload)), () => {
      this.blockId = null;
      this.resetBlock();
      this.message = id ? 'Block saved.' : 'Rooms are now held for the group.';
    }, 'Could not hold the rooms');
  }

  release(b: GroupBlock): void {
    this.run(() => this.api.releaseGroupBlock(this.g!.id, b.id), () => (this.message = `The unbooked ${b.roomTypeName} rooms are back on sale.`), 'Could not release the block');
  }

  removeBlock(b: GroupBlock): void {
    this.run(() => this.api.deleteGroupBlock(this.g!.id, b.id), () => (this.message = 'Block removed.'), 'Could not remove the block');
  }

  // ---- rooming
  addRooms(): void {
    if (!this.add.roomTypeId) return;
    const count = this.add.count ?? 1;
    this.run(() => this.api.addGroupRooms(this.g!.id, { roomTypeId: this.add.roomTypeId!, count, names: null, adults: this.add.adults, children: null, checkInDate: null, checkOutDate: null, nightlyRate: this.add.nightlyRate }), () => (this.message = `${count} room${count === 1 ? '' : 's'} added.`), 'Could not add the rooms');
  }

  applyNames(): void {
    const names = this.namesText.split(/\r?\n/).map((n) => n.trim()).filter((n) => n);
    if (!names.length) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.nameGroupRooms(this.g!.id, names).subscribe({
      next: (r) => {
        this.busy = false;
        this.show(r.group);
        this.namesText = '';
        this.message = `${r.assigned} name${r.assigned === 1 ? '' : 's'} placed on rooms.` + (r.leftover ? ` ${r.leftover} name${r.leftover === 1 ? ' was' : 's were'} left over — add more rooms first.` : '');
      },
      error: (e) => {
        this.busy = false;
        this.error = errMsg(e, 'Could not place the names');
      },
    });
  }

  startRoom(r: GroupRoom): void {
    this.editRoom = r.bookingId;
    this.roomEdit = { name: r.unnamed ? '' : r.guestName, phone: r.guestPhone ?? '', adults: r.adults };
  }

  saveRoom(r: GroupRoom): void {
    const body: { guest?: { fullName: string; phone?: string | null }; adults?: number } = { adults: this.roomEdit.adults };
    if (this.roomEdit.name.trim() && (this.roomEdit.name.trim() !== r.guestName || (this.roomEdit.phone || null) !== r.guestPhone)) {
      body.guest = { fullName: this.roomEdit.name.trim(), phone: this.roomEdit.phone || null };
    }
    this.run(() => this.api.updateGroupRoom(this.g!.id, r.bookingId, body), () => { this.editRoom = null; this.message = 'Room updated.'; }, 'Could not update the room');
  }

  exportRooming(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.api.roomingExport(this.g!.id, format).subscribe({
      next: (b) => downloadBlob(b, `rooming-${this.g!.groupCode}.${format}`),
      error: () => (this.error = 'The export failed.'),
    });
  }

  // ---- money
  openMoney(kind: 'deposits' | 'payments' | 'refunds'): void {
    this.moneyKind = kind;
    this.money.amount = kind === 'payments' ? this.g!.totals.owed : kind === 'refunds' ? this.g!.totals.held : null;
    this.money.reference = '';
  }

  saveMoney(): void {
    if (!this.money.amount || !this.moneyKind) return;
    const kind = this.moneyKind;
    const payload = { method: this.money.method, amount: this.money.amount, reference: this.money.reference || null, note: null };
    this.run(() => this.api.groupMoney(this.g!.id, kind, payload), () => { this.moneyKind = null; this.message = 'Recorded.'; }, 'Could not record the money');
  }
}
