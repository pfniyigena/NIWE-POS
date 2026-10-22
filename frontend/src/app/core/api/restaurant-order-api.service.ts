import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { KitchenTicket, RestaurantOrder, RestaurantSettings, RestaurantVoidEntry } from '../models';

export interface OpenTableOrderPayload {
  guestCount: number;
  serverId?: number | null;
  notes?: string | null;
}

export interface AddRestaurantLinePayload {
  menuItemId: number;
  quantity: number;
  notes?: string | null;
  /** Ids of the picked options. */
  optionIds?: number[] | null;
  /** Course 1 to 5 (the item's usual course when left out). */
  course?: number | null;
  seat?: number | null;
}

export interface UpdateRestaurantLinePayload {
  quantity?: number | null;
  notes?: string | null;
  /** A discount on the line in percent (0 removes it); needs a reason. */
  discountPercent?: number | null;
  discountReason?: string | null;
  /** Change the picked options (only before the line is sent). */
  optionIds?: number[] | null;
  course?: number | null;
  /** Seat number (0 clears it). */
  seat?: number | null;
}

/** Which items to take from a bill: lines (optionally part of the quantity) and/or whole seats. */
export interface CheckSelectionPayload {
  lines?: { lineId: number; quantity?: number }[];
  seats?: number[];
  targetOrderId?: number | null;
}

export interface RestaurantCheckoutPayload {
  payments: { method: string; amount: number }[];
  /** Hotel module: required when a payment uses the "Charge to room" method (a room-service order already knows its guest). */
  roomBookingId?: number | null;
  /** A tip on top of the bill; the payments must cover bill + tip. */
  tip?: number | null;
}

/** Fourier: "Delivery & takeaway" — opens a TAKEAWAY or DELIVERY order (no table). */
export interface OpenOffPremiseOrderPayload {
  customerName: string;
  customerPhone?: string | null;
  /** Required by the backend for delivery orders; ignored for takeaway. */
  customerAddress?: string | null;
  guestCount: number;
  serverId?: number | null;
  notes?: string | null;
}

/** Minimal staff option for the driver picker — see the backend's StaffOptionDto javadoc. */
export interface StaffOption {
  id: number;
  fullName: string;
}

/** The dine-in order-taking + kitchen + checkout flow (Fourier: "New dedicated screen," kept separate from the walk-up POS terminal). */
@Injectable({ providedIn: 'root' })
export class RestaurantOrderApiService {
  constructor(private http: HttpClient) {}

  /** One page of the filtered list (search, status, dates; the total comes back in X-Total-Count). */
  closedPage(f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<RestaurantOrder>> {
    return pagedList<RestaurantOrder>(this.http, `${API_BASE}/restaurant/orders/closed`, f, page, size, extra);
  }

  listOpen(): Observable<RestaurantOrder[]> {
    return this.http.get<RestaurantOrder[]>(`${API_BASE}/restaurant/orders/open`);
  }

  getById(id: number): Observable<RestaurantOrder> {
    return this.http.get<RestaurantOrder>(`${API_BASE}/restaurant/orders/${id}`);
  }

  /** Restaurant Receipts screen (lookup + reprint) — most recent checked-out orders. */
  listClosed(): Observable<RestaurantOrder[]> {
    return this.http.get<RestaurantOrder[]>(`${API_BASE}/restaurant/orders/closed`);
  }

  openOrder(tableId: number, payload: OpenTableOrderPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/tables/${tableId}/orders`, payload);
  }

  // ---- Delivery & takeaway (Fourier: "Delivery & takeaway") ----

  openTakeawayOrder(payload: OpenOffPremiseOrderPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/takeaway`, payload);
  }

  /** Hotel module: a room-service order for a checked-in guest. */
  openRoomServiceOrder(payload: { bookingId: number; notes?: string | null }): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/room-service`, payload);
  }

  openDeliveryOrder(payload: OpenOffPremiseOrderPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/delivery`, payload);
  }

  listDeliveries(): Observable<RestaurantOrder[]> {
    return this.http.get<RestaurantOrder[]>(`${API_BASE}/restaurant/orders/deliveries`);
  }

  assignDriver(orderId: number, driverId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/assign-driver`, { driverId });
  }

  setDeliveryStatus(orderId: number, status: string): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/delivery-status`, { status });
  }

  /** Driver-picker options, open to any logged-in staff — see the backend's StaffOptionDto javadoc for why this isn't the ADMIN-only UserApiService.list(). */
  listActiveStaff(): Observable<StaffOption[]> {
    return this.http.get<StaffOption[]>(`${API_BASE}/restaurant/staff`);
  }

  addLine(orderId: number, payload: AddRestaurantLinePayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines`, payload);
  }

  updateLine(orderId: number, lineId: number, payload: UpdateRestaurantLinePayload): Observable<RestaurantOrder> {
    return this.http.patch<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}`, payload);
  }

  /** Sets (or with 0 / nothing removes) the discount on the whole order: a percentage or an amount, with a reason. */
  setDiscount(orderId: number, payload: { percent?: number | null; amount?: number | null; reason?: string | null }): Observable<RestaurantOrder> {
    return this.http.put<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/discount`, payload);
  }

  setServiceCharge(orderId: number, waived: boolean): Observable<RestaurantOrder> {
    return this.http.put<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/service-charge`, { waived });
  }

  getSettings(): Observable<RestaurantSettings> {
    return this.http.get<RestaurantSettings>(`${API_BASE}/restaurant/settings`);
  }

  saveSettings(payload: RestaurantSettings): Observable<RestaurantSettings> {
    return this.http.put<RestaurantSettings>(`${API_BASE}/restaurant/settings`, payload);
  }

  /** Takes an item off. One that already went to the kitchen needs a reason and a manager's approval. */
  removeLine(orderId: number, lineId: number, reason?: string | null): Observable<RestaurantOrder> {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.http.delete<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}${q}`);
  }

  compLine(orderId: number, lineId: number, reason: string): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}/comp`, { reason });
  }

  uncompLine(orderId: number, lineId: number): Observable<RestaurantOrder> {
    return this.http.delete<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}/comp`);
  }

  /** Splits items off into a new check at the same table; returns the new check. */
  split(orderId: number, payload: CheckSelectionPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/split`, payload);
  }

  /** Moves items onto another open bill; returns the receiving bill. */
  transfer(orderId: number, payload: CheckSelectionPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/transfer`, payload);
  }

  moveTable(orderId: number, tableId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/move-table`, { tableId });
  }

  /** Records a print of the receipt (from the second print it is a copy). */
  recordPrint(orderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/print`, {});
  }

  voidsPage(f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<RestaurantVoidEntry>> {
    return pagedList<RestaurantVoidEntry>(this.http, `${API_BASE}/restaurant/voids`, f, page, size, extra);
  }

  sendToKitchen(orderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/send-to-kitchen`, {});
  }

  /** Releases the waiting lines of a course (and earlier ones) to the kitchen. */
  fireCourse(orderId: number, course: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/fire-course`, { course });
  }

  setGuestAllergens(orderId: number, allergens: string): Observable<RestaurantOrder> {
    return this.http.put<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/guest-allergens`, { allergens });
  }

  checkout(orderId: number, payload: RestaurantCheckoutPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/checkout`, payload);
  }

  cancel(orderId: number, reason?: string | null): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/cancel`, { reason: reason ?? null });
  }

  /** Fourier: "split & merge bills" — the "merge" half: folds `orderId`'s lines into `targetOrderId`. */
  merge(orderId: number, targetOrderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/merge`, { targetOrderId });
  }

  // ---- Kitchen Display (Fourier: "Kitchen Order Tickets / Kitchen Display") ----

  listKitchenTickets(): Observable<KitchenTicket[]> {
    return this.http.get<KitchenTicket[]>(`${API_BASE}/restaurant/kitchen/tickets`);
  }

  setLineKitchenStatus(orderId: number, lineId: number, status: string): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}/kitchen-status`, { status });
  }
}
