import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

export interface ShopZone { id: number; name: string; fee: number; minOrder: number }
export interface ShopProduct { id: number; name: string; description: string | null; price: number; category: string | null; imageUrl: string | null; stock: 'IN_STOCK' | 'LOW' | 'SOLD_OUT';
  /** The normal price when staff set a lower online price (shown struck through); 0 stars and 0 reviews when nobody reviewed it. */
  wasPrice: number | null; rating: number; ratingCount: number }
export interface Shop {
  name: string; intro: string | null; phone: string | null; pickupAddress: string | null; open: boolean; message: string | null;
  pickup: boolean; delivery: boolean; cash: boolean; minOrder: number; freeDeliveryOver: number | null;
  zones: ShopZone[]; categories: string[]; payOnline: boolean; payMethods: string[]; accounts: boolean; currency: string; coupons: boolean;
  /** SMS or WHATSAPP when the shop texts customers, else null: the tick box and the phone sign-in only show then. */
  textChannel: string | null;
  /** True when the shop keeps carts to remind customers who typed an e-mail address: the notice under the e-mail box shows then. */
  cartReminders: boolean;
  /** True when signed-in customers earn and spend loyalty points; loyaltyMaxPercent is the share of the goods points may pay. */
  loyalty: boolean;
  loyaltyMaxPercent: number;
  /** The front page's wording, written by staff in Shop settings > Front page. */
  page: ShopPageContent;
}
export interface ShopLink { label: string; url: string }
export interface ShopPageContent {
  announcementText: string | null; announcementLink: string | null; heroTitle: string | null; heroSubtitle: string | null; heroButtonText: string | null;
  heroButtonLink: string | null; heroImageUrl: string | null; aboutText: string | null; contactEmail: string | null; openingHours: string | null;
  footerLinks: ShopLink[]; socialLinks: ShopLink[];
}
export interface ShopReview { productName: string; reviewerName: string; rating: number; text: string; at: string }
/** The rows of the front page, chosen by staff, and the best approved reviews. */
export interface ShopHome {
  featured: ShopProduct[]; bestSellers: ShopProduct[]; offers: ShopProduct[]; newArrivals: ShopProduct[];
  banners: string[]; reviews: ShopReview[]; ratingAverage: number | null; ratingCount: number;
}
export interface ShopReviewItem { productId: number; name: string; rating: number | null; text: string | null; status: string | null }
export interface ShopReviewOptions { canReview: boolean; why: string | null; items: ShopReviewItem[] }
export interface ShopPage { items: ShopProduct[]; page: number; size: number; total: number; totalPages: number }
export interface ShopOrderPayload {
  name: string; phone: string; email: string | null; address: string | null; method: 'DELIVERY' | 'PICKUP'; zoneId: number | null;
  notes: string | null; language: string; website: string; lines: { productId: number; quantity: number }[];
  payment: 'CASH' | 'MOBILE_MONEY' | 'CARD'; payPhone: string | null; couponCode: string | null;
  /** The customer ticked "send me updates by text message". */
  textUpdates: boolean;
  /** Loyalty points the signed-in customer wants to use; the server decides how many may be used. */
  pointsToUse?: number | null;
}
/** What the customer sees about paying: method COD or ONLINE; status NONE, AWAITING, FAILED, PAID, REFUND_DUE or REFUNDED. */
export interface ShopPayment { method: string; status: string; channel: string | null; redirectUrl: string | null; canRetry: boolean; expiresAt: string | null; paidAmount: number | null }
export interface ShopTrack {
  code: string; status: string; deliveryMethod: string; name: string; zoneName: string | null; subtotal: number; deliveryFee: number; total: number;
  rejectReason: string | null; placedAt: string; lines: { name: string; quantity: number; unitPrice: number; lineTotal: number }[];
  history: { status: string; note: string | null; at: string }[];
  payment: ShopPayment;
  couponCode: string | null; discount: number; feeWaived: number;
  /** The part of discount that was paid with loyalty points, and the points this order earned. */
  loyaltyDiscount: number; pointsEarned: number;
}
/** A customer account on the public shop (no passwords: a code by e-mail or text message proves the address or number). An account opened with a phone number has no e-mail. */
export interface AccountProfile { email: string | null; phone: string | null; name: string | null; address: string | null; language: string; phoneVerified: boolean; points: number }
export interface AccountSession { token: string; profile: AccountProfile }
export interface AccountOrder { code: string; status: string; deliveryMethod: string; total: number; placedAt: string; items: number; paymentStatus: string }
/** The products of an earlier order as the shop has them today; unavailable names what is gone or sold out. */
export interface Reorder { lines: { product: ShopProduct; quantity: number }[]; unavailable: string[] }
/** kind: EARN, SPEND, SPEND_BACK, RET_BACK, RET_TAKE or ADJUST; points is signed. */
export interface LoyaltyEntry { id: number; kind: string; points: number; balanceAfter: number; orderCode: string | null; note: string | null; at: string }
export interface LoyaltyView { enabled: boolean; points: number; pointsPer100: number; maxPercent: number; entries: LoyaltyEntry[] }
export interface LoyaltyCustomer { id: number; name: string | null; email: string | null; phone: string | null; points: number }
export interface LoyaltyList { customersWithPoints: number; pointsOutstanding: number; items: LoyaltyCustomer[] }
/** The cart behind the link of a reminder e-mail, as the shop has it today. */
export interface RestoredCart extends Reorder { email: string | null }
export interface StoreCart {
  id: number; email: string; status: 'ACTIVE' | 'REMINDED' | 'DONE' | 'RECOVERED' | 'STOPPED' | 'EXPIRED'; items: number; quantity: number; savedAt: string;
  reminders: number; firstReminderAt: string | null; secondReminderAt: string | null; recoveredAt: string | null; lastError: string | null;
}
export interface StoreCartList { waiting: number; reminded: number; done: number; recovered: number; stopped: number; expired: number; items: StoreCart[] }
/** What a coupon code is worth on the cart: discount off the items, feeWaived off the delivery fee. */
export interface CouponCheckResult { code: string; type: string; discount: number; feeWaived: number }
export interface StoreCoupon {
  id: number; code: string; description: string | null; type: 'PERCENT' | 'FIXED' | 'FREE_DELIVERY'; value: number; maxDiscount: number | null; minOrder: number;
  validFrom: string | null; validUntil: string | null; maxUses: number | null; oncePerCustomer: boolean; active: boolean; used: number;
}
export interface TestPayment { reference: string; amount: number; currency: string; method: string; state: string }

export interface StoreOrder {
  id: number; code: string; status: string; deliveryMethod: string; paymentMethod: string; customerName: string; customerPhone: string;
  customerEmail: string | null; customerAddress: string | null; zoneName: string | null; deliveryFee: number; notes: string | null;
  subtotal: number; total: number; language: string; salesOrderId: number | null; salesOrderNumber: string | null; invoiceId: number | null;
  invoiceNumber: string | null; rejectReason: string | null; decidedBy: string | null; decidedAt: string | null; createdAt: string;
  paymentStatus: string; paymentChannel: string | null; paymentReference: string | null; paidAmount: number | null; paidAt: string | null; refundedAt: string | null; refundReference: string | null;
  couponCode: string | null; discountAmount: number; feeWaived: number; refundedTotal: number;
  loyaltyPointsUsed: number; loyaltyDiscount: number; loyaltyPointsEarned: number;
  lines: { productId: number; name: string; quantity: number; unitPrice: number; lineTotal: number }[];
  events: { status: string; note: string | null; by: string | null; at: string }[];
}
export interface StoreSettings {
  enabled: boolean; paused: boolean; pauseMessage: string | null; shopName: string | null; introText: string | null; contactPhone: string | null;
  pickupAddress: string | null; pickupEnabled: boolean; deliveryEnabled: boolean; codEnabled: boolean; stockBuffer: number; minOrder: number;
  freeDeliveryOver: number | null; maxPendingPerPhone: number; maxPerIpPerHour: number; deliveryProductId: number | null; deliveryProductName: string | null; notifyEmail: string | null; unansweredAlertMinutes: number;
  onlinePaymentsEnabled: boolean; paymentProvider: string | null; paymentExpiryMinutes: number; accountsEnabled: boolean;
  textProvider: string | null; textChannel: string; textCountryCode: string; textDailyLimit: number; notifyPhone: string | null; returnWindowDays: number;
  cartRemindersEnabled: boolean;
  loyaltyEnabled: boolean; loyaltyPointsPer100: number; loyaltyMaxPercent: number;
}
export interface TextProvider { code: string; label: string; available: boolean; channels: string[] }
/** status is SENT, FAILED or SKIPPED; purpose is ORDER, OWNER, SIGNIN or TEST. */
export interface TextMessage { id: number; at: string; channel: string; to: string; purpose: string; body: string; status: string; provider: string | null; error: string | null; orderId: number | null }
export interface TextPage { items: TextMessage[]; page: number; size: number; total: number; totalPages: number }
export interface TextTestResult { status: string; error: string | null; to: string | null }
export interface PaymentProvider { code: string; label: string; available: boolean; methods: string[] }
export interface PaymentRow {
  id: number; orderId: number; orderCode: string; createdAt: string; provider: string; reference: string; providerReference: string | null; method: string; phone: string | null;
  amount: number; currency: string; status: string; failureReason: string | null; orderStatus: string; orderPaymentStatus: string; flag: string;
}
export interface PaymentSummary { paid: number; inBooks: number; held: number; refundDue: number; refunded: number; toReview: number; failed: number; stuck: number }
export interface Reconciliation { summary: PaymentSummary; rows: PaymentRow[] }
export interface PendingCounts { pending: number; overdue: number; alertMinutes: number; awaitingPayment?: number; refundsDue?: number; paymentsToReview?: number; returnsRequested?: number; returnsToRefund?: number; reviewsPending?: number }
export interface StoreListing {
  id: number; productId: number; productName: string; sku: string | null; category: string | null; unitPrice: number; listed: boolean;
  webTitle: string | null; webDescription: string | null; onlinePrice: number | null; sortOrder: number; hasImage: boolean; stock: string;
  featured: boolean; bestSeller: boolean; specialOffer: boolean; newArrival: boolean; stockInfo: StoreStockInfo;
}
/** Why a product shows the stock it shows. tracked false = stock is not tracked for it, so the shop always shows it as in stock. */
export interface StoreStockInfo { tracked: boolean; inWarehouses: number | null; reserved: number | null; buffer: number; sellable: number | null }
/** Staff's wording of the front page (Shop settings > Front page). Links are one per line, written Label|address. */
export interface StorePageContent {
  announcementText: string | null; announcementLink: string | null; heroTitle: string | null; heroSubtitle: string | null; heroButtonText: string | null;
  heroButtonLink: string | null; heroImageUrl: string | null; aboutText: string | null; contactEmail: string | null; openingHours: string | null;
  footerLinks: string | null; socialLinks: string | null;
}
export interface StoreBanner { id: number; fileName: string; sizeBytes: number; sortOrder: number; url: string }
export interface StoreReview {
  id: number; orderCode: string | null; productId: number; productName: string; reviewerName: string; rating: number; text: string | null;
  status: 'PENDING' | 'APPROVED' | 'HIDDEN'; createdAt: string; moderatedBy: string | null;
}
export interface StoreSubscriber { id: number; email: string; language: string; status: 'PENDING' | 'CONFIRMED' | 'UNSUBSCRIBED'; createdAt: string; confirmedAt: string | null; unsubscribedAt: string | null }
export interface StoreCampaign { id: number; subject: string; body: string; buttonLabel: string | null; buttonLink: string | null; audience: 'ALL' | 'EN' | 'FR'; status: 'DRAFT' | 'SENDING' | 'SENT' | 'CANCELLED'; recipients: number; sent: number; failed: number; skipped: number; pending: number; createdBy: string | null; createdAt: string; startedAt: string | null; finishedAt: string | null; lastError: string | null }
export interface StoreCampaignForm { subject: string; body: string; buttonLabel: string; buttonLink: string; audience: 'ALL' | 'EN' | 'FR' }
export interface StoreCampaignInfo { confirmedAll: number; confirmedEn: number; confirmedFr: number; mailConfigured: boolean; dailyLimit: number; sentLast24h: number; batchSize: number }
export interface StoreDelivery { id: number; email: string; language: string; status: 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED'; error: string | null; sentAt: string | null }
export interface StoreZone { id: number; name: string; fee: number; minOrder: number; active: boolean; sortOrder: number }

/** What the customer sees about a return: its status, what staff said and, once done, what was given back and how. */
export interface ShopReturn {
  code: string; status: 'REQUESTED' | 'APPROVED' | 'REFUSED' | 'REFUNDED'; reason: string; staffNote: string | null; refundAmount: number; refundMethod: string | null;
  refundReference: string | null; requestedAt: string; decidedAt: string | null; refundedAt: string | null; lines: { name: string; quantity: number; amount: number }[];
}
export interface ShopReturnLine { lineId: number; name: string; quantity: number; unitPaid: number; returnable: number }
/** allowed is false with a message saying why not (period over, not completed, everything already asked back). */
export interface ShopReturnOptions { allowed: boolean; message: string | null; windowDays: number; lastDay: string | null; lines: ShopReturnLine[]; returns: ShopReturn[] }
export interface StoreReturn {
  id: number; code: string; orderId: number; orderCode: string; customerName: string; customerPhone: string; customerEmail: string | null;
  status: 'REQUESTED' | 'APPROVED' | 'REFUSED' | 'REFUNDED'; reason: string; staffNote: string | null; refundAmount: number; refundMethod: 'PROVIDER' | 'MANUAL' | 'CASH' | null;
  refundReference: string | null; paymentMethod: string; paymentStatus: string; requestedAt: string; decidedBy: string | null; decidedAt: string | null;
  refundedBy: string | null; refundedAt: string | null; salesOrderReturnNumber: string | null; lines: { lineId: number; name: string; quantity: number; amount: number }[];
}

const TOKEN_KEY = 'niwe.shop.token';
/** The signed-in customer's session token, kept in this browser only. Storage can be blocked, so every access is guarded. */
export const shopToken = {
  get(): string | null {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set(t: string): void {
    try { localStorage.setItem(TOKEN_KEY, t); } catch { /* the customer just has to sign in again next time */ }
  },
  clear(): void {
    try { localStorage.removeItem(TOKEN_KEY); } catch { /* nothing to clear */ }
  },
};

/** The public shop (no login) and the staff screens of the Online Store module. */
@Injectable({ providedIn: 'root' })
export class OnlineStoreApiService {
  private pub = `${API_BASE}/public/store`;
  private base = `${API_BASE}/online-store`;

  constructor(private http: HttpClient) {}

  // ---- customers
  shop(): Observable<Shop> { return this.http.get<Shop>(`${this.pub}/shop`); }
  /** One page of the catalogue (page starts at 0); search and category are applied by the server. */
  products(page: number, size: number, q: string, category: string): Observable<ShopPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) params = params.set('q', q.trim());
    if (category) params = params.set('category', category);
    return this.http.get<ShopPage>(`${this.pub}/products`, { params });
  }
  /** A signed-in customer's order joins their account; a missing or stale token just makes it a guest order. */
  place(payload: ShopOrderPayload): Observable<ShopTrack> { return this.http.post<ShopTrack>(`${this.pub}/orders`, payload, { headers: this.shopHeaders() }); }
  track(code: string): Observable<ShopTrack> { return this.http.get<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}`); }
  cancel(code: string): Observable<ShopTrack> { return this.http.post<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/cancel`, {}); }
  /** What can still be returned on a completed order, and the returns already asked. */
  returnOptions(code: string): Observable<ShopReturnOptions> { return this.http.get<ShopReturnOptions>(`${this.pub}/orders/${encodeURIComponent(code)}/returns`); }
  /** Asks to return goods. The phone number of the order is needed unless the customer is signed in to the account that holds it. */
  requestReturn(code: string, body: { phone: string | null; reason: string; lines: { lineId: number; quantity: number }[] }): Observable<ShopReturn> {
    return this.http.post<ShopReturn>(`${this.pub}/orders/${encodeURIComponent(code)}/returns`, body, { headers: this.shopHeaders() });
  }
  // ---- the front page, reviews and the newsletter
  home(): Observable<ShopHome> { return this.http.get<ShopHome>(`${this.pub}/home`); }
  reviewOptions(code: string): Observable<ShopReviewOptions> { return this.http.get<ShopReviewOptions>(`${this.pub}/orders/${encodeURIComponent(code)}/reviews`); }
  /** A review of one product of a completed order; it waits for staff. The phone number is needed unless the customer is signed in. */
  submitReview(code: string, body: { phone: string | null; productId: number; rating: number; text: string | null; name: string | null }): Observable<ShopReviewItem> {
    return this.http.post<ShopReviewItem>(`${this.pub}/orders/${encodeURIComponent(code)}/reviews`, body, { headers: this.shopHeaders() });
  }
  subscribeNewsletter(email: string, language: string, website: string): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${this.pub}/newsletter`, { email, language, website });
  }
  confirmNewsletter(token: string): Observable<{ ok: boolean; unsubscribeToken: string }> {
    return this.http.post<{ ok: boolean; unsubscribeToken: string }>(`${this.pub}/newsletter/confirm`, { token });
  }
  /** The cart so far, sent a moment after the customer types an e-mail address (the shop keeps it only when its reminders are on). */
  saveCart(c: { email: string; language: string; website: string; lines: { productId: number; quantity: number }[] }): Observable<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>(`${this.pub}/carts`, c);
  }
  restoreCart(token: string): Observable<RestoredCart> { return this.http.get<RestoredCart>(`${this.pub}/carts/${encodeURIComponent(token)}`); }
  stopCartReminders(token: string): Observable<{ ok: boolean }> { return this.http.post<{ ok: boolean }>(`${this.pub}/carts/stop`, { token }); }
  unsubscribeNewsletter(token: string): Observable<{ ok: boolean }> { return this.http.post<{ ok: boolean }>(`${this.pub}/newsletter/unsubscribe`, { token }); }
  /** Pay again, or the other way, for an order that is waiting for its payment. */
  payAgain(code: string, payment: string, payPhone: string | null): Observable<ShopTrack> {
    return this.http.post<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/pay`, { payment, payPhone });
  }
  /** The test provider's pretend payment page (404 unless the simulated provider is on). */
  testPayment(reference: string): Observable<TestPayment> { return this.http.get<TestPayment>(`${this.pub}/payments/simulated/${encodeURIComponent(reference)}`); }
  decideTestPayment(reference: string, decision: 'approve' | 'decline'): Observable<TestPayment> { return this.http.post<TestPayment>(`${this.pub}/payments/simulated/${encodeURIComponent(reference)}/${decision}`, {}); }

  // ---- customers: accounts
  private shopHeaders(): HttpHeaders {
    const t = shopToken.get();
    return t ? new HttpHeaders({ 'X-Shop-Token': t }) : new HttpHeaders();
  }
  /** Sends a sign-in code to an e-mail address (or to the e-mail of the account that has this phone number). The answer is the same either way. */
  accountCode(identifier: string, language: string): Observable<{ sent: boolean }> { return this.http.post<{ sent: boolean }>(`${this.pub}/account/code`, { identifier, language }); }
  accountVerify(identifier: string, code: string): Observable<AccountSession> { return this.http.post<AccountSession>(`${this.pub}/account/verify`, { identifier, code }); }
  accountLoyalty(): Observable<LoyaltyView> { return this.http.get<LoyaltyView>(`${this.pub}/account/loyalty`, { headers: this.shopHeaders() }); }
  accountMe(): Observable<AccountProfile> { return this.http.get<AccountProfile>(`${this.pub}/account/me`, { headers: this.shopHeaders() }); }
  accountSave(p: { name: string | null; phone: string | null; address: string | null; language: string }): Observable<AccountProfile> { return this.http.put<AccountProfile>(`${this.pub}/account/me`, p, { headers: this.shopHeaders() }); }
  accountOrders(): Observable<AccountOrder[]> { return this.http.get<AccountOrder[]>(`${this.pub}/account/orders`, { headers: this.shopHeaders() }); }
  accountReorder(code: string): Observable<Reorder> { return this.http.get<Reorder>(`${this.pub}/account/orders/${encodeURIComponent(code)}/reorder`, { headers: this.shopHeaders() }); }
  accountLogout(): Observable<void> { return this.http.post<void>(`${this.pub}/account/logout`, {}, { headers: this.shopHeaders() }); }
  /** "Apply" at checkout: only asks what the code is worth now. */
  couponCheck(body: { code: string; method: string; zoneId: number | null; phone: string; lines: { productId: number; quantity: number }[] }): Observable<CouponCheckResult> {
    return this.http.post<CouponCheckResult>(`${this.pub}/coupons/check`, body, { headers: this.shopHeaders() });
  }
  /** Texts a code to the phone number saved on the account (needed before that number can be used to sign in). */
  accountPhoneCode(): Observable<void> { return this.http.post<void>(`${this.pub}/account/phone/code`, {}, { headers: this.shopHeaders() }); }
  accountPhoneVerify(code: string): Observable<AccountProfile> { return this.http.post<AccountProfile>(`${this.pub}/account/phone/verify`, { code }, { headers: this.shopHeaders() }); }
  accountDelete(): Observable<void> { return this.http.delete<void>(`${this.pub}/account`, { headers: this.shopHeaders() }); }
  imageUrl(path: string): string { return `${API_BASE.replace(/\/api$/, '')}${path}`; }

  // ---- staff: orders
  page(f: ListFilter, page: number, size: number): Observable<PageResult<StoreOrder>> {
    return pagedList<StoreOrder>(this.http, `${this.base}/orders`, f, page, size, {});
  }
  pendingCount(): Observable<PendingCounts> { return this.http.get<PendingCounts>(`${this.base}/orders/pending-count`); }
  accept(id: number, overrideShortage: boolean): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/accept`, { overrideShortage }); }
  reject(id: number, reason: string): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/reject`, { reason }); }
  pack(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/pack`, {}); }
  outForDelivery(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/out-for-delivery`, {}); }
  readyForPickup(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/ready-for-pickup`, {}); }
  complete(id: number, body: { locationId?: number | null; collectPayment?: boolean; method?: string }): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/complete`, body); }
  cancelOrder(id: number, note: string): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/cancel`, { note }); }

  /** Gives the money back: manual = true records a refund made outside the system (reference required); otherwise the provider is asked. */
  refund(id: number, body: { manual: boolean; reference?: string | null; note?: string | null }): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/refund`, body); }

  // ---- staff: returns
  returnsPage(f: ListFilter, page: number, size: number): Observable<PageResult<StoreReturn>> {
    return pagedList<StoreReturn>(this.http, `${this.base}/returns`, f, page, size, {});
  }
  approveReturn(id: number, note: string): Observable<StoreReturn> { return this.http.post<StoreReturn>(`${this.base}/returns/${id}/approve`, { note: note.trim() || null }); }
  refuseReturn(id: number, note: string): Observable<StoreReturn> { return this.http.post<StoreReturn>(`${this.base}/returns/${id}/refuse`, { note }); }
  /** The goods are back: stock and books through the sale order return, and the money given back the way the customer paid. */
  processReturn(id: number, body: { locationId: number | null; manual: boolean; reference: string | null; note: string | null }): Observable<StoreReturn> {
    return this.http.post<StoreReturn>(`${this.base}/returns/${id}/process`, body);
  }

  // ---- staff: online payments
  payments(from: string, to: string, status: string): Observable<Reconciliation> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (status) params = params.set('status', status);
    return this.http.get<Reconciliation>(`${this.base}/payments`, { params });
  }
  resolvePayment(id: number, note: string): Observable<void> { return this.http.post<void>(`${this.base}/payments/${id}/resolve`, { note }); }
  providers(): Observable<PaymentProvider[]> { return this.http.get<PaymentProvider[]>(`${this.base}/payment-providers`); }

  // ---- staff: text messages
  textProviders(): Observable<TextProvider[]> { return this.http.get<TextProvider[]>(`${this.base}/text-providers`); }
  texts(page: number, size: number): Observable<TextPage> { return this.http.get<TextPage>(`${this.base}/texts`, { params: new HttpParams().set('page', page).set('size', size) }); }
  testText(phone: string, text: string): Observable<TextTestResult> { return this.http.post<TextTestResult>(`${this.base}/texts/test`, { phone, text: text.trim() || null }); }

  // ---- staff: coupons
  coupons(): Observable<StoreCoupon[]> { return this.http.get<StoreCoupon[]>(`${this.base}/coupons`); }
  saveCoupon(id: number | null, body: Partial<StoreCoupon>): Observable<StoreCoupon> {
    return id == null ? this.http.post<StoreCoupon>(`${this.base}/coupons`, body) : this.http.put<StoreCoupon>(`${this.base}/coupons/${id}`, body);
  }
  deleteCoupon(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/coupons/${id}`); }

  // ---- staff: setup
  settings(): Observable<StoreSettings> { return this.http.get<StoreSettings>(`${this.base}/settings`); }
  saveSettings(s: StoreSettings): Observable<StoreSettings> { return this.http.put<StoreSettings>(`${this.base}/settings`, s); }
  listings(): Observable<StoreListing[]> { return this.http.get<StoreListing[]>(`${this.base}/listings`); }
  saveListing(body: { productId: number; listed?: boolean; webTitle?: string | null; webDescription?: string | null; onlinePrice?: number | null; sortOrder?: number;
    featured?: boolean; bestSeller?: boolean; specialOffer?: boolean; newArrival?: boolean }): Observable<StoreListing> {
    return this.http.post<StoreListing>(`${this.base}/listings`, body);
  }
  removeListing(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/listings/${id}`); }
  // ---- staff: front page wording, reviews, newsletter
  pageContent(): Observable<StorePageContent> { return this.http.get<StorePageContent>(`${this.base}/page-content`); }
  savePageContent(c: StorePageContent): Observable<StorePageContent> { return this.http.put<StorePageContent>(`${this.base}/page-content`, c); }
  banners(): Observable<StoreBanner[]> { return this.http.get<StoreBanner[]>(`${this.base}/banners`); }
  uploadBanner(file: File): Observable<StoreBanner> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<StoreBanner>(`${this.base}/banners`, form);
  }
  deleteBanner(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/banners/${id}`); }
  moveBanner(id: number, direction: 'up' | 'down'): Observable<StoreBanner[]> { return this.http.post<StoreBanner[]>(`${this.base}/banners/${id}/move?direction=${direction}`, {}); }
  reviewsPage(f: ListFilter, page: number, size: number): Observable<PageResult<StoreReview>> {
    return pagedList<StoreReview>(this.http, `${this.base}/reviews`, f, page, size, {});
  }
  approveReview(id: number): Observable<StoreReview> { return this.http.post<StoreReview>(`${this.base}/reviews/${id}/approve`, {}); }
  hideReview(id: number): Observable<StoreReview> { return this.http.post<StoreReview>(`${this.base}/reviews/${id}/hide`, {}); }
  deleteReview(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/reviews/${id}`); }
  subscribersPage(f: ListFilter, page: number, size: number): Observable<PageResult<StoreSubscriber>> {
    return pagedList<StoreSubscriber>(this.http, `${this.base}/newsletter`, f, page, size, {});
  }
  subscriberCounts(): Observable<Record<string, number>> { return this.http.get<Record<string, number>>(`${this.base}/newsletter/counts`); }
  exportSubscribers(): Observable<Blob> { return this.http.get(`${this.base}/newsletter/export`, { responseType: 'blob' }); }
  deleteSubscriber(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/newsletter/${id}`); }
  loyaltyList(query: string): Observable<LoyaltyList> { return this.http.get<LoyaltyList>(`${this.base}/loyalty`, { params: query ? { query } : {} }); }
  loyaltyLedger(id: number): Observable<LoyaltyView> { return this.http.get<LoyaltyView>(`${this.base}/loyalty/${id}`); }
  loyaltyAdjust(id: number, points: number, reason: string): Observable<LoyaltyView> { return this.http.post<LoyaltyView>(`${this.base}/loyalty/${id}/adjust`, { points, reason }); }
  carts(status: string): Observable<StoreCartList> { return this.http.get<StoreCartList>(`${this.base}/carts`, { params: status ? { status } : {} }); }
  deleteCart(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/carts/${id}`); }
  campaigns(): Observable<StoreCampaign[]> { return this.http.get<StoreCampaign[]>(`${this.base}/newsletter/campaigns`); }
  campaignInfo(): Observable<StoreCampaignInfo> { return this.http.get<StoreCampaignInfo>(`${this.base}/newsletter/campaigns/info`); }
  campaign(id: number): Observable<StoreCampaign> { return this.http.get<StoreCampaign>(`${this.base}/newsletter/campaigns/${id}`); }
  campaignDeliveries(id: number, status: string): Observable<StoreDelivery[]> { return this.http.get<StoreDelivery[]>(`${this.base}/newsletter/campaigns/${id}/deliveries?size=200&status=${status}`); }
  createCampaign(f: StoreCampaignForm): Observable<StoreCampaign> { return this.http.post<StoreCampaign>(`${this.base}/newsletter/campaigns`, f); }
  updateCampaign(id: number, f: StoreCampaignForm): Observable<StoreCampaign> { return this.http.put<StoreCampaign>(`${this.base}/newsletter/campaigns/${id}`, f); }
  deleteCampaign(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/newsletter/campaigns/${id}`); }
  testCampaign(id: number, email: string): Observable<{ ok: boolean }> { return this.http.post<{ ok: boolean }>(`${this.base}/newsletter/campaigns/${id}/test`, { email }); }
  sendCampaign(id: number): Observable<StoreCampaign> { return this.http.post<StoreCampaign>(`${this.base}/newsletter/campaigns/${id}/send`, {}); }
  cancelCampaign(id: number): Observable<StoreCampaign> { return this.http.post<StoreCampaign>(`${this.base}/newsletter/campaigns/${id}/cancel`, {}); }
  retryCampaign(id: number): Observable<StoreCampaign> { return this.http.post<StoreCampaign>(`${this.base}/newsletter/campaigns/${id}/retry`, {}); }
  zones(): Observable<StoreZone[]> { return this.http.get<StoreZone[]>(`${this.base}/zones`); }
  saveZone(id: number | null, body: Partial<StoreZone>): Observable<StoreZone> {
    return id == null ? this.http.post<StoreZone>(`${this.base}/zones`, body) : this.http.put<StoreZone>(`${this.base}/zones/${id}`, body);
  }
  deleteZone(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/zones/${id}`); }
}
