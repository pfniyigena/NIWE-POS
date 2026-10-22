import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { MenuApiService } from '../core/api/menu-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { KitchenApiService } from '../core/api/kitchen-api.service';
import { HotelApiService, InHouseGuest } from '../core/api/hotel-api.service';
import { DiningTable, MenuCategory, MenuItem, OptionGroup, PaymentMethodOption, RestaurantOrder, RestaurantSettings } from '../core/models';
import { DiningTableApiService } from '../core/api/dining-table-api.service';

interface PaymentRow {
  method: string;
  amount: number;
}

/**
 * The dine-in order-taking screen for one table's open order (Fourier: "New dedicated screen,"
 * kept entirely separate from the walk-up POS terminal's own checkout flow). Add items from the
 * menu (grouped by category — the same menu built in Admin &gt; Restaurant &gt; Menu), send a
 * round to the kitchen, and settle the bill with one or more payments (Fourier: "split & merge
 * bills").
 */
@Component({
  selector: 'app-table-order',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!order && !error" class="text-muted">Loading…</div>

      <div *ngIf="order">
        <div class="flex justify-between align-center mb-1">
          <h3>
            {{ order.displayLabel }}
            <ng-container *ngIf="order.orderType === 'DINE_IN'"> — {{ order.guestCount }} guest{{ order.guestCount === 1 ? '' : 's' }}</ng-container>
            <span class="text-muted" style="font-size:0.9rem;"> ({{ order.status }})</span>
            <span class="to-source" *ngIf="order.source === 'QR' || order.source === 'WEB'" style="font-size:0.75rem; background:#dbeafe; color:#1e40af; border-radius:4px; padding:0.1rem 0.4rem; margin-left:0.4rem;">{{ order.source === 'QR' ? 'Ordered from the table' : 'Online order' }}{{ order.onlineOrderCode ? ' ' + order.onlineOrderCode : '' }}</span>
          </h3>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="order.status === 'CLOSED'" [routerLink]="['/admin/restaurant/receipts', order.id]">🧾 View receipt</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'DINE_IN'" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'DELIVERY'" routerLink="/admin/restaurant/deliveries">← Deliveries board</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'TAKEAWAY'" routerLink="/admin/restaurant/orders/new">← New order</a>
          </div>
        </div>
        <div class="text-muted mb-3" *ngIf="order.orderType !== 'DINE_IN'">
          <span *ngIf="order.customerPhone">📞 {{ order.customerPhone }}</span>
          <span *ngIf="order.customerAddress"> · 📍 {{ order.customerAddress }}</span>
          <span *ngIf="order.orderType === 'DELIVERY' && order.deliveryStatus">
            · {{ order.deliveryStatus.replace('_', ' ') }}<ng-container *ngIf="order.driverName"> ({{ order.driverName }})</ng-container>
          </span>
        </div>

        <div class="checks-bar flex gap-2 align-center mb-2" *ngIf="siblings.length > 1">
          <span class="text-muted">Checks at this table:</span>
          <a *ngFor="let c of siblings" class="check-link btn btn-sm" [class.btn-primary]="c.id === order.id" [class.btn-outline-secondary]="c.id !== order.id"
             [routerLink]="['/admin/restaurant/orders', c.id]">{{ c.checkLabel || 'Check' }} · {{ c.total | number: '1.2-2' }}</a>
        </div>
        <div class="alert alert-success move-info" *ngIf="moveInfo">{{ moveInfo }}</div>

        <div class="alert alert-warning guest-allergens flex justify-between align-center" *ngIf="order.guestAllergens">
          <span>⚠ Guest allergies: <strong>{{ pretty(order.guestAllergens) }}</strong></span>
        </div>

        <div class="flex gap-3" style="align-items:flex-start;">
          <div class="card" style="padding:1rem; flex:1;" *ngIf="order.status === 'OPEN'">
            <h4 style="font-size:1rem;" class="mb-2">Menu</h4>
            <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
              <button
                *ngFor="let c of categories"
                class="btn btn-sm"
                [class.btn-primary]="selectedCategory?.id === c.id"
                [class.btn-outline-secondary]="selectedCategory?.id !== c.id"
                (click)="selectCategory(c)"
              >
                {{ c.name }}
              </button>
            </div>
            <div class="menu-grid">
              <button type="button" class="menu-item" *ngFor="let item of items" [class.sold-out]="item.soldOut" [disabled]="item.soldOut" [title]="item.soldOut ? 'Sold out' + (item.soldOutReason ? ' — ' + item.soldOutReason : '') : ''" (click)="onTile(item)">
                <div>{{ item.product?.name }}<span class="mi-options" *ngIf="groupsOf(item).length > 0" title="Has choices"> ⚙</span></div>
                <div class="text-muted">{{ item.effectivePrice | number: '1.2-2' }}</div>
                <div class="mi-allergens" *ngIf="item.allergens" style="font-size:0.7rem; color:#b45309;">{{ pretty(item.allergens) }}</div>
              </button>
              <p class="text-muted" *ngIf="items.length === 0">No items in this category yet.</p>
            </div>
          </div>

          <div class="card" style="padding:1rem; flex:1;">
            <h4 style="font-size:1rem;" class="mb-2">Order</h4>
            <table class="table-sm mb-2">
              <thead>
                <tr>
                  <th>Item</th>
                  <th class="text-end">Qty</th>
                  <th class="text-end">Total</th>
                  <th>Kitchen</th>
                  <th *ngIf="order.status === 'OPEN'"></th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let line of order.lines">
                  <td>
                    {{ line.description }}<span class="text-muted line-note" *ngIf="line.notes"> — {{ line.notes }}</span>
                    <div class="line-options text-muted" *ngIf="line.options && line.options.length > 0" style="font-size:0.8rem;">
                      {{ optionText(line) }}<span *ngIf="(line.optionsPrice ?? 0) !== 0"> ({{ (line.optionsPrice ?? 0) > 0 ? '+' : '' }}{{ line.optionsPrice | number: '1.2-2' }} each)</span>
                    </div>
                    <div style="font-size:0.75rem;">
                      <span class="line-course badge" *ngIf="showCourses()">Course {{ line.course }}</span>
                      <span class="line-held" *ngIf="line.held" style="color:#b45309;"> ⏸ on hold</span>
                      <span class="line-allergens text-muted" *ngIf="line.allergens"> {{ pretty(line.allergens) }}</span>
 
                      <span class="line-seat text-muted" *ngIf="line.seat"> Seat {{ line.seat }}</span>
                      <span class="line-comped" *ngIf="line.comped" style="color:#047857; font-weight:600;"> 🎁 comped</span>
                      <span class="line-conflict" *ngIf="line.allergyConflict" style="color:#b91c1c; font-weight:600;"> ⚠ contains {{ pretty(line.allergyConflict) }}</span>
                    </div>
                    <div class="line-discount text-muted" *ngIf="(line.discountPercent ?? 0) > 0" style="font-size:0.8rem;">−{{ line.discountPercent }}% ({{ line.discountReason }})</div>
                    <div class="void-form mt-1" *ngIf="voidLineId === line.id">
                      <div class="text-muted" style="font-size:0.8rem;">This item has already gone to the kitchen — a manager must approve taking it off.</div>
                      <div class="flex gap-2 align-center mt-1">
                        <input class="form-control void-reason" style="flex:1;" [(ngModel)]="voidReason" [name]="'vr' + line.id" placeholder="Why? (required)" maxlength="200" />
                        <button class="btn btn-sm btn-danger void-confirm" (click)="confirmVoid(line)">Void it</button>
                        <button class="btn btn-sm btn-link" (click)="voidLineId = null">Close</button>
                      </div>
                      <div class="flex gap-1 mt-1" style="flex-wrap:wrap;"><button type="button" class="btn btn-sm btn-outline-secondary void-chip" *ngFor="let r of voidPresets" (click)="voidReason = r">{{ r }}</button></div>
                    </div>
                    <div class="comp-form mt-1" *ngIf="compLineId === line.id">
                      <div class="flex gap-2 align-center">
                        <input class="form-control comp-reason" style="flex:1;" [(ngModel)]="compReason" [name]="'cr' + line.id" placeholder="Why is it free? (required)" maxlength="200" />
                        <button class="btn btn-sm btn-primary comp-confirm" (click)="confirmComp(line)">Give free</button>
                        <button class="btn btn-sm btn-link" (click)="compLineId = null">Close</button>
                      </div>
                      <div class="flex gap-1 mt-1" style="flex-wrap:wrap;"><button type="button" class="btn btn-sm btn-outline-secondary comp-chip" *ngFor="let r of compPresets" (click)="compReason = r">{{ r }}</button></div>
                    </div>
                    <div class="line-disc-form flex gap-2 align-center mt-1" *ngIf="discountLineId === line.id">
                      <input class="form-control ld-percent" type="number" min="0" max="100" style="width:80px;" [(ngModel)]="ldPercent" [name]="'ldp' + line.id" placeholder="%" />
                      <input class="form-control ld-reason" style="flex:1;" [(ngModel)]="ldReason" [name]="'ldr' + line.id" placeholder="Reason" />
                      <button class="btn btn-sm btn-primary ld-apply" (click)="applyLineDiscount(line)">Apply</button>
                      <button class="btn btn-sm btn-link" (click)="discountLineId = null">Close</button>
                    </div>
                  </td>
                  <td class="text-end">
                    <ng-container *ngIf="order.status === 'OPEN'">
                      <button class="btn btn-sm btn-outline-secondary" (click)="changeQty(line, -1)">−</button>
                      {{ line.quantity }}
                      <button class="btn btn-sm btn-outline-secondary" (click)="changeQty(line, 1)">+</button>
                    </ng-container>
                    <span *ngIf="order.status !== 'OPEN'">{{ line.quantity }}</span>
                  </td>
                  <td class="text-end">{{ line.lineTotal | number: '1.2-2' }}</td>
                  <td><span class="text-muted">{{ line.kitchenStatus }}</span>
                    <span class="line-remade" *ngIf="(line.remakeCount ?? 0) > 0" style="color:#b45309; font-size:0.75rem;"> remade ×{{ line.remakeCount }}</span>
                    <button class="btn btn-sm btn-link line-refire-btn" *ngIf="order.status === 'OPEN' && line.kitchenStatus !== 'PENDING'" (click)="sendBack(line)">Send back</button></td>
                  <td *ngIf="order.status === 'OPEN'" class="flex gap-1">
                    <button class="btn btn-sm btn-outline-secondary line-edit-btn" title="Note, course and choices" (click)="openEditPanel(line)">✎</button>
                    <button class="btn btn-sm btn-outline-secondary line-disc-btn" title="Discount this item" (click)="openLineDiscount(line)">%</button>
                    <button class="btn btn-sm btn-outline-secondary line-comp-btn" *ngIf="!line.comped" title="Give this item free" (click)="openComp(line)">🎁</button>
                    <button class="btn btn-sm btn-outline-secondary uncomp-btn" *ngIf="line.comped" title="Charge this item again" (click)="uncomp(line)">Charge</button>
                    <button class="btn btn-sm btn-outline-danger line-remove-btn" (click)="startRemove(line)">✕</button>
                  </td>
                </tr>
                <tr *ngIf="order.lines.length === 0">
                  <td colspan="5" class="text-muted">No items yet — add from the menu.</td>
                </tr>
              </tbody>
            </table>

            <div class="card item-panel mb-3" *ngIf="panel" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));">
              <h4 style="font-size:1rem;" class="mb-2">{{ panel.mode === 'add' ? 'Add' : 'Change' }} {{ panel.item.product?.name }}</h4>
              <div class="alert alert-danger" *ngIf="panelError">{{ panelError }}</div>
              <div class="flex gap-2 mb-2 align-center" *ngIf="panel.mode === 'add'">
                <label class="form-label" style="margin:0;">Quantity</label>
                <input class="form-control ip-qty" type="number" min="1" style="width:80px;" [(ngModel)]="pQty" name="pQty" />
              </div>
              <div class="mb-2" *ngIf="panel.mode === 'edit' && !panelEditable()" style="font-size:0.85rem; color:#b45309;">
                This line has already gone to the kitchen — only its note can change now.
              </div>
              <div class="mb-2" *ngFor="let g of panelGroups()">
                <div style="font-weight:600;">{{ g.name }}<span class="text-muted" style="font-weight:400; font-size:0.8rem;"> {{ g.required ? '— choose' + (g.maxSelect === 1 ? ' one' : '') : '— optional' }}{{ g.maxSelect > 1 ? ' (up to ' + g.maxSelect + ')' : '' }}</span></div>
                <div class="flex gap-2" style="flex-wrap:wrap;">
                  <button type="button" class="btn btn-sm ip-option" *ngFor="let o of g.options"
                          [class.btn-primary]="isPicked(g, o.id!)" [class.btn-outline-secondary]="!isPicked(g, o.id!)"
                          [disabled]="!panelEditable()" (click)="togglePick(g, o.id!)">
                    {{ o.name }}<span *ngIf="o.priceDelta !== 0"> ({{ o.priceDelta > 0 ? '+' : '' }}{{ o.priceDelta | number: '1.2-2' }})</span>
                  </button>
                </div>
              </div>
              <div class="flex gap-2 mb-2 align-center">
                <label class="form-label" style="margin:0;">Seat</label>
                <input class="form-control ip-seat" type="number" min="1" max="40" style="width:80px;" [(ngModel)]="pSeat" name="pSeat" placeholder="optional" />
              </div>
              <div class="flex gap-2 mb-2 align-center" *ngIf="panelEditable()">
                <label class="form-label" style="margin:0;">Course</label>
                <select class="form-select ip-course" style="width:110px;" [(ngModel)]="pCourse" name="pCourse">
                  <option *ngFor="let c of [1,2,3,4,5]" [ngValue]="c">Course {{ c }}</option>
                </select>
              </div>
              <div class="mb-2">
                <label class="form-label">Note for the kitchen</label>
                <input class="form-control ip-notes" [(ngModel)]="pNotes" name="pNotes" maxlength="200" />
                <div class="flex gap-1 mt-1" style="flex-wrap:wrap;">
                  <button type="button" class="btn btn-sm btn-outline-secondary note-chip" *ngFor="let n of notePresets" (click)="addNoteChip(n)">{{ n }}</button>
                </div>
              </div>
              <div class="flex gap-2">
                <button class="btn btn-primary ip-save" [disabled]="panelBusy" (click)="savePanel()">{{ panel.mode === 'add' ? 'Add to order' : 'Save' }}</button>
                <button class="btn btn-link ip-cancel" (click)="closePanel()">Cancel</button>
              </div>
            </div>

            <div class="totals mb-3">
              <div class="flex justify-between t-discount" *ngIf="(order.discountTotal ?? 0) > 0"><span>Discounts given</span><span>−{{ order.discountTotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between"><span>Subtotal</span><span>{{ order.subtotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-service" *ngIf="(order.serviceChargePercent ?? 0) > 0">
                <span>Service charge ({{ order.serviceChargePercent }}%)<span *ngIf="order.serviceChargeWaived"> — waived</span>
                  <button class="btn btn-sm btn-link sc-toggle" *ngIf="order.status === 'OPEN'" (click)="toggleServiceCharge()">{{ order.serviceChargeWaived ? 'Restore' : 'Waive' }}</button></span>
                <span>{{ (order.serviceCharge ?? 0) | number: '1.2-2' }}</span>
              </div>
              <div class="flex justify-between"><span>Tax</span><span>{{ order.taxTotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-rounding" *ngIf="(order.roundingAdjustment ?? 0) !== 0"><span>Rounding</span><span>{{ order.roundingAdjustment | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-total" style="font-weight:600;"><span>Total</span><span>{{ order.total | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-tip" *ngIf="(order.tipAmount ?? 0) > 0"><span>Tip</span><span>{{ order.tipAmount | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-deposit" *ngIf="depositUsed() > 0"><span>Reservation deposit already paid</span><span>−{{ depositUsed() | number: '1.2-2' }}</span></div>
              <div class="flex justify-between" *ngIf="order.paidTotal > 0"><span>Paid</span><span>{{ order.paidTotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between t-balance" *ngIf="order.paidTotal > 0 || depositUsed() > 0" style="font-weight:600;"><span>Balance due</span><span>{{ order.balanceDue | number: '1.2-2' }}</span></div>
            </div>

            <div class="flex gap-2 mb-3" *ngIf="order.status === 'OPEN'">
              <button class="btn btn-outline-primary send-kitchen" [disabled]="order.lines.length === 0" (click)="sendToKitchen()">Send to kitchen</button>
              <button class="btn btn-outline-primary fire-course" *ngFor="let c of heldCourses()" (click)="fireCourse(c)">Fire course {{ c }}</button>
              <button class="btn btn-outline-secondary allergy-btn" (click)="toggleAllergy()">Allergies</button>
              <button class="btn btn-primary" [disabled]="order.lines.length === 0" (click)="toggleCheckout()">Checkout</button>
              <button class="btn btn-outline-secondary discount-btn" [disabled]="order.lines.length === 0" (click)="toggleDiscount()">Discount</button>
              <button class="btn btn-outline-secondary split-btn" [disabled]="order.lines.length === 0" (click)="openMove()">Split / move items…</button>
              <button class="btn btn-outline-secondary move-table-btn" *ngIf="order.orderType === 'DINE_IN'" (click)="openMoveTable()">Move table…</button>
              <button class="btn btn-outline-secondary" (click)="onToggleMerge()">Merge into…</button>
              <button class="btn btn-outline-danger cancel-btn" (click)="cancelOrder()">Cancel order</button>
            </div>

            <div class="text-muted held-note mb-2" style="font-size:0.85rem;" *ngIf="order.status === 'OPEN' && heldCount() > 0">⏸ {{ heldCount() }} item{{ heldCount() === 1 ? '' : 's' }} waiting for their course to be fired.</div>

            <div class="card move-panel mb-3" *ngIf="showMove" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));">
              <h4 style="font-size:1rem;" class="mb-2">Split or move items</h4>
              <div class="flex gap-2 align-center mb-2">
                <label class="form-label" style="margin:0;">Move to</label>
                <select class="form-select mv-dest" style="width:auto;" [(ngModel)]="mvDest" name="mvDest">
                  <option *ngIf="order.orderType === 'DINE_IN'" value="new">A new check at this table</option>
                  <option *ngFor="let o of moveTargets()" [value]="o.id">{{ o.displayLabel }} ({{ o.total | number: '1.2-2' }})</option>
                </select>
              </div>
              <div class="flex gap-2 mb-2" *ngIf="seatList().length > 0" style="flex-wrap:wrap;">
                <span class="text-muted" style="align-self:center;">By seat:</span>
                <button type="button" class="btn btn-sm mv-seat" *ngFor="let n of seatList()" [class.btn-primary]="seatAllPicked(n)" [class.btn-outline-secondary]="!seatAllPicked(n)" (click)="toggleSeat(n)">Seat {{ n }}</button>
              </div>
              <div class="flex justify-between align-center mb-1" *ngFor="let line of order.lines">
                <label><input type="checkbox" class="mv-chk" [checked]="mvPick[line.id] != null" (change)="mvToggle(line, $any($event.target).checked)" /> {{ line.quantity }}× {{ line.description }}<span class="text-muted" *ngIf="line.seat"> (seat {{ line.seat }})</span> — {{ line.lineGrandTotal | number: '1.2-2' }}</label>
                <span *ngIf="mvPick[line.id] != null && line.quantity > 1">move <input type="number" class="form-control mv-qty" style="width:70px; display:inline-block;" min="1" [max]="line.quantity" [ngModel]="mvPick[line.id]" (ngModelChange)="mvPick[line.id] = $event" [name]="'mq' + line.id" /> of {{ line.quantity }}</span>
              </div>
              <div class="flex gap-2 mt-2">
                <button class="btn btn-primary mv-go" (click)="doMove()">Move</button>
                <button class="btn btn-link" (click)="showMove = false">Close</button>
              </div>
            </div>

            <div class="card mt-panel mb-3" *ngIf="showMoveTable" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));">
              <h4 style="font-size:1rem;" class="mb-2">Move this bill to another table</h4>
              <div class="flex gap-2">
                <select class="form-select mt-table" [(ngModel)]="mtTableId" name="mtTable">
                  <option [ngValue]="null">Choose a free table…</option>
                  <option *ngFor="let t of freeTables" [ngValue]="t.id">{{ t.name }}<ng-container *ngIf="t.section"> — {{ t.section }}</ng-container></option>
                </select>
                <button class="btn btn-primary mt-go" [disabled]="!mtTableId" (click)="doMoveTable()">Move</button>
              </div>
              <div class="text-muted" style="font-size:0.85rem;" *ngIf="freeTables.length === 0">No table is free right now.</div>
            </div>

            <div class="card cancel-panel mb-3" *ngIf="showCancel" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));">
              <h4 style="font-size:1rem;" class="mb-2">Cancel this order</h4>
              <div class="text-muted mb-2" style="font-size:0.85rem;">This frees the table without recording a sale.<span *ngIf="hasSent()"> Some of it has already gone to the kitchen, so a reason and a manager's approval are needed.</span></div>
              <div class="flex gap-2">
                <input class="form-control cancel-reason" style="flex:1;" [(ngModel)]="cancelReason" name="cancelReason" [placeholder]="hasSent() ? 'Why? (required)' : 'Why? (optional)'" maxlength="200" />
                <button class="btn btn-danger cancel-confirm" (click)="doCancel()">Cancel the order</button>
                <button class="btn btn-link" (click)="showCancel = false">Keep it</button>
              </div>
            </div>

            <div class="card allergy-panel mb-3" *ngIf="showAllergy" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));">
              <h4 style="font-size:1rem;" class="mb-2">Guest allergies</h4>
              <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
                <label *ngFor="let a of allergenList"><input type="checkbox" class="al-chk" [attr.data-al]="a" [(ngModel)]="alSel[a]" [name]="'al' + a" /> {{ pretty(a) }}</label>
              </div>
              <div class="flex gap-2">
                <button class="btn btn-primary al-save" (click)="saveAllergy()">Save</button>
                <button class="btn btn-link" (click)="showAllergy = false">Close</button>
              </div>
            </div>

            <div class="card order-discount" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));" *ngIf="showDiscount">
              <h4 style="font-size:1rem;" class="mb-2">Discount on the whole bill</h4>
              <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
                <select class="form-select od-kind" style="width:auto;" [(ngModel)]="odKind" name="odKind">
                  <option value="percent">Percent</option>
                  <option value="amount">Amount</option>
                </select>
                <input class="form-control od-value" type="number" min="0" step="0.01" style="width:120px;" [(ngModel)]="odValue" name="odValue" />
                <input class="form-control od-reason" style="flex:1; min-width:160px;" [(ngModel)]="odReason" name="odReason" placeholder="Reason (required)" />
              </div>
              <div class="flex gap-2">
                <button class="btn btn-primary od-apply" (click)="applyOrderDiscount()">Apply</button>
                <button class="btn btn-outline-danger od-remove" *ngIf="order.orderDiscountPercent || order.orderDiscountAmount" (click)="removeOrderDiscount()">Remove the discount</button>
                <span class="text-muted" style="align-self:center; font-size:0.85rem;" *ngIf="order.discountReason">Now: {{ order.discountReason }}<span *ngIf="order.discountBy"> ({{ order.discountBy }})</span></span>
              </div>
            </div>

            <div class="card" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));" *ngIf="showCheckout">
              <h4 style="font-size:1rem;" class="mb-2">Payments</h4>

              <div class="flex gap-2 mb-2">
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'custom'" [class.btn-outline-secondary]="splitMode !== 'custom'" (click)="setSplitMode('custom')">
                  Custom amount
                </button>
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'equal'" [class.btn-outline-secondary]="splitMode !== 'equal'" (click)="setSplitMode('equal')">
                  Split equally
                </button>
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'byItem'" [class.btn-outline-secondary]="splitMode !== 'byItem'" (click)="setSplitMode('byItem')">
                  Split by item
                </button>
              </div>

              <div class="flex gap-2 align-center mb-2" *ngIf="splitMode === 'equal'">
                <label class="form-label" style="margin:0;">Split into</label>
                <input class="form-control" type="number" min="2" style="width:80px;" [(ngModel)]="splitCount" name="splitCount" />
                <span>guests' shares</span>
                <button class="btn btn-sm btn-outline-primary" (click)="applyEqualSplit()">Apply</button>
              </div>

              <div class="mb-2" *ngIf="splitMode === 'byItem'">
                <div class="flex gap-2 align-center mb-2">
                  <label class="form-label" style="margin:0;">Number of guests' shares</label>
                  <input class="form-control" type="number" min="2" style="width:80px;" [(ngModel)]="splitCount" name="splitCountItem" />
                </div>
                <div class="flex justify-between align-center mb-1" *ngFor="let line of order.lines">
                  <span>{{ line.quantity }}× {{ line.description }} — {{ line.lineGrandTotal | number: '1.2-2' }}</span>
                  <select
                    class="form-select"
                    style="width:140px;"
                    [ngModel]="lineShares[line.id] ?? null"
                    (ngModelChange)="setLineShare(line.id, $event)"
                    [name]="'share' + line.id"
                  >
                    <option [ngValue]="null">Unassigned</option>
                    <option *ngFor="let n of shareRange()" [ngValue]="n">Share {{ n }}</option>
                  </select>
                </div>
                <button class="btn btn-sm btn-outline-primary mt-2" (click)="applyItemSplit()">Apply</button>
              </div>

              <div class="flex gap-2 align-center mb-2" *ngFor="let row of paymentRows; let i = index">
                <span class="text-muted" style="width:70px;" *ngIf="splitMode !== 'custom'">Share {{ i + 1 }}</span>
                <select class="form-select" style="flex:1;" [(ngModel)]="row.method" [name]="'method' + i">
                  <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
                </select>
                <input class="form-control" type="number" style="width:120px;" min="0" step="0.01" [(ngModel)]="row.amount" [name]="'amount' + i" />
                <button class="btn btn-sm btn-outline-danger" (click)="removePaymentRow(i)">✕</button>
              </div>
              <div class="form-group mb-2" *ngIf="roomRowSelected()">
                <label class="form-label">Charge to which room?</label>
                <select class="form-select" [(ngModel)]="roomBookingId" name="roomBooking">
                  <option [ngValue]="null">Choose the guest…</option>
                  <option *ngFor="let g of inHouse" [ngValue]="g.bookingId">Room {{ g.roomNumber }} — {{ g.guestName }}</option>
                </select>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="inHouse.length === 0">No guest is checked in right now.</div>
              </div>
              <div class="mb-2" *ngIf="settings?.tipsEnabled">
                <label class="form-label">Tip</label>
                <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                  <button class="btn btn-sm btn-outline-secondary tip-preset" *ngFor="let p of tipPresets" (click)="setTipPercent(p)">{{ p }}%</button>
                  <button class="btn btn-sm btn-outline-secondary tip-none" (click)="setTip(0)">No tip</button>
                  <input class="form-control tip-amount" type="number" min="0" step="0.01" style="width:120px;" [(ngModel)]="tip" (ngModelChange)="tipChanged()" name="tip" />
                </div>
              </div>
              <div class="flex gap-2 mb-2">
                <button class="btn btn-sm btn-outline-secondary" (click)="addPaymentRow()">+ Add payment</button>
                <span class="text-muted pay-summary" style="align-self:center;">
                  Entered: {{ paymentRowsTotal() | number: '1.2-2' }} / due {{ payable() | number: '1.2-2' }}<span *ngIf="tip > 0"> (bill {{ order.total | number: '1.2-2' }} + tip {{ tip | number: '1.2-2' }})</span>
                </span>
              </div>
              <div class="mb-2 text-muted dep-note" *ngIf="depositUsed() > 0">A reservation deposit of {{ depositUsed() | number: '1.2-2' }} has already been paid and is taken off the amount due.<span *ngIf="depositUnused() > 0"> The other {{ depositUnused() | number: '1.2-2' }} is given back to the guest.</span></div>
              <div class="mb-2 change-due" *ngIf="changeDue() > 0" style="font-weight:600;">Change to give back: {{ changeDue() | number: '1.2-2' }}</div>
              <button class="btn btn-primary" [disabled]="checkingOut" (click)="doCheckout()">{{ checkingOut ? 'Processing…' : 'Confirm checkout' }}</button>
            </div>

            <div class="card" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));" *ngIf="showMerge">
              <h4 style="font-size:1rem;" class="mb-2">Merge this order into another open table</h4>
              <div class="flex gap-2">
                <select class="form-select" [(ngModel)]="mergeTargetId" name="mergeTarget">
                  <option [ngValue]="null">Select a table…</option>
                  <option *ngFor="let o of otherOpenOrders" [ngValue]="o.id">{{ o.displayLabel }} ({{ o.total | number: '1.2-2' }})</option>
                </select>
                <button class="btn btn-primary" [disabled]="!mergeTargetId" (click)="doMerge()">Merge</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .menu-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
        gap: 8px;
      }
      .menu-item {
        border: 1px solid var(--border, #ddd);
        border-radius: 6px;
        padding: 0.6rem;
        text-align: center;
        background: var(--surface, #fff);
        cursor: pointer;
      }
      .menu-item.sold-out {
        opacity: 0.45;
        text-decoration: line-through;
        cursor: not-allowed;
      }
      .menu-item:hover {
        background: var(--hover, rgba(0, 0, 0, 0.05));
      }
      .totals {
        border-top: 1px solid var(--border, #ddd);
        padding-top: 0.5rem;
      }
    `,
  ],
})
export class TableOrderComponent implements OnInit {
  orderId!: number;
  order: RestaurantOrder | null = null;
  error: string | null = null;

  categories: MenuCategory[] = [];
  selectedCategory: MenuCategory | null = null;
  items: MenuItem[] = [];

  showCheckout = false;
  checkingOut = false;
  paymentMethods: PaymentMethodOption[] = [];
  paymentRows: PaymentRow[] = [];
  /** Hotel module: guests a "Charge to room" payment can go to (a room-service order already has its guest). */
  inHouse: InHouseGuest[] = [];
  roomBookingId: number | null = null;

  /**
   * Fourier: "Include split & merge bills." "Custom" is the original free-form payment rows;
   * "equal"/"byItem" are calculators that populate those same rows for you (see applyEqualSplit/
   * applyItemSplit) — checkout itself is unchanged either way, since the backend just wants a
   * list of {method, amount} payments that add up to the total (see
   * RestaurantOrderApiService.checkout).
   */
  splitMode: 'custom' | 'equal' | 'byItem' = 'custom';
  splitCount = 2;
  /** Line id -> 1-based share number, for "split by item." Unassigned lines are left out of the map. */
  lineShares: Record<number, number | null> = {};

  /** Pricing rules (tip choices); the order screen works without them if they cannot be read. */
  settings: RestaurantSettings | null = null;
  tip = 0;
  showDiscount = false;
  odKind: 'percent' | 'amount' = 'percent';
  odValue: number | null = null;
  odReason = '';
  discountLineId: number | null = null;
  ldPercent: number | null = null;
  ldReason = '';

  /** Item panel: choices, course and note for a line being added or changed. */
  panel: { mode: 'add' | 'edit'; item: MenuItem; line?: RestaurantOrder['lines'][number] } | null = null;
  pQty = 1;
  pCourse = 1;
  pNotes = '';
  pSel: Record<number, number[]> = {};
  pGroups: OptionGroup[] = [];
  panelError: string | null = null;
  panelBusy = false;
  /** Every menu item, so a line can find the choices of the item it came from. */
  allItems: MenuItem[] = [];
  allergenList: string[] = [];
  showAllergy = false;
  alSel: Record<string, boolean> = {};

  /** Other open bills (for the checks bar and for moving items), refreshed with every load. */
  siblings: RestaurantOrder[] = [];
  moveInfo: string | null = null;
  voidLineId: number | null = null;
  voidReason = '';
  voidPresets = ['Wrong item', 'Guest changed their mind', 'Kitchen error', 'Spoiled or dropped'];
  compLineId: number | null = null;
  compReason = '';
  compPresets = ['Birthday', 'VIP guest', 'Complaint', 'Staff meal'];
  showMove = false;
  mvDest: string = 'new';
  mvPick: Record<number, number> = {};
  openBills: RestaurantOrder[] = [];
  showMoveTable = false;
  mtTableId: number | null = null;
  freeTables: DiningTable[] = [];
  showCancel = false;
  cancelReason = '';
  pSeat: number | null = null;

  showMerge = false;
  otherOpenOrders: RestaurantOrder[] = [];
  mergeTargetId: number | null = null;

  constructor(
    private orderApi: RestaurantOrderApiService,
    private menuApi: MenuApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private kitchenApi: KitchenApiService,
    private hotelApi: HotelApiService,
    private tableApi: DiningTableApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Moving between checks of one table only changes the id in the address, so follow it.
    this.route.paramMap.subscribe((params) => {
      this.orderId = Number(params.get('id'));
      this.order = null;
      this.showCheckout = this.showMove = this.showMoveTable = this.showCancel = this.showDiscount = this.showMerge = this.showAllergy = false;
      this.panel = null;
      this.voidLineId = this.compLineId = this.discountLineId = null;
      this.moveInfo = null;
      this.error = null;
      this.loadOrder();
    });
    this.menuApi.listCategories().subscribe({
      next: (c) => {
        this.categories = c;
        if (c.length > 0) this.selectCategory(c[0]);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load menu categories'),
    });
    this.menuApi.listItems().subscribe({ next: (i) => (this.allItems = i), error: () => (this.allItems = []) });
    this.menuApi.allergens().subscribe({ next: (a) => (this.allergenList = a), error: () => (this.allergenList = []) });
    this.orderApi.getSettings().subscribe({ next: (s) => (this.settings = s), error: () => (this.settings = null) });
    this.paymentMethodApi.listActive().subscribe({
      next: (methods) => {
        this.paymentMethods = methods;
        if (methods.some((m) => m.roomCharge)) {
          this.hotelApi.inHouseGuests().subscribe({ next: (g) => (this.inHouse = g), error: () => (this.inHouse = []) });
        }
      },
      error: () => {},
    });
  }

  get tipPresets(): number[] {
    return (this.settings?.tipPresets ?? '')
      .split(',')
      .map((x) => Number(x.trim()))
      .filter((x) => x > 0);
  }

  loadOrder(): void {
    this.orderApi.getById(this.orderId).subscribe({
      next: (o) => {
        this.order = o;
        if (o.roomBookingId && !this.roomBookingId) this.roomBookingId = o.roomBookingId;
        this.loadSiblings();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load order'),
    });
  }

  selectCategory(c: MenuCategory): void {
    this.selectedCategory = c;
    this.menuApi.listItems(c.id).subscribe({
      next: (i) => (this.items = i),
      error: () => (this.items = []),
    });
  }

  pretty(csv: string | null | undefined): string {
    return (csv ?? '')
      .split(',')
      .filter((x) => x)
      .map((x) => x.charAt(0) + x.slice(1).toLowerCase())
      .join(', ');
  }

  get notePresets(): string[] {
    return (this.settings?.notePresets ?? '')
      .split(',')
      .map((x) => x.trim())
      .filter((x) => x);
  }

  groupsOf(item: MenuItem): OptionGroup[] {
    return (item.optionGroups ?? []).filter((g) => g.active && g.options.some((o) => o.active)).map((g) => ({ ...g, options: g.options.filter((o) => o.active) }));
  }

  optionText(line: RestaurantOrder['lines'][number]): string {
    return (line.options ?? []).map((o) => o.name).join(', ');
  }

  /** Course labels only show once the order uses more than the first course. */
  showCourses(): boolean {
    return (this.order?.lines ?? []).some((l) => (l.course ?? 1) > 1);
  }

  /** Courses (after the first) that have lines waiting to be fired. */
  heldCourses(): number[] {
    const set = new Set<number>();
    for (const l of this.order?.lines ?? []) if (l.held && l.course) set.add(l.course);
    return Array.from(set).sort((a, b) => a - b);
  }

  heldCount(): number {
    return (this.order?.lines ?? []).filter((l) => l.held).length;
  }

  fireCourse(course: number): void {
    this.error = null;
    this.orderApi.fireCourse(this.orderId, course).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to fire the course'),
    });
  }

  toggleAllergy(): void {
    this.showAllergy = !this.showAllergy;
    if (this.showAllergy) {
      const have = (this.order?.guestAllergens ?? '').split(',');
      this.alSel = {};
      for (const a of this.allergenList) this.alSel[a] = have.includes(a);
    }
  }

  saveAllergy(): void {
    this.error = null;
    const chosen = this.allergenList.filter((a) => this.alSel[a]).join(',');
    this.orderApi.setGuestAllergens(this.orderId, chosen).subscribe({
      next: (o) => {
        this.order = o;
        this.showAllergy = false;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save the allergies'),
    });
  }

  /** The dish has to be made again: back to the kitchen with a fresh clock. */
  sendBack(line: { id: number; description: string }): void {
    if (!confirm(`Send ${line.description} back to the kitchen to be made again?`)) return;
    this.error = null;
    this.kitchenApi.refire(this.orderId, line.id).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Could not send it back'),
    });
  }

  /** A tile with choices opens the item panel; a plain one goes straight onto the order. */
  onTile(item: MenuItem): void {
    if (this.groupsOf(item).length > 0) this.openAddPanel(item);
    else this.addLine(item);
  }

  openAddPanel(item: MenuItem): void {
    this.panel = { mode: 'add', item };
    this.pGroups = this.groupsOf(item);
    this.pSeat = null;
    this.pQty = 1;
    this.pCourse = item.defaultCourse ?? 1;
    this.pNotes = '';
    this.pSel = {};
    this.panelError = null;
  }

  openEditPanel(line: RestaurantOrder['lines'][number]): void {
    const item = this.allItems.find((i) => i.id === line.menuItemId) ?? ({ id: -1, product: { name: line.description }, optionGroups: [] } as unknown as MenuItem);
    this.panel = { mode: 'edit', item, line };
    this.pGroups = this.groupsOf(item);
    this.pSeat = line.seat ?? null;
    this.pCourse = line.course ?? 1;
    this.pNotes = line.notes ?? '';
    this.pSel = {};
    const picked = new Set((line.options ?? []).map((o) => o.optionId));
    for (const g of this.pGroups) this.pSel[g.id!] = g.options.filter((o) => picked.has(o.id!)).map((o) => o.id!);
    this.panelError = null;
  }

  closePanel(): void {
    this.panel = null;
  }

  panelGroups(): OptionGroup[] {
    return this.panel ? this.pGroups : [];
  }

  /** Choices and course can change only while the line is still waiting. */
  panelEditable(): boolean {
    return !this.panel || this.panel.mode === 'add' || this.panel.line?.kitchenStatus === 'PENDING';
  }

  isPicked(g: OptionGroup, optionId: number): boolean {
    return (this.pSel[g.id!] ?? []).includes(optionId);
  }

  togglePick(g: OptionGroup, optionId: number): void {
    const cur = this.pSel[g.id!] ?? [];
    if (cur.includes(optionId)) {
      this.pSel[g.id!] = cur.filter((x) => x !== optionId);
    } else if (g.maxSelect === 1) {
      this.pSel[g.id!] = [optionId];
    } else if (g.maxSelect > 0 && cur.length >= g.maxSelect) {
      this.panelError = `"${g.name}": choose at most ${g.maxSelect}`;
    } else {
      this.pSel[g.id!] = [...cur, optionId];
      this.panelError = null;
    }
    if (this.pSel[g.id!]?.length) this.panelError = null;
  }

  addNoteChip(text: string): void {
    this.pNotes = this.pNotes.trim() ? `${this.pNotes.trim()}, ${text}` : text;
  }

  savePanel(): void {
    if (!this.panel) return;
    const groups = this.panelGroups();
    const optionIds = groups.flatMap((g) => this.pSel[g.id!] ?? []);
    if (this.panelEditable()) {
      const missing = groups.find((g) => g.required && (this.pSel[g.id!] ?? []).length === 0);
      if (missing) {
        this.panelError = `Choose an option for "${missing.name}"`;
        return;
      }
    }
    this.panelError = null;
    this.panelBusy = true;
    const done = (o: RestaurantOrder) => {
      this.order = o;
      this.panel = null;
      this.panelBusy = false;
    };
    const fail = (err: { error?: { message?: string } }) => {
      this.panelError = err?.error?.message ?? 'Failed to save';
      this.panelBusy = false;
    };
    if (this.panel.mode === 'add') {
      this.orderApi
        .addLine(this.orderId, { menuItemId: this.panel.item.id, quantity: Math.max(1, Number(this.pQty) || 1), notes: this.pNotes.trim() || null, optionIds, course: this.pCourse, seat: this.pSeat ? Number(this.pSeat) : null })
        .subscribe({ next: done, error: fail });
    } else {
      const line = this.panel.line!;
      const payload: Record<string, unknown> = { notes: this.pNotes.trim(), seat: this.pSeat ? Number(this.pSeat) : 0 };
      if (this.panelEditable()) {
        payload['course'] = this.pCourse;
        if (groups.length > 0) payload['optionIds'] = optionIds;
      }
      this.orderApi.updateLine(this.orderId, line.id, payload).subscribe({ next: done, error: fail });
    }
  }

  addLine(item: MenuItem): void {
    this.error = null;
    this.orderApi.addLine(this.orderId, { menuItemId: item.id, quantity: 1 }).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to add item'),
    });
  }

  changeQty(line: RestaurantOrder['lines'][number], delta: number): void {
    const newQty = line.quantity + delta;
    this.error = null;
    if (newQty <= 0) {
      this.removeLine(line);
      return;
    }
    this.orderApi.updateLine(this.orderId, line.id, { quantity: newQty }).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update line'),
    });
  }

  loadSiblings(): void {
    const o = this.order;
    if (!o || !o.tableId || o.status !== 'OPEN') {
      this.siblings = [];
      return;
    }
    this.orderApi.listOpen().subscribe({
      next: (list) => (this.siblings = list.filter((x) => x.tableId === o.tableId).sort((a, b) => a.id - b.id)),
      error: () => (this.siblings = []),
    });
  }

  hasSent(): boolean {
    return (this.order?.lines ?? []).some((l) => l.kitchenStatus !== 'PENDING');
  }

  /** An item not yet sent comes straight off; one that went to the kitchen asks for a reason first. */
  startRemove(line: RestaurantOrder['lines'][number]): void {
    this.error = null;
    if (line.kitchenStatus === 'PENDING') {
      this.removeLine(line);
      return;
    }
    this.voidLineId = line.id;
    this.voidReason = '';
  }

  confirmVoid(line: RestaurantOrder['lines'][number]): void {
    if (!this.voidReason.trim()) {
      this.error = 'Say why the item is being voided';
      return;
    }
    this.error = null;
    this.orderApi.removeLine(this.orderId, line.id, this.voidReason.trim()).subscribe({
      next: (o) => {
        this.order = o;
        this.voidLineId = null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to void the item'),
    });
  }

  openComp(line: RestaurantOrder['lines'][number]): void {
    this.compLineId = line.id;
    this.compReason = '';
    this.error = null;
  }

  confirmComp(line: RestaurantOrder['lines'][number]): void {
    if (!this.compReason.trim()) {
      this.error = 'Say why the item is given free';
      return;
    }
    this.error = null;
    this.orderApi.compLine(this.orderId, line.id, this.compReason.trim()).subscribe({
      next: (o) => {
        this.order = o;
        this.compLineId = null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to comp the item'),
    });
  }

  uncomp(line: RestaurantOrder['lines'][number]): void {
    this.error = null;
    this.orderApi.uncompLine(this.orderId, line.id).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to charge the item again'),
    });
  }

  // ---- split / move ----

  openMove(): void {
    this.showMove = !this.showMove;
    this.moveInfo = null;
    if (!this.showMove) return;
    this.mvPick = {};
    this.mvDest = this.order?.orderType === 'DINE_IN' ? 'new' : '';
    this.orderApi.listOpen().subscribe({
      next: (l) => {
        this.openBills = l.filter((o) => o.id !== this.orderId);
        if (!this.mvDest && this.moveTargets().length > 0) this.mvDest = String(this.moveTargets()[0].id);
      },
      error: () => (this.openBills = []),
    });
  }

  moveTargets(): RestaurantOrder[] {
    return this.openBills.filter((o) => o.orderType === this.order?.orderType);
  }

  seatList(): number[] {
    const set = new Set<number>();
    for (const l of this.order?.lines ?? []) if (l.seat) set.add(l.seat);
    return Array.from(set).sort((a, b) => a - b);
  }

  seatAllPicked(seat: number): boolean {
    const lines = (this.order?.lines ?? []).filter((l) => l.seat === seat);
    return lines.length > 0 && lines.every((l) => this.mvPick[l.id] === l.quantity);
  }

  toggleSeat(seat: number): void {
    const lines = (this.order?.lines ?? []).filter((l) => l.seat === seat);
    const all = this.seatAllPicked(seat);
    for (const l of lines) {
      if (all) delete this.mvPick[l.id];
      else this.mvPick[l.id] = l.quantity;
    }
  }

  mvToggle(line: RestaurantOrder['lines'][number], checked: boolean): void {
    if (checked) this.mvPick[line.id] = line.quantity;
    else delete this.mvPick[line.id];
  }

  doMove(): void {
    const lines = Object.entries(this.mvPick).map(([id, q]) => ({ lineId: Number(id), quantity: Number(q) }));
    if (lines.length === 0) {
      this.error = 'Tick the items to move';
      return;
    }
    this.error = null;
    if (this.mvDest === 'new') {
      this.orderApi.split(this.orderId, { lines }).subscribe({
        next: (created) => {
          this.showMove = false;
          this.moveInfo = `${created.checkLabel ?? 'A new check'} created — open it from the checks bar to take payment.`;
          this.loadOrder();
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to split the bill'),
      });
    } else {
      const target = Number(this.mvDest);
      this.orderApi.transfer(this.orderId, { lines, targetOrderId: target }).subscribe({
        next: (receiving) => {
          this.showMove = false;
          this.moveInfo = `Moved to ${receiving.displayLabel}.`;
          this.orderApi.getById(this.orderId).subscribe({
            next: (o) => {
              if (o.status !== 'OPEN') this.router.navigate(['/admin/restaurant/orders', target]);
              else this.loadOrder();
            },
            error: () => this.loadOrder(),
          });
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to move the items'),
      });
    }
  }

  openMoveTable(): void {
    this.showMoveTable = !this.showMoveTable;
    if (!this.showMoveTable) return;
    this.mtTableId = null;
    this.tableApi.listAll().subscribe({
      next: (t) => (this.freeTables = t.filter((x) => x.active && x.status === 'AVAILABLE' && !x.openOrderId && x.id !== this.order?.tableId)),
      error: () => (this.freeTables = []),
    });
  }

  doMoveTable(): void {
    if (!this.mtTableId) return;
    this.error = null;
    this.orderApi.moveTable(this.orderId, this.mtTableId).subscribe({
      next: (o) => {
        this.order = o;
        this.showMoveTable = false;
        this.moveInfo = `Moved to table ${o.tableName}.`;
        this.loadSiblings();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to move the bill'),
    });
  }

  removeLine(line: RestaurantOrder['lines'][number]): void {
    this.error = null;
    this.orderApi.removeLine(this.orderId, line.id).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to remove line'),
    });
  }

  sendToKitchen(): void {
    this.error = null;
    this.orderApi.sendToKitchen(this.orderId).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to send to kitchen'),
    });
  }

  toggleCheckout(): void {
    this.showCheckout = !this.showCheckout;
    if (this.showCheckout) {
      this.splitMode = 'custom';
      this.lineShares = {};
      this.tip = 0;
      this.paymentRows = [];
      this.addPaymentRow();
    }
  }

  /** The part of a reservation deposit that goes toward this bill. */
  depositUsed(): number {
    const dep = this.order?.depositApplied ?? 0;
    return Math.min(dep, Math.max(this.order?.total ?? 0, 0));
  }

  /** What is left of the deposit once the bill is covered (it goes back to the guest). */
  depositUnused(): number {
    return Math.max(0, Math.round(((this.order?.depositApplied ?? 0) - this.depositUsed()) * 100) / 100);
  }

  /** What has to be collected: the bill plus any tip, less a deposit already paid. */
  payable(): number {
    return Math.max(0, Math.round(((this.order?.total ?? 0) + (Number(this.tip) || 0) - this.depositUsed()) * 100) / 100);
  }

  /** Cash handed over beyond the bill (only cash can be given back as change). */
  changeDue(): number {
    return Math.max(0, Math.round((this.paymentRowsTotal() - this.payable()) * 100) / 100);
  }

  setTip(amount: number): void {
    this.tip = Math.max(0, Math.round(amount * 100) / 100);
    this.tipChanged();
  }

  setTipPercent(pct: number): void {
    this.setTip(((this.order?.total ?? 0) * pct) / 100);
  }

  /** With a single payment line the amount simply follows bill + tip. */
  tipChanged(): void {
    if (this.paymentRows.length === 1) this.paymentRows[0].amount = this.payable();
  }

  addPaymentRow(): void {
    const due = this.order ? this.payable() - this.paymentRowsTotal() : 0;
    this.paymentRows.push({ method: this.paymentMethods[0]?.code ?? '', amount: Math.max(due, 0) });
  }

  removePaymentRow(i: number): void {
    this.paymentRows.splice(i, 1);
  }

  paymentRowsTotal(): number {
    return this.paymentRows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
  }

  setSplitMode(mode: 'custom' | 'equal' | 'byItem'): void {
    this.splitMode = mode;
    this.error = null;
  }

  shareRange(): number[] {
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    return Array.from({ length: n }, (_, i) => i + 1);
  }

  setLineShare(lineId: number, share: number | null): void {
    this.lineShares[lineId] = share;
  }

  /** Divides the total evenly across `splitCount` shares — the last share absorbs the rounding remainder so the rows always sum to exactly the total. */
  applyEqualSplit(): void {
    if (!this.order) return;
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    const total = this.payable();
    const base = Math.floor((total / n) * 100) / 100;
    const rows: PaymentRow[] = [];
    let allocated = 0;
    for (let i = 0; i < n; i++) {
      const amount = i === n - 1 ? Math.round((total - allocated) * 100) / 100 : base;
      rows.push({ method: this.paymentMethods[0]?.code ?? '', amount });
      allocated += amount;
    }
    this.paymentRows = rows;
    this.error = null;
  }

  /** Sums each share's assigned lines' lineGrandTotal (see the model's own javadoc — it's already the exact fair-share amount, tax included, whichever way that line's tax is set up). Refuses until every line has a share so nobody's items get left out of the bill. */
  applyItemSplit(): void {
    if (!this.order) return;
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    const shareAmounts: number[] = new Array(n).fill(0);
    let unassigned = 0;
    for (const line of this.order.lines) {
      const share = this.lineShares[line.id];
      if (!share || share < 1 || share > n) {
        unassigned++;
        continue;
      }
      shareAmounts[share - 1] += line.lineGrandTotal;
    }
    if (unassigned > 0) {
      this.error = `${unassigned} item${unassigned === 1 ? '' : 's'} still need a share assigned before applying.`;
      return;
    }
    // The lines add up to the bill before service charge and rounding (and any tip): spread those over the shares in proportion.
    const itemsSum = shareAmounts.reduce((a, b) => a + b, 0);
    const target = this.payable();
    const scale = itemsSum > 0 ? target / itemsSum : 1;
    const scaled = shareAmounts.map((a) => Math.round(a * scale * 100) / 100);
    const drift = Math.round((target - scaled.reduce((a, b) => a + b, 0)) * 100) / 100;
    scaled[scaled.length - 1] = Math.round((scaled[scaled.length - 1] + drift) * 100) / 100;
    this.paymentRows = scaled.map((amount) => ({ method: this.paymentMethods[0]?.code ?? '', amount }));
    this.error = null;
  }

  /** Hotel module: is any payment row on the "Charge to room" method? */
  roomRowSelected(): boolean {
    const roomCodes = this.paymentMethods.filter((m) => m.roomCharge).map((m) => m.code);
    return this.paymentRows.some((r) => roomCodes.includes(r.method));
  }

  doCheckout(): void {
    if (!this.order) return;
    if (this.paymentRows.length === 0) this.addPaymentRow();
    if (this.roomRowSelected() && !this.roomBookingId) {
      this.error = 'Choose the guest whose room this is charged to.';
      return;
    }
    this.error = null;
    this.checkingOut = true;
    const payments = this.paymentRows.filter((r) => Number(r.amount) > 0);
    this.orderApi.checkout(this.orderId, { payments, roomBookingId: this.roomRowSelected() ? this.roomBookingId : null, tip: Number(this.tip) > 0 ? Number(this.tip) : null }).subscribe({
      next: (o) => {
        this.order = o;
        this.checkingOut = false;
        this.showCheckout = false;
        this.router.navigate(['/admin/restaurant/tables/floor-plan']);
      },
      error: (err) => {
        this.checkingOut = false;
        this.error = err?.error?.message ?? 'Failed to check out';
      },
    });
  }

  // ---- Discounts and the service charge ----

  openLineDiscount(line: RestaurantOrder['lines'][number]): void {
    this.discountLineId = this.discountLineId === line.id ? null : line.id;
    this.ldPercent = line.discountPercent ? line.discountPercent : null;
    this.ldReason = line.discountReason ?? '';
    this.error = null;
  }

  applyLineDiscount(line: RestaurantOrder['lines'][number]): void {
    this.error = null;
    this.orderApi.updateLine(this.orderId, line.id, { discountPercent: Number(this.ldPercent) || 0, discountReason: this.ldReason }).subscribe({
      next: (o) => {
        this.order = o;
        this.discountLineId = null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not apply the discount'),
    });
  }

  toggleDiscount(): void {
    this.showDiscount = !this.showDiscount;
    if (this.showDiscount && this.order) {
      const pct = this.order.orderDiscountPercent ?? 0;
      const amt = this.order.orderDiscountAmount ?? 0;
      this.odKind = amt > 0 ? 'amount' : 'percent';
      this.odValue = amt > 0 ? amt : pct > 0 ? pct : null;
      this.odReason = this.order.discountReason ?? '';
    }
  }

  applyOrderDiscount(): void {
    const v = Number(this.odValue) || 0;
    this.error = null;
    this.orderApi
      .setDiscount(this.orderId, { percent: this.odKind === 'percent' ? v : 0, amount: this.odKind === 'amount' ? v : 0, reason: this.odReason })
      .subscribe({
        next: (o) => {
          this.order = o;
          this.showDiscount = false;
          this.refreshPaymentRows();
        },
        error: (err) => (this.error = err?.error?.message ?? 'Could not apply the discount'),
      });
  }

  removeOrderDiscount(): void {
    this.error = null;
    this.orderApi.setDiscount(this.orderId, { percent: 0, amount: 0 }).subscribe({
      next: (o) => {
        this.order = o;
        this.showDiscount = false;
        this.refreshPaymentRows();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not remove the discount'),
    });
  }

  toggleServiceCharge(): void {
    if (!this.order) return;
    this.error = null;
    this.orderApi.setServiceCharge(this.orderId, !this.order.serviceChargeWaived).subscribe({
      next: (o) => {
        this.order = o;
        this.refreshPaymentRows();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not change the service charge'),
    });
  }

  /** When the bill changes while the payment panel is open, a single payment line follows the new amount. */
  private refreshPaymentRows(): void {
    if (this.showCheckout && this.paymentRows.length === 1) this.paymentRows[0].amount = this.payable();
  }

  cancelOrder(): void {
    this.showCancel = !this.showCancel;
    this.cancelReason = '';
    this.error = null;
  }

  doCancel(): void {
    if (this.hasSent() && !this.cancelReason.trim()) {
      this.error = 'Say why the order is being cancelled';
      return;
    }
    this.error = null;
    this.orderApi.cancel(this.orderId, this.cancelReason.trim() || null).subscribe({
      next: () => this.router.navigate([this.order?.orderType === 'DINE_IN' ? '/admin/restaurant/tables/floor-plan' : '/admin/restaurant/orders']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel order'),
    });
  }

  onToggleMerge(): void {
    this.showMerge = !this.showMerge;
    if (this.showMerge) {
      this.orderApi.listOpen().subscribe({
        next: (orders) => (this.otherOpenOrders = orders.filter((o) => o.id !== this.orderId)),
        error: () => (this.otherOpenOrders = []),
      });
    }
  }

  doMerge(): void {
    if (!this.mergeTargetId) return;
    this.error = null;
    this.orderApi.merge(this.orderId, this.mergeTargetId).subscribe({
      next: () => this.router.navigate(['/admin/restaurant/orders', this.mergeTargetId]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to merge orders'),
    });
  }
}
