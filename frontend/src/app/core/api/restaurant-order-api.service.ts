import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { KitchenTicket, RestaurantOrder } from '../models';

export interface OpenTableOrderPayload {
  guestCount: number;
  serverId?: number | null;
  notes?: string | null;
}

export interface AddRestaurantLinePayload {
  menuItemId: number;
  quantity: number;
  notes?: string | null;
}

export interface UpdateRestaurantLinePayload {
  quantity?: number | null;
  notes?: string | null;
}

export interface RestaurantCheckoutPayload {
  payments: { method: string; amount: number }[];
  /** Hotel module: required when a payment uses the "Charge to room" method (a room-service order already knows its guest). */
  roomBookingId?: number | null;
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

  removeLine(orderId: number, lineId: number): Observable<RestaurantOrder> {
    return this.http.delete<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/lines/${lineId}`);
  }

  sendToKitchen(orderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/send-to-kitchen`, {});
  }

  checkout(orderId: number, payload: RestaurantCheckoutPayload): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/checkout`, payload);
  }

  cancel(orderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/orders/${orderId}/cancel`, {});
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
