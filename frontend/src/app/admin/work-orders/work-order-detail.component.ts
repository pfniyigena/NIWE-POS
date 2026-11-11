import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { AssetApiService, FixedAsset } from '../../core/api/asset-api.service';
import { BomApiService } from '../../core/api/bom-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { ModuleApiService } from '../../core/api/module-api.service';
import { StaffOption, TimeApiService } from '../../core/api/time-api.service';
import { Account, Bom, Customer, Product } from '../../core/models';
import { QuickCustomerComponent } from '../../shared/quick-customer.component';
import { WorkOrderExtrasComponent } from './work-order-extras.component';
import {
  InvoicePreview, PackageSummary, WoLinePayload, WorkOrderApiService, WorkOrderDetail, WorkOrderPayload,
} from '../../core/api/work-order-api.service';

import { T } from '../../core/i18n/t';
/**
 * Work Orders > one job. A manager creates and edits it; the person it is assigned to works it (checklist, parts, start, complete).
 * The same screen serves /admin/work-orders/:id and /work-orders/:id (the second is open to every signed-in user, for their own jobs).
 */
@Component({
  selector: 'app-work-order-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, QuickCustomerComponent, WorkOrderExtrasComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ d ? d.number + ' — ' + d.title : ('New work order' | translate) }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="backLink()">{{ 'Back' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <!-- header form: new, or a manager editing -->
      <form class="card mb-3" style="padding:1rem; max-width:56rem;" *ngIf="editing" (ngSubmit)="save()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Type' | translate }}</label>
            <select class="form-select" name="ty" [(ngModel)]="f.type">
              <option value="CUSTOMER">{{ 'Customer job (billed)' | translate }}</option>
              <option value="INTERNAL">{{ 'Internal maintenance or repair' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Priority' | translate }}</label>
            <select class="form-select" name="pr" [(ngModel)]="f.priority">
              <option value="LOW">{{ 'Low' | translate }}</option><option value="NORMAL">{{ 'Normal' | translate }}</option><option value="HIGH">{{ 'High' | translate }}</option><option value="URGENT">{{ 'Urgent' | translate }}</option>
            </select></div>
          <div class="form-group" *ngIf="!d && packages.length"><label class="form-label">{{ 'Start from a service package' | translate }}</label>
            <select class="form-select" name="pk" [(ngModel)]="f.packageId" (ngModelChange)="packageChosen()">
              <option [ngValue]="null">{{ 'None' | translate }}</option>
              <option *ngFor="let p of packages" [ngValue]="p.id">{{ p.name }}{{ p.fixedPrice > 0 ? ' — ' + (p.fixedPrice | number: '1.0-2') : '' }}</option>
            </select></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Title' | translate }}</label><input class="form-control" name="ti" [(ngModel)]="f.title" required /></div>
        <div class="form-group"><label class="form-label">{{ 'Description' | translate }}</label><textarea class="form-control" rows="2" name="de" [(ngModel)]="f.description"></textarea></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="f.type === 'CUSTOMER'"><label class="form-label">{{ 'Customer' | translate }}</label>
            <select class="form-select" name="cu" [(ngModel)]="f.customerId">
              <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select>
            <app-quick-customer (created)="onCustomerCreated($event)"></app-quick-customer></div>
          <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="assets.length"><label class="form-label">{{ 'Asset (optional)' | translate }}</label>
            <select class="form-select" name="as" [(ngModel)]="f.assetId">
              <option [ngValue]="null">{{ 'None' | translate }}</option>
              <option *ngFor="let a of assets" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
            </select></div>
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'What is being worked on (optional)' | translate }}</label>
            <input class="form-control" name="it" [(ngModel)]="f.itemDescription" [placeholder]="'e.g. Laptop serial 123, vehicle RAB 123A' | translate" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Assigned to' | translate }}</label>
            <select class="form-select" name="au" [(ngModel)]="f.assignedToUserId">
              <option [ngValue]="null">{{ 'Nobody yet' | translate }}</option>
              <option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Scheduled' | translate }}</label><app-date-picker name="sc" [(ngModel)]="f.scheduledDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Due' | translate }}</label><app-date-picker name="du" [(ngModel)]="f.dueDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Hourly rate' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="lr" [(ngModel)]="f.labourRate" [placeholder]="'Default rate' | translate" /></div>
          <div class="form-group" *ngIf="f.type === 'CUSTOMER'"><label class="form-label">{{ 'Fixed price (optional)' | translate }}</label>
            <input class="form-control" type="number" min="0" step="0.01" name="fp" [(ngModel)]="f.fixedPrice" [placeholder]="'Bill hours and parts' | translate" /></div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ d ? ('Save' | translate) : ('Create work order' | translate) }}</button>
          <button class="btn btn-outline-secondary" type="button" *ngIf="d" (click)="editing = false">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <ng-container *ngIf="d">
        <!-- summary -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="!editing">
          <div class="flex justify-between" style="flex-wrap:wrap; gap:0.75rem;">
            <div>
              <span class="badge" [class.badge-success]="d.status === 'COMPLETED'" [class.badge-primary]="d.status === 'IN_PROGRESS'"
                    [class.badge-warning]="d.status === 'ON_HOLD'" [class.badge-secondary]="d.status === 'OPEN' || d.status === 'CANCELLED'">{{ d.status.replace('_', ' ') }}</span>
              <span class="badge badge-secondary" style="margin-left:0.3rem;">{{ d.type === 'CUSTOMER' ? ('Customer job' | translate) : ('Internal' | translate) }}</span>
              <span class="badge badge-danger" style="margin-left:0.3rem;" *ngIf="d.priority === 'URGENT'">{{ 'Urgent' | translate }}</span>
              <span class="badge badge-warning" style="margin-left:0.3rem;" *ngIf="d.priority === 'HIGH'">{{ 'High priority' | translate }}</span>
              <span class="badge badge-danger" style="margin-left:0.3rem;" *ngIf="d.overdue">{{ 'Overdue' | translate }}</span>
              <span class="badge" style="margin-left:0.3rem;" id="wo-head-approval" *ngIf="d.type === 'CUSTOMER' && d.approvalRequired"
                    [class.badge-success]="d.approvalStatus === 'APPROVED'" [class.badge-warning]="d.approvalStatus === 'NONE' || d.approvalStatus === 'REQUESTED'"
                    [class.badge-danger]="d.approvalStatus === 'REJECTED'">{{ approvalLabel() }}</span>
              <span class="badge badge-secondary" style="margin-left:0.3rem;" *ngIf="d.scheduleId">{{ 'Planned maintenance' | translate }}</span>
              <p style="margin:0.6rem 0 0;" *ngIf="d.description">{{ d.description }}</p>
              <p class="text-muted" style="margin:0.3rem 0 0;" *ngIf="d.itemDescription">{{ 'Working on: {p0}' | translate: { p0: (d.itemDescription) } }}</p>
            </div>
            <div style="font-size:0.9rem; min-width:16rem;">
              <div *ngIf="d.customerName">{{ 'Customer:' | translate }} <strong>{{ d.customerName }}</strong></div>
              <div *ngIf="d.assetName">{{ 'Asset:' | translate }} <a [routerLink]="['/admin/assets', d.assetId]" *ngIf="d.canManage">{{ d.assetName }}</a><span *ngIf="!d.canManage">{{ d.assetName }}</span></div>
              <div>{{ 'Assigned to:' | translate }} <strong>{{ d.assignedToName || ('nobody yet' | translate) }}</strong></div>
              <div>{{ 'Scheduled: {p0} · Due: {p1}' | translate: { p0: (d.scheduledDate || '—'), p1: (d.dueDate || '—') } }}</div>
              <div *ngIf="d.packageName">{{ 'Package: {p0}' | translate: { p0: (d.packageName) } }}</div>
              <div *ngIf="d.fixedPrice > 0">{{ 'Fixed price:' | translate }} <strong>{{ d.fixedPrice | number: '1.0-2' }}</strong></div>
              <div *ngIf="d.labourRate">{{ 'Hourly rate: {p0}' | translate: { p0: (d.labourRate | number: '1.0-2') } }}</div>
              <div class="text-muted" *ngIf="d.completedAt">{{ 'Completed {p0}' | translate: { p0: (d.completedAt | date: 'medium') } }}</div>
              <div *ngIf="d.completionNote" class="text-muted">{{ 'Note: {p0}' | translate: { p0: (d.completionNote) } }}</div>
            </div>
          </div>
        </div>

        <!-- actions -->
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:center;">
          <button class="btn btn-primary" *ngIf="d.canWork && (d.status === 'OPEN' || d.status === 'ON_HOLD')" [disabled]="busy || !approvedToStart()" [title]="approvedToStart() ? '' : 'The customer has to approve the estimate first'" (click)="act('start')">{{ d.status === 'ON_HOLD' ? ('Resume' | translate) : ('Start work' | translate) }}</button>
          <button class="btn btn-outline-secondary" *ngIf="d.canWork && (d.status === 'OPEN' || d.status === 'IN_PROGRESS')" [disabled]="busy" (click)="act('hold')">{{ 'Put on hold' | translate }}</button>
          <button class="btn btn-success" *ngIf="d.canWork && active()" [disabled]="busy" (click)="showComplete = !showComplete">{{ 'Complete…' | translate }}</button>
          <a class="btn btn-outline-primary" routerLink="/time" *ngIf="modules.on('TIME_TRACKING') && active()">{{ 'Log time' | translate }}</a>
          <button class="btn btn-outline-secondary" *ngIf="d.canManage && !editing && active() && !d.invoiceId" (click)="startEdit()">{{ 'Edit details' | translate }}</button>
          <button class="btn btn-outline-secondary" *ngIf="d.canManage && (d.status === 'COMPLETED' || d.status === 'CANCELLED') && !d.invoiceId" [disabled]="busy" (click)="act('reopen')">{{ 'Reopen' | translate }}</button>
          <button class="btn btn-outline-danger" *ngIf="d.canManage && active()" [disabled]="busy" (click)="act('cancel')">{{ 'Cancel job' | translate }}</button>
          <button class="btn btn-outline-danger" *ngIf="d.canManage && (d.status === 'OPEN' || d.status === 'CANCELLED') && !d.invoiceId" (click)="remove()">{{ 'Delete' | translate }}</button>
        </div>
        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="showComplete">
          <strong class="mb-2">{{ 'Complete this job' | translate }}</strong>
          <p class="text-muted" style="font-size:0.85rem;">{{ 'Parts listed below are taken out of stock and their cost is posted. Make sure they are all recorded first.' | translate }}</p>
          <div class="form-group"><label class="form-label">{{ 'Note (optional)' | translate }}</label><textarea class="form-control" rows="2" name="cn" [(ngModel)]="completionNote"></textarea></div>
          <label *ngIf="d.canManage && openSteps() > 0" style="display:flex; gap:0.4rem; align-items:center;" class="mb-2">
            <input type="checkbox" name="fc" [(ngModel)]="force" /> {{ 'Complete even though {p0} checklist step(s) are not done' | translate: { p0: (openSteps()) } }}</label>
          <div class="flex gap-2"><button class="btn btn-success" [disabled]="busy" (click)="complete()">{{ 'Complete job' | translate }}</button>
            <button class="btn btn-outline-secondary" (click)="showComplete = false">{{ 'Not yet' | translate }}</button></div>
        </div>

        <app-work-order-extras [d]="d" (changed)="apply($event)"></app-work-order-extras>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <div style="flex:2; min-width:22rem;">
            <!-- checklist -->
            <div class="card mb-3" style="padding:1rem;">
              <div class="flex justify-between align-center mb-2">
                <strong>{{ 'Checklist' | translate }}</strong>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canManage && active() && !editSteps" (click)="startSteps()">{{ 'Edit steps' | translate }}</button>
              </div>
              <ng-container *ngIf="!editSteps">
                <label *ngFor="let c of d.checklist" style="display:flex; gap:0.5rem; align-items:flex-start; padding:0.2rem 0;">
                  <input type="checkbox" [checked]="c.done" [disabled]="!d.canWork || !active() || busy" (change)="toggle(c.id)" />
                  <span [style.text-decoration]="c.done ? 'line-through' : ''" [style.opacity]="c.done ? 0.6 : 1">{{ c.text }}
                    <span class="text-muted" style="font-size:0.75rem;" *ngIf="c.done && c.doneByName"> — {{ c.doneByName }}</span></span>
                </label>
                <p class="text-muted" *ngIf="!d.checklist.length">{{ 'No checklist steps.' | translate }}</p>
              </ng-container>
              <ng-container *ngIf="editSteps">
                <textarea class="form-control" rows="6" name="st" [(ngModel)]="stepsText" placeholder="One step per line"></textarea>
                <div class="flex gap-2 mt-2"><button class="btn btn-primary btn-sm" [disabled]="busy" (click)="saveSteps()">{{ 'Save steps' | translate }}</button>
                  <button class="btn btn-outline-secondary btn-sm" (click)="editSteps = false">{{ 'Cancel' | translate }}</button></div>
              </ng-container>
            </div>

            <!-- parts -->
            <div class="card mb-3" style="padding:1rem;">
              <strong class="mb-2">{{ 'Parts and materials' | translate }}</strong>
              <table class="table-sm" *ngIf="lineEdits.length">
                <thead><tr><th>{{ 'Product' | translate }}</th><th style="width:6rem;">{{ 'Qty' | translate }}</th><th style="width:8rem;">{{ 'Price' | translate }}</th><th *ngIf="d.fixedPrice > 0">{{ 'In fixed price' | translate }}</th><th></th></tr></thead>
                <tbody>
                  <tr *ngFor="let l of lineEdits; let i = index">
                    <td>{{ l.name }}</td>
                    <td><input class="form-control" type="number" step="any" min="0.0001" [name]="'q' + i" [(ngModel)]="l.quantity" [disabled]="!canEditParts()" /></td>
                    <td><input class="form-control" type="number" min="0" step="0.01" [name]="'p' + i" [(ngModel)]="l.unitPrice" [disabled]="!canEditParts() || d.type === 'INTERNAL'" /></td>
                    <td *ngIf="d.fixedPrice > 0"><input type="checkbox" [name]="'i' + i" [(ngModel)]="l.included" [disabled]="!canEditParts()" /></td>
                    <td><button class="btn btn-sm btn-outline-danger" *ngIf="canEditParts()" (click)="lineEdits.splice(i, 1)">{{ 'Remove' | translate }}</button></td>
                  </tr>
                </tbody>
              </table>
              <p class="text-muted" *ngIf="!lineEdits.length">{{ 'No parts used yet.' | translate }}</p>
              <ng-container *ngIf="canEditParts()">
                <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
                  <input class="form-control" style="width:14rem;" [placeholder]="'Search a product' | translate" name="ps" [(ngModel)]="productQuery" (keyup.enter)="searchProducts()" />
                  <button class="btn btn-outline-secondary btn-sm" type="button" (click)="searchProducts()">{{ 'Search' | translate }}</button>
                  <select class="form-select" style="width:auto; max-width:18rem;" name="pp" [(ngModel)]="pickProductId" *ngIf="products.length">
                    <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                    <option *ngFor="let p of products" [ngValue]="p.id">{{ '{p0} ({p1} in stock)' | translate: { p0: (p.name), p1: (p.stockQuantity) } }}</option>
                  </select>
                  <button class="btn btn-outline-primary btn-sm" type="button" *ngIf="pickProductId" (click)="addPart()">{{ 'Add' | translate }}</button>
                </div>
                <div class="mt-2"><button class="btn btn-primary btn-sm" [disabled]="busy" (click)="saveParts()">{{ 'Save parts' | translate }}</button></div>
              </ng-container>
              <p class="text-muted mt-2" style="font-size:0.8rem;" *ngIf="!d.partsPosted && lineEdits.length && active()">{{ 'Stock moves and the cost is posted when the job is completed.' | translate }}</p>
              <p class="text-muted mt-2" style="font-size:0.8rem;" *ngIf="d.partsPosted">{{ 'Stock was taken and the cost of {p0} was posted.' | translate: { p0: (d.money.partsCost | number: '1.0-2') } }}</p>
            </div>

            <!-- external costs -->
            <div class="card mb-3" style="padding:1rem;" *ngIf="d.canManage">
              <strong class="mb-2">{{ 'Outside costs' | translate }}</strong>
              <table class="table-sm" *ngIf="d.costs.length">
                <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Supplier' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th><th class="text-end" *ngIf="d.type === 'CUSTOMER'">{{ 'Charged' | translate }}</th><th></th></tr></thead>
                <tbody>
                  <tr *ngFor="let c of d.costs">
                    <td>{{ c.costDate }}</td><td>{{ c.description }}<span class="badge badge-secondary" *ngIf="c.expenseId" style="margin-left:0.3rem;">{{ 'Expense' | translate }}</span></td>
                    <td>{{ c.supplierName || '—' }}</td><td class="text-end">{{ c.amount | number: '1.0-2' }}</td>
                    <td class="text-end" *ngIf="d.type === 'CUSTOMER'">{{ c.chargeAmount | number: '1.0-2' }}</td>
                    <td><button class="btn btn-sm btn-outline-danger" *ngIf="!d.invoiceId && !c.expenseId" (click)="removeCost(c.id)">{{ 'Remove' | translate }}</button></td>
                  </tr>
                </tbody>
              </table>
              <p class="text-muted" *ngIf="!d.costs.length">{{ 'None recorded.' | translate }}</p>
              <form *ngIf="d.status !== 'CANCELLED' && !d.invoiceId" (ngSubmit)="addCost()" class="mt-2">
                <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                  <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="cd" [(ngModel)]="cost.costDate"></app-date-picker></div>
                  <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'What' | translate }}</label><input class="form-control" name="cdsc" [(ngModel)]="cost.description" /></div>
                  <div class="form-group"><label class="form-label">{{ 'Supplier' | translate }}</label><input class="form-control" name="csp" [(ngModel)]="cost.supplierName" /></div>
                  <div class="form-group"><label class="form-label">{{ 'Cost' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="cam" style="width:7rem;" [(ngModel)]="cost.amount" /></div>
                  <div class="form-group" *ngIf="d.type === 'CUSTOMER'"><label class="form-label">{{ 'Charge customer' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="cch" style="width:7rem;" [(ngModel)]="cost.chargeAmount" /></div>
                  <div class="form-group" *ngIf="expenseAccounts.length"><label class="form-label">{{ 'Record as expense in' | translate }}</label>
                    <select class="form-select" name="cea" [(ngModel)]="cost.expenseAccountId">
                      <option [ngValue]="null">{{ 'Don\\'t record' | translate }}</option>
                      <option *ngFor="let a of expenseAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
                    </select></div>
                  <div class="form-group"><button class="btn btn-outline-primary" type="submit" [disabled]="busy">{{ 'Add cost' | translate }}</button></div>
                </div>
              </form>
            </div>
          </div>

          <div style="flex:1; min-width:18rem;">
            <!-- hours and money -->
            <div class="card mb-3" style="padding:1rem;">
              <strong class="mb-2">{{ 'Hours' | translate }}</strong>
              <div>{{ '{p0} h logged' | translate: { p0: (d.hours | number: '1.0-2') } }}<span *ngIf="d.type === 'CUSTOMER' && d.fixedPrice <= 0"> {{ '· {p0} h approved and billable' | translate: { p0: (d.billableHours | number: '1.0-2') } }}</span></div>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="d.type === 'INTERNAL'">{{ 'Internal hours are recorded for information; salaries are paid through payroll.' | translate }}</div>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="d.type === 'CUSTOMER' && d.fixedPrice > 0">{{ 'The price is fixed, so hours are not billed.' | translate }}</div>
            </div>
            <div class="card mb-3" style="padding:1rem;">
              <strong class="mb-2">{{ 'Cost and price' | translate }}</strong>
              <table class="table-sm">
                <tbody>
                  <tr><td>{{ 'Parts cost' | translate }}</td><td class="text-end">{{ d.money.partsCost | number: '1.0-2' }}</td></tr>
                  <tr><td>{{ 'Outside costs' | translate }}</td><td class="text-end">{{ d.money.externalCost | number: '1.0-2' }}</td></tr>
                  <tr><td><strong>{{ 'Total cost' | translate }}</strong></td><td class="text-end"><strong>{{ d.money.totalCost | number: '1.0-2' }}</strong></td></tr>
                  <ng-container *ngIf="d.type === 'CUSTOMER'">
                    <tr><td>{{ d.invoiceId ? ('Invoiced' | translate) : ('Expected to invoice' | translate) }}</td>
                      <td class="text-end">{{ (d.money.invoiceTotal != null ? d.money.invoiceTotal : d.money.expectedRevenue) | number: '1.0-2' }}</td></tr>
                  </ng-container>
                </tbody>
              </table>
            </div>

            <!-- manufacturing -->
            <div class="card mb-3" style="padding:1rem;" *ngIf="modules.on('MANUFACTURING') && (d.canManage || d.manufacturingOrderId)">
              <strong class="mb-2">{{ 'Manufacturing' | translate }}</strong>
              <ng-container *ngIf="d.manufacturingOrderId">
                <div>{{ 'Order' | translate }} <a [routerLink]="['/admin/manufacturing/orders', d.manufacturingOrderId]" *ngIf="d.canManage">{{ d.manufacturingOrderNumber }}</a>
                  <span *ngIf="!d.canManage">{{ d.manufacturingOrderNumber }}</span> — {{ d.manufacturingOrderStatus || 'unknown' }}</div>
                <p class="text-muted" style="font-size:0.8rem;">{{ 'The job cannot be completed until this order is produced.' | translate }}</p>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canManage && active()" (click)="unlinkMo()">{{ 'Unlink' | translate }}</button>
              </ng-container>
              <ng-container *ngIf="!d.manufacturingOrderId && d.canManage && active()">
                <p class="text-muted" style="font-size:0.85rem;">{{ 'Make something for this job: create a manufacturing order linked to it.' | translate }}</p>
                <div class="form-group"><label class="form-label">{{ 'Bill of materials' | translate }}</label>
                  <select class="form-select" name="mb" [(ngModel)]="mo.bomId">
                    <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                    <option *ngFor="let b of boms" [ngValue]="b.id">{{ b.code }} — {{ b.productName }}</option>
                  </select></div>
                <div class="form-group"><label class="form-label">{{ 'Quantity' | translate }}</label><input class="form-control" type="number" step="any" min="0.0001" name="mq" [(ngModel)]="mo.quantity" /></div>
                <button class="btn btn-outline-primary btn-sm" [disabled]="busy || !mo.bomId" (click)="linkMo()">{{ 'Create manufacturing order' | translate }}</button>
              </ng-container>
            </div>

            <!-- billing -->
            <div class="card mb-3" style="padding:1rem;" *ngIf="d.type === 'CUSTOMER' && d.status === 'COMPLETED'">
              <strong class="mb-2">{{ 'Invoice' | translate }}</strong>
              <ng-container *ngIf="d.invoiceId">
                <div>{{ 'On invoice' | translate }} <a [routerLink]="['/admin/invoices', d.invoiceId]">{{ d.invoiceNumber }}</a></div>
                <button class="btn btn-sm btn-outline-secondary mt-2" *ngIf="d.canManage" [disabled]="busy" (click)="release()">{{ 'Release (bill again)' | translate }}</button>
              </ng-container>
              <ng-container *ngIf="!d.invoiceId">
                <button class="btn btn-outline-primary btn-sm" *ngIf="!preview" [disabled]="busy" (click)="loadPreview()">{{ 'Preview invoice' | translate }}</button>
                <ng-container *ngIf="preview">
                  <table class="table-sm">
                    <tbody>
                      <tr *ngFor="let l of preview.lines"><td>{{ l.description }}<span *ngIf="l.quantity > 1"> × {{ l.quantity }}</span></td><td class="text-end">{{ l.amount | number: '1.0-2' }}</td></tr>
                      <tr><td><strong>{{ 'Total (before tax)' | translate }}</strong></td><td class="text-end"><strong>{{ preview.total | number: '1.0-2' }}</strong></td></tr>
                    </tbody>
                  </table>
                  <div class="alert alert-warning" *ngIf="preview.warning">{{ preview.warning }}</div>
                  <div class="form-group"><label class="form-label">{{ 'Due date (optional)' | translate }}</label><app-date-picker name="idd" [(ngModel)]="dueDate"></app-date-picker></div>
                  <div class="flex gap-2">
                    <button class="btn btn-primary btn-sm" [disabled]="busy || !preview.lines.length" (click)="createInvoice()">{{ 'Create draft invoice' | translate }}</button>
                    <button class="btn btn-outline-secondary btn-sm" (click)="preview = null">{{ 'Close' | translate }}</button>
                  </div>
                </ng-container>
              </ng-container>
            </div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class WorkOrderDetailComponent implements OnInit {
  id: number | null = null;
  d: WorkOrderDetail | null = null;
  editing = false;
  busy = false;
  error: string | null = null;
  notice: string | null = null;
  adminRoute = false;

  customers: Customer[] = [];
  staff: StaffOption[] = [];
  assets: FixedAsset[] = [];
  boms: Bom[] = [];
  expenseAccounts: Account[] = [];
  packages: PackageSummary[] = [];
  products: Product[] = [];

  f: WorkOrderPayload = { type: 'CUSTOMER', priority: 'NORMAL', title: '', description: '', itemDescription: '', customerId: null, assetId: null, packageId: null, fixedPrice: null,
    labourRate: null, assignedToUserId: null, requestedDate: null, scheduledDate: null, dueDate: null };

  editSteps = false;
  stepsText = '';
  lineEdits: { productId: number; name: string; quantity: number; unitPrice: number | null; included: boolean }[] = [];
  productQuery = '';
  pickProductId: number | null = null;
  cost: { costDate: string | null; description: string; supplierName: string; amount: number | null; chargeAmount: number | null; expenseAccountId: number | null } =
    { costDate: null, description: '', supplierName: '', amount: null, chargeAmount: null, expenseAccountId: null };
  showComplete = false;
  completionNote = '';
  force = false;
  mo: { bomId: number | null; quantity: number } = { bomId: null, quantity: 1 };
  preview: InvoicePreview | null = null;
  dueDate: string | null = null;

  onCustomerCreated(c: Customer): void {
    this.customers = [...this.customers, c].sort((a, b) => a.name.localeCompare(b.name));
    this.f.customerId = c.id;
  }

  constructor(
    private api: WorkOrderApiService, private route: ActivatedRoute, private router: Router, public modules: ModuleApiService,
    private customerApi: CustomerApiService, private catalogApi: CatalogApiService, private assetApi: AssetApiService, private bomApi: BomApiService,
    private accountApi: AccountApiService, private timeApi: TimeApiService,
  ) {}

  ngOnInit(): void {
    this.adminRoute = this.router.url.startsWith('/admin');
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      this.id = Number(idParam);
      this.api.get(this.id).subscribe({ next: (d) => this.show(d), error: (e) => (this.error = e?.error?.message ?? T('Could not load the work order')) });
    } else {
      this.editing = true;
      this.loadPickers(true);
      this.api.packages(true).subscribe({ next: (p) => (this.packages = p), error: () => {} });
    }
  }

  private pickersLoaded = false;
  private loadPickers(force = false): void {
    if (this.pickersLoaded && !force) return;
    this.pickersLoaded = true;
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !x.walkIn)), error: () => {} });
    this.timeApi.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    if (this.modules.on('ASSETS')) this.assetApi.list().subscribe({ next: (a) => (this.assets = a), error: () => {} });
  }

  private show(d: WorkOrderDetail): void {
    this.d = d;
    this.id = d.id;
    this.lineEdits = d.lines.map((l) => ({ productId: l.productId, name: l.productName, quantity: l.quantity, unitPrice: l.unitPrice, included: l.included }));
    if (d.canManage) {
      if (this.modules.on('MANUFACTURING') && !this.boms.length) this.bomApi.listAll().subscribe({ next: (b) => (this.boms = b.filter((x) => x.active)), error: () => {} });
      if (this.modules.on('EXPENSES') && !this.expenseAccounts.length) this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.expenseAccounts = a), error: () => {} });
    }
  }

  apply(d: WorkOrderDetail, notice?: string): void {
    this.busy = false;
    this.error = null;
    this.notice = notice ?? null;
    this.preview = null;
    this.show(d);
  }

  private fail(e: any, fallback: string): void {
    this.busy = false;
    this.notice = null;
    this.error = e?.error?.message ?? fallback;
  }

  backLink(): string {
    return this.adminRoute && this.d?.canManage !== false ? '/admin/work-orders' : '/work-orders/mine';
  }

  active(): boolean {
    return !!this.d && (this.d.status === 'OPEN' || this.d.status === 'IN_PROGRESS' || this.d.status === 'ON_HOLD');
  }

  /** A customer job that needs the customer's approval cannot be started until it has been given. */
  approvedToStart(): boolean {
    const d = this.d;
    return !d || d.type !== 'CUSTOMER' || !d.approvalRequired || d.approvalStatus === 'APPROVED';
  }

  approvalLabel(): string {
    const s = this.d?.approvalStatus;
    return s === 'APPROVED' ? 'Estimate approved' : s === 'REQUESTED' ? 'Waiting for the customer' : s === 'REJECTED' ? 'Estimate declined' : 'Estimate to be sent';
  }

  openSteps(): number {
    return this.d ? this.d.checklist.filter((c) => !c.done).length : 0;
  }

  canEditParts(): boolean {
    return !!this.d && this.d.canWork && this.active() && !this.d.invoiceId;
  }

  startEdit(): void {
    if (!this.d) return;
    this.loadPickers();
    const d = this.d;
    this.f = {
      type: d.type, priority: d.priority, title: d.title, description: d.description, itemDescription: d.itemDescription, customerId: d.customerId, assetId: d.assetId,
      packageId: d.packageId, fixedPrice: d.fixedPrice || null, labourRate: d.labourRate, assignedToUserId: d.assignedToUserId, requestedDate: d.requestedDate,
      scheduledDate: d.scheduledDate, dueDate: d.dueDate, branchId: d.branchId,
    };
    this.editing = true;
  }

  packageChosen(): void {
    const p = this.packages.find((x) => x.id === this.f.packageId);
    if (p && p.fixedPrice > 0 && !this.f.fixedPrice) this.f.fixedPrice = p.fixedPrice;
    if (p && !this.f.title.trim()) this.f.title = p.name;
  }

  save(): void {
    this.error = null;
    this.busy = true;
    const call = this.id ? this.api.update(this.id, this.f) : this.api.create(this.f);
    call.subscribe({
      next: (d) => {
        if (!this.id) {
          this.busy = false;
          this.router.navigate(['/admin/work-orders', d.id]);
          return;
        }
        this.editing = false;
        this.apply(d, 'Saved.');
      },
      error: (e) => this.fail(e, T('Could not save')),
    });
  }

  remove(): void {
    if (!this.id || !confirm(T('Delete this work order?'))) return;
    this.api.delete(this.id).subscribe({ next: () => this.router.navigate(['/admin/work-orders']), error: (e) => this.fail(e, T('Could not delete')) });
  }

  act(action: 'start' | 'hold' | 'reopen' | 'cancel'): void {
    if (!this.id) return;
    if (action === 'cancel' && !confirm(T('Cancel this job?'))) return;
    if (action === 'reopen' && !confirm(T('Reopen this job? Parts are returned to stock and the cost posting is reversed.'))) return;
    this.busy = true;
    const calls = { start: () => this.api.start(this.id!), hold: () => this.api.hold(this.id!), reopen: () => this.api.reopen(this.id!), cancel: () => this.api.cancel(this.id!) };
    calls[action]().subscribe({ next: (d) => this.apply(d), error: (e) => this.fail(e, T('Could not change the status')) });
  }

  complete(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.complete(this.id, this.completionNote.trim() || null, this.force).subscribe({
      next: (d) => { this.showComplete = false; this.apply(d, 'Job completed.'); },
      error: (e) => this.fail(e, T('Could not complete the job')),
    });
  }

  toggle(itemId: number): void {
    if (!this.id) return;
    this.busy = true;
    this.api.toggle(this.id, itemId).subscribe({ next: (d) => this.apply(d), error: (e) => this.fail(e, T('Could not update the step')) });
  }

  startSteps(): void {
    this.stepsText = (this.d?.checklist ?? []).map((c) => c.text).join('\n');
    this.editSteps = true;
  }

  saveSteps(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.saveChecklist(this.id, this.stepsText.split('\n').map((s) => s.trim()).filter((s) => s)).subscribe({
      next: (d) => { this.editSteps = false; this.apply(d, 'Checklist saved.'); },
      error: (e) => this.fail(e, T('Could not save the checklist')),
    });
  }

  searchProducts(): void {
    this.catalogApi.listAllProducts(this.productQuery.trim() || null, 0, 30).subscribe({ next: (r) => (this.products = r.content.filter((p) => p.active !== false)), error: () => {} });
  }

  addPart(): void {
    const p = this.products.find((x) => x.id === this.pickProductId);
    if (!p) return;
    const existing = this.lineEdits.find((l) => l.productId === p.id);
    if (existing) existing.quantity += 1;
    else this.lineEdits.push({ productId: p.id, name: p.name, quantity: 1, unitPrice: p.unitPrice, included: false });
    this.pickProductId = null;
  }

  saveParts(): void {
    if (!this.id) return;
    this.busy = true;
    const lines: WoLinePayload[] = this.lineEdits.map((l) => ({ productId: l.productId, quantity: Number(l.quantity) || 1, unitPrice: l.unitPrice, included: l.included }));
    this.api.saveLines(this.id, lines).subscribe({ next: (d) => this.apply(d, 'Parts saved.'), error: (e) => this.fail(e, T('Could not save the parts')) });
  }

  addCost(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.addCost(this.id, {
      costDate: this.cost.costDate, description: this.cost.description, supplierName: this.cost.supplierName || null, amount: Number(this.cost.amount) || 0,
      chargeAmount: this.cost.chargeAmount, expenseAccountId: this.cost.expenseAccountId,
    }).subscribe({
      next: (d) => {
        this.cost = { costDate: null, description: '', supplierName: '', amount: null, chargeAmount: null, expenseAccountId: null };
        this.apply(d, 'Cost added.');
      },
      error: (e) => this.fail(e, T('Could not add the cost')),
    });
  }

  removeCost(costId: number): void {
    if (!this.id || !confirm(T('Remove this cost?'))) return;
    this.busy = true;
    this.api.deleteCost(this.id, costId).subscribe({ next: (d) => this.apply(d), error: (e) => this.fail(e, T('Could not remove the cost')) });
  }

  linkMo(): void {
    if (!this.id || !this.mo.bomId) return;
    this.busy = true;
    this.api.linkManufacturing(this.id, this.mo.bomId, Number(this.mo.quantity) || 1, this.d?.scheduledDate ?? null).subscribe({
      next: (d) => this.apply(d, 'Manufacturing order created.'),
      error: (e) => this.fail(e, T('Could not create the manufacturing order')),
    });
  }

  unlinkMo(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.unlinkManufacturing(this.id).subscribe({ next: (d) => this.apply(d), error: (e) => this.fail(e, T('Could not unlink')) });
  }

  loadPreview(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.invoicePreview(this.id).subscribe({
      next: (p) => { this.busy = false; this.preview = p; },
      error: (e) => this.fail(e, T('Could not build the invoice preview')),
    });
  }

  createInvoice(): void {
    if (!this.id) return;
    this.busy = true;
    this.api.invoice(this.id, this.dueDate, null).subscribe({
      next: (r) => {
        this.api.get(this.id!).subscribe({ next: (d) => this.apply(d, 'Draft invoice ' + r.invoiceNumber + ' created.'), error: () => (this.busy = false) });
      },
      error: (e) => this.fail(e, T('Could not create the invoice')),
    });
  }

  release(): void {
    if (!this.id || !confirm(T('Release this job from its invoice so it can be billed again? Void or delete the invoice first if it is issued.'))) return;
    this.busy = true;
    this.api.releaseInvoice(this.id).subscribe({ next: (d) => this.apply(d, 'Released.'), error: (e) => this.fail(e, T('Could not release the invoice')) });
  }
}
