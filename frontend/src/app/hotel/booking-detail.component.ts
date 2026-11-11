import { TranslateService } from '@ngx-translate/core';
import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Bill, CancelPreview, CheckoutPreview, CompanyOption, Folio, FolioCharge, HotelApiService, InHouseGuest, PaymentLine, QuoteNight, RatePlan, Room } from '../core/api/hotel-api.service';
import { showPdf } from '../core/open-pdf';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';
import { addDays, errMsg, statusBadge, statusLabel, today } from './hotel-util';

import { T } from '../core/i18n/t';
type Panel = 'checkin' | 'edit' | 'cancel' | 'noshow' | 'move' | 'extend' | 'charge' | 'payment' | 'checkout' | 'movecharge' | null;

/** A booking and its bill (folio): arrival, in-house charges, payments, check-out. */
@Component({
  selector: 'app-booking-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <ng-container *ngIf="folio as f">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <h3>
            {{ f.booking.bookingNumber }}
            <span class="badge" [ngClass]="badge(f.booking.status)" style="font-size:0.75rem;">{{ label(f.booking.status) }}</span>
            <span class="badge badge-warning vip-badge" style="font-size:0.75rem;" *ngIf="f.booking.guestVip">{{ 'VIP' | translate }}</span>
            <span class="badge badge-danger black-badge" style="font-size:0.75rem;" *ngIf="f.booking.guestBlacklisted">{{ 'Blacklisted' | translate }}</span>
            <span class="badge badge-info online-badge" style="font-size:0.75rem;" *ngIf="f.booking.online">{{ 'Website' | translate }} <span *ngIf="f.booking.confirmationCode">· {{ f.booking.confirmationCode }}</span></span>
            <span class="badge badge-warning hold-badge" style="font-size:0.75rem;" *ngIf="f.booking.status === 'BOOKED' && f.booking.holdExpiresAt" [title]="'Released if no deposit is recorded in time' | translate">{{ 'Deposit {p0} due by {p1}' | translate: { p0: (f.booking.depositDue | number: '1.0-2'), p1: (f.booking.holdExpiresAt | date: 'd MMM, HH:mm') } }}</span>
            <a class="badge badge-info group-link" style="font-size:0.75rem;" *ngIf="f.booking.groupId" [routerLink]="['/admin/hotel/groups', f.booking.groupId]">{{ 'Group {p0} · {p1}' | translate: { p0: (f.booking.groupCode), p1: (f.booking.groupName) } }}</a>
          </h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/hotel/bookings', f.booking.id, 'invoice']">{{ 'Print invoice' | translate }}</a>
            <button class="btn btn-outline-secondary bill-pdf" type="button" (click)="pdf(0)">{{ 'Bill (PDF)' | translate }}</button>
            <button class="btn btn-outline-secondary reg-card" type="button" *ngIf="f.booking.status !== 'CANCELLED' && f.booking.status !== 'NO_SHOW'" (click)="registrationCard()">{{ 'Registration card' | translate }}</button>
            <button class="btn btn-outline-secondary confirm-letter" type="button" *ngIf="f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'" (click)="confirmation()">{{ 'Confirmation' | translate }}</button>
            <button class="btn btn-outline-secondary mail-confirm" type="button" *ngIf="canManage && (f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN')" (click)="mailConfirmation()" [title]="'E-mail the confirmation letter to the guest' | translate">{{ 'E-mail confirmation' | translate }}</button>
            <ng-container *ngIf="canManage">
              <button class="btn btn-success" *ngIf="f.booking.status === 'BOOKED' && f.booking.checkInDate <= todayStr" (click)="open('checkin')">{{ 'Check in' | translate }}</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'" (click)="open('edit')">{{ 'Edit' | translate }}</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('move')">{{ 'Move room' | translate }}</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('extend')">{{ 'Change departure' | translate }}</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('charge')">{{ '+ Charge' | translate }}</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'" (click)="open('payment')">{{ f.booking.status === 'BOOKED' ? ('+ Deposit' | translate) : ('+ Payment' | translate) }}</button>
              <button class="btn btn-outline-primary pay-fee" *ngIf="(f.booking.status === 'CANCELLED' || f.booking.status === 'NO_SHOW') && f.balance > 0" (click)="open('payment')">{{ 'Pay fee owed' | translate }}</button>
              <button class="btn btn-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('checkout')">{{ 'Check out' | translate }}</button>
              <button class="btn btn-outline-danger" *ngIf="f.booking.status === 'BOOKED' && f.booking.checkInDate <= todayStr" (click)="open('noshow')">{{ 'No-show' | translate }}</button>
              <button class="btn btn-outline-danger" *ngIf="f.booking.status === 'BOOKED'" (click)="open('cancel')">{{ 'Cancel booking' | translate }}</button>
            </ng-container>
          </div>
        </div>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'GUEST' | translate }}</div><strong><a class="guest-link" [routerLink]="['/admin/hotel/guests', f.booking.guestId]">{{ f.booking.guestName }}</a></strong><div style="font-size:0.85rem;">{{ f.booking.guestPhone }}</div><div class="guest-prefs text-muted" style="font-size:0.8rem;" *ngIf="f.booking.guestPreferences">★ {{ f.booking.guestPreferences }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'ROOM' | translate }}</div><strong>{{ f.booking.roomNumber || ('Not assigned' | translate) }}</strong><div style="font-size:0.85rem;">{{ f.booking.roomTypeName }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'STAY' | translate }}</div><strong>{{ f.booking.checkInDate }} → {{ f.booking.checkOutDate }}</strong><div style="font-size:0.85rem;">{{ '{p0} night{p1}' | translate: { p0: (f.booking.nights), p1: (f.booking.nights === 1 ? '' : 's') } }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'GUESTS' | translate }}</div><strong>{{ '{p0} adult{p1}' | translate: { p0: (f.booking.adults), p1: (f.booking.adults === 1 ? '' : 's') } }}<span *ngIf="f.booking.children"> {{ '+ {p0} child{p1}' | translate: { p0: (f.booking.children), p1: (f.booking.children === 1 ? '' : 'ren') } }}</span></strong></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'RATE / NIGHT' | translate }}</div><strong>{{ f.booking.nightlyTotal | number: '1.2-2' }}</strong><div style="font-size:0.85rem;">{{ 'before tax' | translate }}<span *ngIf="f.booking.rateMode === 'AUTO' && !sameNights()"> {{ '· average' | translate }}</span></div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'PRICED' | translate }}</div><strong class="rate-mode">{{ f.booking.rateMode === 'AUTO' ? ('Hotel rates' | translate) : ('Agreed rate' | translate) }}</strong><div style="font-size:0.85rem;" *ngIf="f.booking.ratePlanName">{{ f.booking.ratePlanName }}</div></div>
          </div>
          <div style="margin-top:0.5rem;" *ngIf="nights.length > 1 && !sameNights()">
            <button class="btn btn-sm btn-outline-secondary nights-toggle" type="button" (click)="showNights = !showNights">{{ showNights ? ('Hide price by night' | translate) : ('Price by night' | translate) }}</button>
            <table class="table-sm nights-table no-stack" style="margin-top:0.4rem; max-width:480px;" *ngIf="showNights">
              <thead><tr><th>{{ 'Night' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th>{{ 'Rate' | translate }}</th></tr></thead>
              <tbody><tr *ngFor="let n of nights"><td>{{ n.date }}</td><td class="text-end">{{ n.net | number: '1.2-2' }}</td><td class="text-muted">{{ n.ruleName || ('Standard' | translate) }}</td></tr></tbody>
            </table>
          </div>
          <div class="text-muted" style="font-size:0.85rem; margin-top:0.5rem; white-space:pre-line;" *ngIf="f.booking.notes">{{ f.booking.notes }}</div>
          <div class="text-muted" style="font-size:0.85rem; margin-top:0.5rem;" *ngIf="f.booking.cancelReason">{{ 'Reason: {p0}' | translate: { p0: (f.booking.cancelReason) } }}</div>
        </div>

        <!-- Bills of the stay -->
        <div class="card bills-card" style="padding:1rem; margin-bottom:1rem;" *ngIf="f.bills.length > 1 || (canManage && (f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'))">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4 style="font-size:1rem;">{{ 'Bills' | translate }} <span class="text-muted" style="font-size:0.85rem;" *ngIf="f.bills.length === 1">{{ '— one bill for the whole stay' | translate }}</span></h4>
            <button class="btn btn-sm btn-outline-primary split-bill" type="button" *ngIf="canManage && (f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN') && f.bills.length < 6" (click)="openBillForm()">{{ '+ Split bill / bill a company' | translate }}</button>
          </div>
          <form *ngIf="billForm" class="bill-form" style="margin-bottom:0.75rem;" (ngSubmit)="addBill()">
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Name (optional)' | translate }}</label><input class="form-control" name="bl" [(ngModel)]="billForm.label" [placeholder]="'Extras, Company, Spouse...' | translate" /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Billed to' | translate }}</label>
                <select class="form-control bill-company" name="bc" [(ngModel)]="billForm.companyId"><option [ngValue]="null">{{ 'The guest' | translate }}</option><option *ngFor="let c of companies" [ngValue]="c.id">{{ c.name }}</option></select></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Add bill' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="billForm = null">{{ 'Cancel' | translate }}</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem; margin-top:0.3rem;" *ngIf="companies.length === 0">{{ 'No companies are set up for credit yet — open Customers and enable credit to bill a company.' | translate }}</div>
          </form>
          <table class="table-sm bills-table" *ngIf="f.bills.length > 1 || f.bills[0].companyId || f.bills[0].toGroup || f.booking.groupId">
            <thead><tr><th>{{ 'Bill' | translate }}</th><th>{{ 'Billed to' | translate }}</th><th class="text-end">{{ 'Charges' | translate }}</th><th class="text-end">{{ 'Received' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let b of f.bills">
                <td><strong>{{ b.label || ('Bill {p0}' | translate: { p0: b.billNo }) }}</strong><span class="badge badge-info" style="margin-left:0.3rem;" *ngIf="b.roomNights && f.bills.length > 1">{{ 'Room nights' | translate }}</span></td>
                <td>
                  <ng-container *ngIf="b.companyName; else guestOwes">{{ b.companyName }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="b.companyBillNumber">{{ 'Invoice {p0} · {p1}' | translate: { p0: (b.companyBillNumber), p1: (b.companyBillStatus === 'PAID' ? 'paid' : 'on account') } }}</div></ng-container>
                  <ng-template #guestOwes><ng-container *ngIf="b.toGroup; else guestPays">{{ 'The group' | translate }}<div class="text-muted" style="font-size:0.8rem;">{{ '{p0} · on the group\\'s account at check-out' | translate: { p0: (f.booking.groupCode) } }}</div></ng-container><ng-template #guestPays>{{ f.booking.guestName }}</ng-template></ng-template>
                </td>
                <td class="text-end">{{ b.charges | number: '1.2-2' }}</td>
                <td class="text-end">{{ b.paid | number: '1.2-2' }}</td>
                <td class="text-end">{{ b.companyName || b.toGroup ? ('On account' | translate) : (b.balance | number: '1.2-2') }}</td>
                <td class="flex gap-2" style="flex-wrap:wrap;">
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="pdf(b.billNo)">{{ 'PDF' | translate }}</button>
                  <ng-container *ngIf="canManage && (f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN')">
                    <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="!b.roomNights && f.bills.length > 1" (click)="roomHere(b)">{{ 'Room nights here' | translate }}</button>
                    <button class="btn btn-sm btn-outline-primary bill-to-company" type="button" *ngIf="!b.companyName && companies.length && b.paid === 0" (click)="billTo(b)">{{ 'Bill a company…' | translate }}</button>
                    <button class="btn btn-sm btn-outline-primary bill-to-group" type="button" *ngIf="f.booking.groupId && !b.companyName && !b.toGroup && b.paid === 0" (click)="billGroup(b)">{{ 'Bill the group' | translate }}</button>
                    <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="b.companyName || b.toGroup" (click)="backToGuest(b)">{{ 'Back to guest' | translate }}</button>
                    <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="b.billNo > 1 && b.charges === 0 && b.paid === 0 && !b.roomNights" (click)="removeBill(b)">{{ 'Remove' | translate }}</button>
                  </ng-container>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="flex gap-2 align-center" *ngIf="pickCompanyFor as pb" style="margin-top:0.5rem; flex-wrap:wrap;">
            <select class="form-control pick-company" style="max-width:260px;" name="pc" [(ngModel)]="pickCompanyId"><option [ngValue]="null">{{ 'Choose a company…' | translate }}</option><option *ngFor="let c of companies" [ngValue]="c.id">{{ c.name }}</option></select>
            <button class="btn btn-primary btn-sm" type="button" (click)="confirmBillTo()" [disabled]="busy || !pickCompanyId">{{ 'Send this bill to the company' | translate }}</button>
            <button class="btn btn-link btn-sm" type="button" (click)="pickCompanyFor = null">{{ 'Cancel' | translate }}</button>
          </div>
        </div>

        <!-- Action panels -->
        <div class="card" style="padding:1rem; margin-bottom:1rem;" *ngIf="panel">
          <ng-container [ngSwitch]="panel">
            <form *ngSwitchCase="'checkin'" (ngSubmit)="doCheckIn()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Check in' | translate }}</h4>
              <div class="form-group"><label class="form-label">{{ 'Room' | translate }}</label>
                <select class="form-control" style="max-width:340px;" name="room" [(ngModel)]="roomId" required>
                  <option *ngFor="let r of freeRooms" [ngValue]="r.id">{{ r.number }} · {{ r.roomTypeName }} · {{ r.housekeepingStatus | titlecase }}</option>
                </select></div>
              <label class="mb-2" style="display:block;"><input type="checkbox" name="anyway" [(ngModel)]="allowNotReady" /> {{ 'Check in even if the room is not marked clean' | translate }}</label>
              <button class="btn btn-success" type="submit" [disabled]="busy">{{ 'Check in' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'edit'" (ngSubmit)="doEdit()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Edit booking' | translate }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group" *ngIf="f.booking.status === 'BOOKED'"><label class="form-label">{{ 'Arrival' | translate }}</label><input class="form-control" type="date" name="ci" [(ngModel)]="edit.checkInDate" /></div>
                <div class="form-group"><label class="form-label">{{ 'Departure' | translate }}</label><input class="form-control" type="date" name="co" [(ngModel)]="edit.checkOutDate" /></div>
                <div class="form-group"><label class="form-label">{{ 'Adults' | translate }}</label><input class="form-control" style="max-width:90px;" type="number" min="1" name="ad" [(ngModel)]="edit.adults" /></div>
                <div class="form-group"><label class="form-label">{{ 'Children' | translate }}</label><input class="form-control" style="max-width:90px;" type="number" min="0" name="ch" [(ngModel)]="edit.children" /></div>
              </div>
              <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
                <div class="form-group" *ngIf="f.booking.status === 'BOOKED' && plans.length"><label class="form-label">{{ 'Rate plan' | translate }}</label>
                  <select class="form-control edit-plan" style="max-width:240px;" name="eplan" [(ngModel)]="edit.ratePlanId">
                    <option [ngValue]="null">{{ 'Standard rate' | translate }}</option>
                    <option *ngFor="let p of plans" [ngValue]="p.id">{{ p.name }}</option>
                  </select></div>
                <div class="form-group"><label class="form-label">{{ 'Price' | translate }}</label>
                  <select class="form-control edit-mode" style="max-width:220px;" name="emode" [(ngModel)]="edit.mode">
                    <option value="AUTO">{{ 'Hotel rates (by night)' | translate }}</option>
                    <option value="FIXED">{{ 'Agreed nightly rate' | translate }}</option>
                  </select></div>
                <div class="form-group" *ngIf="edit.mode === 'FIXED'"><label class="form-label">{{ 'Nightly rate' | translate }}</label>
                  <input class="form-control edit-rate" style="max-width:140px;" type="number" min="0" step="0.01" name="rt" [(ngModel)]="edit.nightlyRate" /></div>
              </div>
              <div class="text-muted" style="font-size:0.8rem; margin-bottom:0.5rem;" *ngIf="f.booking.status === 'CHECKED_IN'">{{ 'A new rate applies to nights not yet charged.' | translate }}</div>
              <div class="text-muted" style="font-size:0.8rem; margin-bottom:0.5rem;" *ngIf="f.booking.status === 'BOOKED' && edit.mode === 'AUTO'">{{ 'Changing the dates, room type or plan re-prices the stay at today\\'s hotel rates.' | translate }}</div>
              <div class="form-group"><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control" name="nt" [(ngModel)]="edit.notes" /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'move'" (ngSubmit)="doMove()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Move to another room' | translate }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">{{ 'New room' | translate }}</label>
                  <select class="form-control" name="mr" [(ngModel)]="roomId" required>
                    <option *ngFor="let r of freeRooms" [ngValue]="r.id">{{ r.number }} · {{ r.roomTypeName }} · {{ r.housekeepingStatus | titlecase }}</option>
                  </select></div>
                <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="why" [(ngModel)]="reason" /></div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Move guest' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'extend'" (ngSubmit)="doExtend()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Change departure date' | translate }}</h4>
              <div class="form-group"><input class="form-control" style="max-width:200px;" type="date" name="co2" [(ngModel)]="newDeparture" required /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'charge'" (ngSubmit)="doCharge()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Add a charge' | translate }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">{{ 'Description' | translate }}</label><input class="form-control" name="cd" [(ngModel)]="chargeDesc" [placeholder]="'Laundry, minibar, late check-out...' | translate" required /></div>
                <div class="form-group"><label class="form-label">{{ 'Amount (before tax; negative = discount)' | translate }}</label><input class="form-control" style="max-width:170px;" type="number" step="0.01" name="ca" [(ngModel)]="chargeAmount" required /></div>
              </div>
              <div class="form-group" *ngIf="f.bills.length > 1"><label class="form-label">{{ 'Put it on' | translate }}</label>
                <select class="form-control charge-bill" style="max-width:260px;" name="cb" [(ngModel)]="chargeBill"><option *ngFor="let b of f.bills" [ngValue]="b.billNo">{{ b.label || ('Bill {p0}' | translate: { p0: b.billNo }) }}{{ b.companyName ? ' (' + b.companyName + ')' : '' }}</option></select></div>
              <label class="mb-2" style="display:block;"><input type="checkbox" name="tx" [(ngModel)]="chargeTaxable" /> {{ 'Add tax' | translate }}</label>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Add charge' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'payment'" (ngSubmit)="doPayment()">
              <h4 style="font-size:1rem;" class="mb-2">{{ f.booking.status === 'BOOKED' ? 'Take a deposit' : f.booking.status === 'CHECKED_IN' ? ('Take a payment' | translate) : ('Pay the fee owed' | translate) }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" style="max-width:160px;" type="number" min="0.01" step="0.01" name="pa" [(ngModel)]="payAmount" required /></div>
                <div class="form-group"><label class="form-label">{{ 'Method' | translate }}</label>
                  <select class="form-control" name="pm" [(ngModel)]="payMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                <div class="form-group"><label class="form-label">{{ 'Reference' | translate }}</label><input class="form-control" name="pr" [(ngModel)]="payRef" /></div>
                <div class="form-group" *ngIf="guestBills.length > 1"><label class="form-label">{{ 'For bill' | translate }}</label>
                  <select class="form-control pay-bill" name="pb" [(ngModel)]="payBill"><option *ngFor="let b of guestBills" [ngValue]="b.billNo">{{ b.label || ('Bill {p0}' | translate: { p0: b.billNo }) }}</option></select></div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Record' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'checkout'" (ngSubmit)="doCheckout()">
              <h4 style="font-size:1rem;" class="mb-2">Check out &amp; settle the bill</h4>
              <p style="font-size:0.9rem;" *ngIf="!coPreview">{{ 'Working out the bill…' | translate }}</p>
              <ng-container *ngIf="coPreview as pv">
                <div class="alert alert-warning co-fees" *ngIf="pv.fees.length">
                  <div *ngFor="let fee of pv.fees"><strong>{{ fee.description }}</strong>: {{ fee.gross | number: '1.2-2' }}</div>
                  <label style="display:block; margin-top:0.3rem;"><input type="checkbox" name="wf" [(ngModel)]="waiveFees" (ngModelChange)="coWaive()" /> {{ 'Waive {p0}' | translate: { p0: (pv.fees.length > 1 ? 'these fees' : 'this fee') } }}</label>
                </div>
                <p style="font-size:0.9rem;">
                  {{ 'Guest\\'s bills' | translate }} <strong>{{ pv.guestDue | number: '1.2-2' }}</strong> {{ '· deposits and payments' | translate }}
                  <strong>{{ f.deposits + f.paymentsTotal - f.refunds | number: '1.2-2' }}</strong> ·
                  <ng-container *ngIf="coToPay > 0">{{ 'still to pay' | translate }} <strong class="co-topay">{{ coToPay | number: '1.2-2' }}</strong></ng-container>
                  <ng-container *ngIf="coToPay <= 0">{{ 'nothing more to pay' | translate }}<span *ngIf="pv.guestDue - (f.deposits + f.paymentsTotal - f.refunds) < 0"> {{ '— {p0} will be refunded' | translate: { p0: ((f.deposits + f.paymentsTotal - f.refunds) - pv.guestDue | number: '1.2-2') } }}</span></ng-container>.
                  <span *ngIf="pv.companyDue > 0" class="co-company"> {{ '{p0} goes on the company\\'s account.' | translate: { p0: (pv.companyDue | number: '1.2-2') } }}</span>
                  {{ 'Any remaining nights up to today are added when you confirm.' | translate }}
                </p>
              </ng-container>
              <div class="flex gap-2 mb-2" *ngFor="let l of payLines; let i = index" style="align-items:flex-end; flex-wrap:wrap;">
                <div class="form-group" style="margin:0;"><label class="form-label" *ngIf="i === 0">{{ 'Pay with' | translate }}</label>
                  <select class="form-control" [name]="'m' + i" [(ngModel)]="l.method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                <div class="form-group" style="margin:0;"><label class="form-label" *ngIf="i === 0">{{ 'Amount' | translate }}</label>
                  <input class="form-control" style="max-width:150px;" type="number" min="0" step="0.01" [name]="'a' + i" [(ngModel)]="l.amount" /></div>
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="payLines.splice(i, 1)" *ngIf="payLines.length > 1">{{ 'Remove' | translate }}</button>
              </div>
              <button class="btn btn-sm btn-outline-secondary mb-2" type="button" (click)="addPayLine()">{{ '+ Split payment' | translate }}</button>
              <div class="form-group" *ngIf="coPreview && (f.deposits + f.paymentsTotal - f.refunds) > coPreview.guestDue"><label class="form-label">{{ 'Refund by' | translate }}</label>
                <select class="form-control" style="max-width:220px;" name="rm" [(ngModel)]="refundMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Check out' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'movecharge'" (ngSubmit)="doMoveCharge()">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Move "{p0}" to another guest' | translate: { p0: (moveLine?.description) } }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">{{ 'Guest (in-house)' | translate }}</label>
                  <select class="form-control move-target" name="mt" [(ngModel)]="moveTarget" required>
                    <option *ngFor="let g of moveTargets" [ngValue]="g.bookingId">{{ 'Room {p0} · {p1} ({p2})' | translate: { p0: (g.roomNumber), p1: (g.guestName), p2: (g.bookingNumber) } }}</option>
                  </select></div>
                <div class="form-group"><label class="form-label">{{ 'Reason (optional)' | translate }}</label><input class="form-control" name="mw" [(ngModel)]="reason" /></div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="busy || !moveTarget">{{ 'Move charge' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>

            <form *ngSwitchCase="'cancel'" (ngSubmit)="doCancel(false)">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Cancel booking' | translate }}</h4>
              <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="cr" [(ngModel)]="reason" /></div>
              <ng-container *ngTemplateOutlet="feeNotice"></ng-container>
              <ng-container *ngTemplateOutlet="depositChoice"></ng-container>
              <button class="btn btn-danger" type="submit" [disabled]="busy">{{ 'Cancel booking' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>
            <form *ngSwitchCase="'noshow'" (ngSubmit)="doCancel(true)">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'Mark as no-show' | translate }}</h4>
              <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="nr" [(ngModel)]="reason" /></div>
              <ng-container *ngTemplateOutlet="feeNotice"></ng-container>
              <ng-container *ngTemplateOutlet="depositChoice"></ng-container>
              <button class="btn btn-danger" type="submit" [disabled]="busy">{{ 'Mark no-show' | translate }}</button>
              <button class="btn btn-link" type="button" (click)="panel = null">{{ 'Close' | translate }}</button>
            </form>
          </ng-container>
        </div>

        <ng-template #feeNotice>
          <div class="alert alert-warning cancel-fee" *ngIf="cancelPrev && cancelPrev.fee as fee">
            <strong>{{ fee.description }}: {{ fee.gross | number: '1.2-2' }}</strong>
            <div style="font-size:0.85rem;">
              <span *ngIf="cancelPrev.feeFromDeposit > 0">{{ '{p0} is taken from the deposit.' | translate: { p0: (cancelPrev.feeFromDeposit | number: '1.2-2') } }} </span>
              <span *ngIf="cancelPrev.stillOwed > 0">{{ '{p0} stays owed by the guest.' | translate: { p0: (cancelPrev.stillOwed | number: '1.2-2') } }} </span>
            </div>
            <label style="display:block; margin-top:0.3rem;"><input type="checkbox" name="wv" [(ngModel)]="waiveFee" /> {{ 'Waive the fee' | translate }}</label>
          </div>
        </ng-template>

        <ng-template #depositChoice>
          <div *ngIf="folio && folio.deposits > 0" class="form-group">
            <label class="form-label">{{ 'Deposit of {p0}' | translate: { p0: (folio.deposits | number: '1.2-2') } }}</label>
            <label class="flex gap-2"><input type="radio" name="da" value="REFUND" [(ngModel)]="depositAction" /> {{ 'Give it back' | translate }}</label>
            <label class="flex gap-2"><input type="radio" name="da" value="FORFEIT" [(ngModel)]="depositAction" /> {{ 'Keep it (becomes income)' | translate }}</label>
            <select class="form-control" style="max-width:220px; margin-top:0.4rem;" name="drm" [(ngModel)]="refundMethod" *ngIf="depositAction === 'REFUND'">
              <option *ngFor="let m of methods" [value]="m.code">{{ 'Refund by {p0}' | translate: { p0: (m.name) } }}</option>
            </select>
          </div>
        </ng-template>

        <!-- Folio -->
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Charges' | translate }}</h4>
        <table class="mb-3">
          <thead><tr><th>{{ 'Description' | translate }}</th><th>{{ 'Type' | translate }}</th><th *ngIf="f.bills.length > 1">{{ 'Bill' | translate }}</th><th class="text-end">{{ 'Net' | translate }}</th><th class="text-end">{{ 'Tax' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of f.charges" [style.opacity]="c.voided ? 0.5 : 1">
              <td [style.text-decoration]="c.voided ? 'line-through' : 'none'">{{ c.description }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="c.voided">{{ 'Voided{p0}' | translate: { p0: (c.voidReason ? ': ' + c.voidReason : '') } }}</div></td>
              <td>{{ typeLabel(c.chargeType) }}</td>
              <td *ngIf="f.bills.length > 1">
                <select class="form-control assign-bill" style="min-width:110px; padding:0.15rem 0.3rem;" *ngIf="canManage && !c.voided && (f.booking.status === 'CHECKED_IN' || f.booking.status === 'BOOKED'); else billName" [ngModel]="c.billNo" (ngModelChange)="assign(c, $event)" [ngModelOptions]="{standalone: true}">
                  <option *ngFor="let b of f.bills" [ngValue]="b.billNo">{{ b.label || ('Bill {p0}' | translate: { p0: b.billNo }) }}</option>
                </select>
                <ng-template #billName>{{ billLabel(c.billNo) }}</ng-template>
              </td>
              <td class="text-end">{{ c.net | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.tax | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.amount | number: '1.2-2' }}</td>
              <td>
                <button class="btn btn-sm btn-outline-secondary move-charge" *ngIf="canManage && !c.voided && c.chargeType !== 'ROOM' && c.chargeType !== 'FEE' && f.booking.status === 'CHECKED_IN'" (click)="openMoveCharge(c)">{{ 'Move' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" *ngIf="canManage && !c.voided && (c.chargeType === 'ROOM' || c.chargeType === 'EXTRA') && (f.booking.status === 'CHECKED_IN' || f.booking.status === 'BOOKED')" (click)="voidCharge(c.id)">{{ 'Void' | translate }}</button>
              </td>
            </tr>
            <tr *ngIf="f.charges.length === 0"><td [attr.colspan]="f.bills.length > 1 ? 7 : 6" class="text-muted">{{ f.booking.status === 'BOOKED' ? ('Room charges start at check-in. Quote for the stay: {p0}' | translate: { p0: (f.booking.chargesTotal | number: '1.2-2') }) : ('No charges.' | translate) }}</td></tr>
          </tbody>
          <tfoot *ngIf="f.charges.length">
            <tr><th [attr.colspan]="f.bills.length > 1 ? 3 : 2">{{ 'Total' | translate }}</th><th class="text-end"></th><th class="text-end">{{ f.taxTotal | number: '1.2-2' }}</th><th class="text-end">{{ f.chargesTotal | number: '1.2-2' }}</th><th></th></tr>
          </tfoot>
        </table>

        <h4 style="font-size:1rem;" class="mb-2">Deposits &amp; payments</h4>
        <table class="mb-3">
          <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Method' | translate }}</th><th *ngIf="f.bills.length > 1">{{ 'Bill' | translate }}</th><th>{{ 'Reference' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let p of f.payments">
              <td>{{ p.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
              <td>{{ p.kind | titlecase }}</td>
              <td>{{ p.method }}</td>
              <td *ngIf="f.bills.length > 1">{{ billLabel(p.billNo) }}</td>
              <td>{{ p.reference || p.note }}</td>
              <td class="text-end">{{ p.kind === 'REFUND' ? '−' : '' }}{{ p.amount | number: '1.2-2' }}</td>
              <td><button class="btn btn-sm btn-outline-secondary receipt-pdf" type="button" (click)="receipt(p.id)">{{ 'Receipt' | translate }}</button></td>
            </tr>
            <tr *ngIf="f.payments.length === 0"><td [attr.colspan]="f.bills.length > 1 ? 7 : 6" class="text-muted">{{ 'Nothing received yet.' | translate }}</td></tr>
          </tbody>
        </table>
        <p style="font-size:1.05rem;">{{ 'Balance:' | translate }} <strong [style.color]="f.balance > 0 ? 'var(--danger-text)' : 'inherit'">{{ f.balance | number: '1.2-2' }}</strong>
          <span class="text-muted" style="font-size:0.85rem;" *ngIf="f.booking.status === 'BOOKED'"> {{ '(quote {p0} less deposits {p1} = {p2})' | translate: { p0: (f.booking.chargesTotal | number: '1.2-2'), p1: (f.deposits | number: '1.2-2'), p2: (f.booking.balance | number: '1.2-2') } }}</span></p>
      </ng-container>
    </app-shell>
  `,
})
export class BookingDetailComponent implements OnInit {
  folio: Folio | null = null;
  methods: PaymentMethodOption[] = [];
  panel: Panel = null;
  busy = false;
  error: string | null = null;
  message: string | null = null;
  todayStr = today();

  freeRooms: Room[] = [];
  roomId: number | null = null;
  allowNotReady = false;
  edit: any = {};
  plans: RatePlan[] = [];
  nights: QuoteNight[] = [];
  showNights = false;
  reason = '';
  newDeparture = '';
  chargeDesc = '';
  chargeAmount: number | null = null;
  chargeTaxable = true;
  payAmount: number | null = null;
  payMethod = '';
  payRef = '';
  payLines: { method: string; amount: number | null }[] = [];
  refundMethod = '';
  depositAction: 'REFUND' | 'FORFEIT' = 'REFUND';

  companies: CompanyOption[] = [];
  guestBills: Bill[] = [];
  billForm: { label: string; companyId: number | null } | null = null;
  pickCompanyFor: Bill | null = null;
  pickCompanyId: number | null = null;
  chargeBill: number | null = null;
  payBill: number | null = null;
  moveLine: FolioCharge | null = null;
  moveTargets: InHouseGuest[] = [];
  moveTarget: number | null = null;
  cancelPrev: CancelPreview | null = null;
  waiveFee = false;
  coPreview: CheckoutPreview | null = null;
  waiveFees = false;
  coToPay = 0;

  badge = statusBadge;
  label = statusLabel;
  private id = 0;

  constructor(private api: HotelApiService, private pay: PaymentMethodApiService, private route: ActivatedRoute, private router: Router, public auth: AuthService, private tr: TranslateService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.pay.listActive().subscribe((m) => {
      this.methods = m.filter((x) => !x.credit && !x.roomCharge);
      const preferred = (this.methods.find((x) => x.cash) ?? this.methods[0])?.code ?? '';
      this.payMethod = this.refundMethod = preferred;
    });
    if (this.canManage) this.api.companies().subscribe({ next: (c) => (this.companies = c), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.folio(this.id).subscribe({
      next: (f) => {
        this.folio = f;
        this.guestBills = f.bills.filter((b) => !b.companyId && !b.toGroup);
        this.nights = [];
        if (f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN') {
          this.api.bookingNights(this.id).subscribe({ next: (n) => (this.nights = n), error: () => {} });
        }
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load the booking')),
    });
  }

  /** True when every night costs the same, so there is nothing to break down. */
  sameNights(): boolean {
    return this.nights.length === 0 || this.nights.every((n) => n.net === this.nights[0].net);
  }

  typeLabel(t: string): string {
    return { ROOM: 'Room', RESTAURANT: 'Restaurant', POS: 'Shop', ROOM_SERVICE: 'Room service', EXTRA: 'Extra', FEE: 'Fee' }[t] ?? t;
  }

  billLabel(n: number): string {
    const b = this.folio?.bills.find((x) => x.billNo === n);
    return b?.label || this.tr.instant('Bill {p0}', { p0: n });
  }

  open(panel: Panel): void {
    this.error = this.message = null;
    this.panel = panel;
    const b = this.folio!.booking;
    if (panel === 'checkin') {
      this.roomId = b.roomId;
      this.allowNotReady = false;
      this.loadRooms(null, today(), b.checkOutDate > today() ? b.checkOutDate : addDays(today(), 1), b.id, b.roomId);
    } else if (panel === 'move') {
      this.roomId = null;
      this.reason = '';
      const from = today();
      this.loadRooms(null, from, b.checkOutDate > from ? b.checkOutDate : addDays(from, 1), b.id, null);
    } else if (panel === 'edit') {
      this.edit = { checkInDate: b.checkInDate, checkOutDate: b.checkOutDate, adults: b.adults, children: b.children, nightlyRate: b.nightlyRate, notes: b.notes, ratePlanId: b.ratePlanId, mode: b.rateMode };
      if (this.plans.length === 0) this.api.ratePlans().subscribe({ next: (p) => (this.plans = p.filter((x) => x.active || x.id === b.ratePlanId)), error: () => {} });
    } else if (panel === 'extend') {
      this.newDeparture = b.checkOutDate;
    } else if (panel === 'charge') {
      this.chargeBill = this.guestBills[0]?.billNo ?? 1;
      this.chargeDesc = '';
      this.chargeAmount = null;
      this.chargeTaxable = true;
    } else if (panel === 'payment') {
      this.payAmount = null;
      this.payRef = '';
      this.payBill = this.guestBills[0]?.billNo ?? 1;
      if (b.status === 'CANCELLED' || b.status === 'NO_SHOW') this.payAmount = this.folio!.balance;
    } else if (panel === 'checkout') {
      this.coPreview = null;
      this.waiveFees = false;
      this.payLines = [{ method: this.payMethod, amount: 0 }];
      this.loadCheckoutPreview();
    } else if (panel === 'cancel' || panel === 'noshow') {
      this.reason = '';
      this.depositAction = 'REFUND';
      this.waiveFee = false;
      this.cancelPrev = null;
      this.api.cancelPreview(this.id, panel === 'noshow').subscribe({ next: (p) => (this.cancelPrev = p), error: () => {} });
    }
  }

  private loadRooms(typeId: number | null, from: string, to: string, exclude: number, keep: number | null): void {
    this.api.freeRooms(typeId, from, to, exclude).subscribe((r) => {
      this.freeRooms = r.filter((x) => x.id !== this.folio?.booking.roomId || keep === x.id);
      if (keep && !this.freeRooms.some((x) => x.id === keep)) this.roomId = null;
    });
  }

  private loadCheckoutPreview(): void {
    this.api.checkoutPreview(this.id).subscribe({
      next: (pv) => {
        this.coPreview = pv;
        this.coWaive();
      },
      error: (e) => (this.error = errMsg(e, 'Could not work out the bill')),
    });
  }

  /** Recomputes what the guest still has to pay when fees are waived or not, and fills the first payment line. */
  coWaive(): void {
    const pv = this.coPreview;
    if (!pv) return;
    const received = this.folio!.deposits + this.folio!.paymentsTotal - this.folio!.refunds;
    const fees = this.waiveFees ? 0 : pv.fees.reduce((t, x) => t + x.gross, 0);
    const feesIncluded = pv.fees.reduce((t, x) => t + x.gross, 0);
    this.coToPay = Math.max(0, pv.guestDue - feesIncluded + fees - received);
    if (this.payLines.length === 1) this.payLines[0].amount = Math.round(this.coToPay * 100) / 100;
  }

  openBillForm(): void {
    this.billForm = { label: '', companyId: null };
  }

  addBill(): void {
    const f = this.billForm!;
    this.run(this.api.addBill(this.id, f.label.trim() || null, f.companyId), T('Bill added.'), false);
    this.billForm = null;
  }

  billTo(b: Bill): void {
    this.pickCompanyFor = b;
    this.pickCompanyId = null;
  }

  confirmBillTo(): void {
    const b = this.pickCompanyFor!;
    this.run(this.api.updateBill(this.id, b.billNo, { companyId: this.pickCompanyId }), T('The bill will go on the company\'s account at check-out.'), false);
    this.pickCompanyFor = null;
  }

  billGroup(b: Bill): void {
    this.run(this.api.updateBill(this.id, b.billNo, { toGroup: true }), T('The bill will go on the group\'s account at check-out.'), false);
  }

  backToGuest(b: Bill): void {
    this.run(this.api.updateBill(this.id, b.billNo, { clearCompany: true }), T('Bill back with the guest.'), false);
  }

  roomHere(b: Bill): void {
    this.run(this.api.setRoomBill(this.id, b.billNo), T('Room nights moved to this bill.'), false);
  }

  removeBill(b: Bill): void {
    this.run(this.api.deleteBill(this.id, b.billNo), T('Bill removed.'), false);
  }

  assign(c: FolioCharge, billNo: number): void {
    if (billNo === c.billNo) return;
    this.run(this.api.assignCharge(this.id, c.id, billNo), T('Moved to {p0}.', { p0: this.billLabel(billNo) }), false);
  }

  openMoveCharge(c: FolioCharge): void {
    this.error = this.message = null;
    this.moveLine = c;
    this.moveTarget = null;
    this.reason = '';
    this.panel = 'movecharge';
    this.api.inHouseGuests().subscribe({ next: (g) => (this.moveTargets = g.filter((x) => x.bookingId !== this.id)), error: () => {} });
  }

  doMoveCharge(): void {
    if (!this.moveLine || !this.moveTarget) return;
    this.run(this.api.moveCharge(this.id, this.moveLine.id, this.moveTarget, this.reason || null), T('Charge moved to the other guest.'));
  }

  pdf(billNo: number): void {
    showPdf(this.api.billPdf(this.id, billNo), 'bill.pdf', (err) => { if (err) this.error = err; });
  }

  registrationCard(): void {
    showPdf(this.api.registrationCardPdf(this.id), 'registration-card.pdf', (err) => { if (err) this.error = err; });
  }

  confirmation(): void {
    showPdf(this.api.confirmationPdf(this.id), 'confirmation.pdf', (err) => { if (err) this.error = err; });
  }

  mailConfirmation(): void {
    this.error = null;
    this.message = null;
    this.api.sendConfirmationMail(this.id).subscribe({
      next: () => (this.message = T('The confirmation is on its way to the guest.')),
      error: (e) => (this.error = errMsg(e, 'Could not send the e-mail')),
    });
  }

  receipt(paymentId: number): void {
    showPdf(this.api.receiptPdf(this.id, paymentId), 'receipt.pdf', (err) => { if (err) this.error = err; });
  }

  addPayLine(): void {
    this.payLines.push({ method: this.payMethod, amount: null });
  }

  private run(call: { subscribe: (o: any) => any }, ok: string | null, close = true): void {
    this.error = this.message = null;
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        if (close) this.panel = null;
        if (ok) this.message = ok;
        this.load();
      },
      error: (e: any) => {
        this.busy = false;
        this.error = errMsg(e, 'That did not work');
      },
    });
  }

  doCheckIn(): void {
    this.run(this.api.checkIn(this.id, this.roomId, this.allowNotReady), T('Guest checked in.'));
  }

  doEdit(): void {
    const b = this.folio!.booking;
    const e = this.edit;
    const payload: any = { checkOutDate: e.checkOutDate, adults: e.adults, children: e.children, notes: e.notes ?? '' };
    // A typed rate makes the price an agreement; leaving the choice on "Hotel rates" keeps (or restores) night-by-night pricing.
    if (e.mode === 'FIXED') payload.nightlyRate = e.nightlyRate;
    else if (b.rateMode === 'FIXED') payload.autoRate = true;
    if (b.status === 'BOOKED') {
      payload.checkInDate = e.checkInDate;
      if ((e.ratePlanId ?? null) !== (b.ratePlanId ?? null) && e.ratePlanId !== null) payload.ratePlanId = e.ratePlanId;
    }
    this.run(this.api.updateBooking(this.id, payload), T('Booking updated.'));
  }

  doMove(): void {
    if (!this.roomId) return void (this.error = T('Choose the new room.'));
    this.run(this.api.moveRoom(this.id, this.roomId, this.reason || null), T('Guest moved.'));
  }

  doExtend(): void {
    this.run(this.api.extend(this.id, this.newDeparture), T('Departure date changed.'));
  }

  doCharge(): void {
    if (this.chargeAmount === null) return;
    this.run(this.api.addCharge(this.id, this.chargeDesc, this.chargeAmount, this.chargeTaxable, this.folio!.bills.length > 1 ? this.chargeBill : null), T('Charge added.'));
  }

  doPayment(): void {
    if (!this.payAmount) return;
    const booked = this.folio!.booking.status === 'BOOKED';
    this.run(this.api.addPayment(this.id, booked ? 'DEPOSIT' : 'PAYMENT', this.payMethod, this.payAmount, this.payRef || null, this.guestBills.length > 1 ? this.payBill : null), booked ? T('Deposit recorded.') : T('Payment recorded.'));
  }

  voidCharge(chargeId: number): void {
    const reason = prompt('Why is this charge being voided? (optional)');
    if (reason === null) return;
    this.run(this.api.voidCharge(this.id, chargeId, reason || null), T('Charge voided.'), false);
  }

  doCheckout(): void {
    const payments: PaymentLine[] = this.payLines.filter((l) => l.amount && l.amount > 0).map((l) => ({ method: l.method, amount: l.amount! }));
    this.error = this.message = null;
    this.busy = true;
    this.api.checkOut(this.id, payments, this.refundMethod || null, this.waiveFees).subscribe({
      next: () => {
        this.busy = false;
        this.router.navigate(['/admin/hotel/bookings', this.id, 'invoice']);
      },
      error: (e) => {
        this.busy = false;
        this.error = errMsg(e, 'Could not check out');
      },
    });
  }

  doCancel(noShow: boolean): void {
    const hasDeposit = (this.folio?.deposits ?? 0) > 0;
    const action = hasDeposit ? this.depositAction : 'REFUND';
    const call = noShow
      ? this.api.noShow(this.id, this.reason || null, action, this.refundMethod || null, this.waiveFee)
      : this.api.cancel(this.id, this.reason || null, action, this.refundMethod || null, this.waiveFee);
    this.run(call, noShow ? T('Marked as no-show.') : T('Booking cancelled.'));
  }
}
