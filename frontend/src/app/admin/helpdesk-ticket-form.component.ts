import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCategoryApiService, HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, Customer, HelpDeskCategory, HelpDeskPriority, HelpDeskTicketType } from '../core/models';

/**
 * Admin > Help Desk > Tickets > New/Edit — staff-created ("authenticated") half of ticket
 * creation (Fourier, via AskUserQuestion: "Staff-created vs. public form"). Picking an existing
 * Customer pre-fills requester name/email/phone from that record, same "quick customer creation"
 * convenience {@code SubscriptionFormComponent} already has for its own customer picker — minus
 * the inline quick-add panel here, since a ticket's requester doesn't have to be an existing
 * Customer at all (a free-text name/email/phone works just as well for a true walk-in).
 *
 * <p>type is fixed once a ticket exists — see the backend HelpDeskTicketRequest's own javadoc —
 * so the type picker is disabled on edit.
 */
@Component({
  selector: 'app-helpdesk-ticket-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit ticket' : 'New Help Desk ticket' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Type</label>
          <select class="form-select" name="type" [(ngModel)]="type" [disabled]="!!id">
            <option value="CUSTOMER">Customer request</option>
            <option value="INTERNAL">Internal request</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            Type can't be changed after a ticket is created — close this one and open a new one if it was miscategorized.
          </div>
        </div>

        <div class="form-group" *ngIf="type === 'CUSTOMER'">
          <label class="form-label">Customer (optional)</label>
          <select class="form-select" name="customerId" [(ngModel)]="customerId" (ngModelChange)="onCustomerChange()">
            <option [ngValue]="null">— Not an existing customer / internal —</option>
            <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>

        <div class="form-group" *ngIf="type === 'CUSTOMER'">
          <label class="form-label">Requester name</label>
          <input class="form-control" name="requesterName" [(ngModel)]="requesterName" placeholder="Who's asking" />
        </div>
        <div class="flex gap-2 mb-2" *ngIf="type === 'CUSTOMER'">
          <div class="grow">
            <label class="form-label">Requester email</label>
            <input class="form-control" type="email" name="requesterEmail" [(ngModel)]="requesterEmail" />
          </div>
          <div class="grow">
            <label class="form-label">Requester phone</label>
            <input class="form-control" name="requesterPhone" [(ngModel)]="requesterPhone" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Subject</label>
          <input class="form-control" name="subject" [(ngModel)]="subject" placeholder="Short summary" required />
        </div>
        <div class="form-group">
          <label class="form-label">Description</label>
          <textarea class="form-control" name="description" [(ngModel)]="description" rows="4" required></textarea>
        </div>
        <div class="flex gap-2 mb-2">
          <div class="grow">
            <label class="form-label">Category (optional)</label>
            <select class="form-select" name="categoryId" [(ngModel)]="categoryId">
              <option [ngValue]="null">— None —</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="grow">
            <label class="form-label">Priority</label>
            <select class="form-select" name="priority" [(ngModel)]="priority">
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Assign to (optional)</label>
          <select class="form-select" name="assignedToUserId" [(ngModel)]="assignedToUserId">
            <option [ngValue]="null">— Unassigned —</option>
            <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>

        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ id ? 'Save' : 'Create ticket' }}</button>
          <a class="btn btn-link" [routerLink]="id ? ['/admin/helpdesk/tickets', id] : ['/admin/helpdesk/tickets']">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class HelpDeskTicketFormComponent implements OnInit {
  id: number | null = null;
  type: HelpDeskTicketType = 'CUSTOMER';
  customerId: number | null = null;
  requesterName: string | null = null;
  requesterEmail: string | null = null;
  requesterPhone: string | null = null;
  subject = '';
  description = '';
  categoryId: number | null = null;
  priority: HelpDeskPriority = 'MEDIUM';
  assignedToUserId: number | null = null;

  customers: Customer[] = [];
  categories: HelpDeskCategory[] = [];
  users: AppUser[] = [];
  error: string | null = null;

  constructor(
    private ticketApi: HelpDeskTicketApiService,
    private categoryApi: HelpDeskCategoryApiService,
    private customerApi: CustomerApiService,
    private userApi: UserApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c) });
    this.categoryApi.listAll().subscribe({ next: (c) => (this.categories = c.filter((x) => x.active)) });
    this.userApi.list().subscribe({ next: (u) => (this.users = u) });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.ticketApi.getById(this.id).subscribe({
      next: (t) => {
        this.type = t.type;
        this.customerId = t.customerId;
        this.requesterName = t.requesterName;
        this.requesterEmail = t.requesterEmail;
        this.requesterPhone = t.requesterPhone;
        this.subject = t.subject;
        this.description = t.description;
        this.categoryId = t.categoryId;
        this.priority = t.priority;
        this.assignedToUserId = t.assignedToUserId;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load ticket'),
    });
  }

  /** Fourier: picking a customer pre-fills requester details from their record, same convenience as SubscriptionFormComponent's own customer picker — still editable afterward. */
  onCustomerChange(): void {
    const customer = this.customers.find((c) => c.id === this.customerId);
    if (customer) {
      this.requesterName = customer.name;
      this.requesterEmail = customer.email;
      this.requesterPhone = customer.phone;
    }
  }

  submit(): void {
    this.error = null;
    if (!this.subject.trim() || !this.description.trim()) {
      this.error = 'Subject and description are required';
      return;
    }
    const payload = {
      type: this.type,
      subject: this.subject.trim(),
      description: this.description.trim(),
      categoryId: this.categoryId,
      priority: this.priority,
      customerId: this.type === 'CUSTOMER' ? this.customerId : null,
      requesterName: this.type === 'CUSTOMER' ? this.requesterName : null,
      requesterEmail: this.type === 'CUSTOMER' ? this.requesterEmail : null,
      requesterPhone: this.type === 'CUSTOMER' ? this.requesterPhone : null,
      assignedToUserId: this.assignedToUserId,
    };
    const call = this.id ? this.ticketApi.update(this.id, payload) : this.ticketApi.create(payload);
    call.subscribe({
      next: (t) => this.router.navigate(['/admin/helpdesk/tickets', t.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save ticket'),
    });
  }
}
