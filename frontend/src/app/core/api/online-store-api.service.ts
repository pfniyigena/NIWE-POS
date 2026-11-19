import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

export interface ShopZone { id: number; name: string; fee: number; minOrder: number }
export interface ShopProduct { id: number; name: string; description: string | null; price: number; category: string | null; imageUrl: string | null; stock: 'IN_STOCK' | 'LOW' | 'SOLD_OUT';
  /** The normal price when staff set a lower online price (shown struck through); 0 stars and 0 reviews when nobody reviewed it. */
  wasPrice: number | null; rating: number; ratingCount: number;
  /** A product with sizes, colours or models: how many options it has (0 = a plain product); priceFrom = the options do not all cost the same, so the card says "From". */
  variants?: number; priceFrom?: boolean }
/** One row of the specifications table of a product page. */
export interface ProductSpec { label: string; value: string }
export interface ProductReview { productName: string; reviewerName: string; rating: number; text: string | null; at: string }
/** The product page: images are the gallery addresses in order (the product's own picture first). stars[0] counts 1-star reviews, stars[4] 5-star ones. */
export interface ProductDetail {
  product: ShopProduct; brand: string | null; images: string[]; specs: ProductSpec[];
  rating: { average: number; count: number; stars: number[] };
  reviews: ProductReview[]; related: ShopProduct[]; bought: ShopProduct[];
  /** Only for a product with options: the choices (Colour: Red, Blue ...), every option with its own price, stock and pictures, and the one to show first. */
  options?: { name: string; values: string[] }[]; variants?: ProductVariant[]; selectedVariantId?: number | null;
}
export interface ProductVariant { id: number; label: string; values: string[]; price: number; wasPrice: number | null; stock: 'IN_STOCK' | 'LOW' | 'SOLD_OUT'; sku: string | null; pictures: string[] }
/** What staff edit for a product on the shop: extra pictures and specification rows. */
export interface ListingImage { id: number; fileName: string; sizeBytes: number; sortOrder: number; url: string }
export interface ListingDetails { images: ListingImage[]; specs: ProductSpec[]; hasMainImage: boolean; maxImages: number }
/** A provider the customer may pay with (several can be switched on: the customer chooses). */
export interface ShopPayProvider { code: string; label: string; available: boolean; methods: string[] }
export interface Shop {
  name: string; intro: string | null; phone: string | null; pickupAddress: string | null; open: boolean; message: string | null;
  pickup: boolean; delivery: boolean; cash: boolean; minOrder: number; freeDeliveryOver: number | null;
  zones: ShopZone[]; categories: string[]; payOnline: boolean; payMethods: string[]; payProviders: ShopPayProvider[]; accounts: boolean; currency: string; coupons: boolean;
  /** SMS or WHATSAPP when the shop texts customers, else null: the tick box and the phone sign-in only show then. */
  textChannel: string | null;
  /** True when the shop keeps carts to remind customers who typed an e-mail address: the notice under the e-mail box shows then. */
  cartReminders: boolean;
  /** True when signed-in customers earn and spend loyalty points; loyaltyMaxPercent is the share of the goods points may pay. */
  loyalty: boolean;
  loyaltyMaxPercent: number;
  /** The brands of the listed products, and the categories with a picture and a count (tiles of the front page). */
  brands: string[]; categoryTiles: ShopTile[];
  /** The shop's WhatsApp number as digits (for a wa.me link), or null when none is set. */
  whatsapp: string | null;
  /** The front page's wording, written by staff in Shop settings > Front page. */
  page: ShopPageContent;
}
export interface ShopTile { name: string; imageUrl: string | null; count: number }
/** What the visitor chose in the search box and the filter bar. */
export interface ShopFilter { brand: string; minPrice: number | null; maxPrice: number | null; inStock: boolean; minRating: number; sort: string }
export interface ShopSuggest { products: ShopProduct[]; categories: string[]; brands: string[] }
export interface ShopLink { label: string; url: string }
export interface ShopPageContent {
  announcementText: string | null; announcementLink: string | null; heroTitle: string | null; heroSubtitle: string | null; heroButtonText: string | null;
  heroButtonLink: string | null; heroImageUrl: string | null; aboutText: string | null; contactEmail: string | null; openingHours: string | null;
  footerLinks: ShopLink[]; socialLinks: ShopLink[];
  /** The shop's colour (#rrggbb) and the estimated delivery time in days; empty when staff set none. */
  brandColor: string | null; deliveryDaysMin: number | null; deliveryDaysMax: number | null; autoSlideRows: boolean;
}
export interface ShopReview { productName: string; reviewerName: string; rating: number; text: string; at: string }
/** The rows of the front page, chosen by staff, and the best approved reviews. */
export interface ShopHome {
  featured: ShopProduct[]; bestSellers: ShopProduct[]; offers: ShopProduct[]; newArrivals: ShopProduct[];
  banners: string[]; reviews: ShopReview[]; ratingAverage: number | null; ratingCount: number; ratingStars: number[];
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
  /** The provider chosen when the shop offers several (MTN_MOMO, AIRTEL_MONEY); null for cash. */
  paymentProvider?: string | null;
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
  /** The invoice number once the order is completed (its PDF can then be downloaded), else null. */
  invoiceNumber: string | null;
  /** The driver bringing the order, once it is out for delivery. */
  driverName?: string | null; driverPhone?: string | null;
  /** The customer can confirm the goods (they are out for delivery or ready), and when and how it was confirmed. */
  canConfirm?: boolean; receivedAt?: string | null; receivedVia?: string | null;
}
/** The delivery code to give to the driver (while the order is out or ready), or the reception details after. */
export interface ShopReceipt { status: string; canConfirm: boolean; code: string | null; receivedAt: string | null; receivedVia: string | null; signedName: string | null }
/** A customer account on the public shop (no passwords: a code by e-mail or text message proves the address or number). An account opened with a phone number has no e-mail. */
export interface AccountProfile { email: string | null; phone: string | null; name: string | null; address: string | null; language: string; phoneVerified: boolean; points: number }
/** A saved delivery address of the signed-in customer; zoneId is the delivery area it belongs to, when the shop has areas. */
export interface ShopAddress { id: number; label: string; zoneId: number | null; address: string; isDefault: boolean }
export interface AccountSession { token: string; profile: AccountProfile }
export interface AccountOrder { code: string; status: string; deliveryMethod: string; total: number; placedAt: string; items: number; paymentStatus: string; invoiceNumber: string | null; receiptCode: string | null; canConfirm: boolean }
/** A return of one of the customer's orders (My returns). */
export interface AccountReturn { orderCode: string; data: { code: string; status: string; reason: string; staffNote: string | null; refundAmount: number; refundMethod: string | null; refundReference: string | null; requestedAt: string; decidedAt: string | null; refundedAt: string | null; lines: { name: string; quantity: number; amount: number }[] } }
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
  driverId: number | null; driverName: string | null; driverPhone: string | null;
  receivedAt: string | null; receivedVia: string | null; receivedBy: string | null; signedName: string | null; hasSignature: boolean; hasCode: boolean;
  lines: { productId: number; name: string; quantity: number; unitPrice: number; lineTotal: number }[];
  events: { status: string; note: string | null; by: string | null; at: string }[];
}
/** A person who delivers orders (a simple list kept in the shop settings, not an employee record). openOrders = orders still to deliver. */
export interface Driver { id: number; name: string; phone: string | null; notes: string | null; active: boolean; openOrders: number }
/** A customer waiting for a sold-out product (staff view), and a product customers are waiting for. */
export interface StockAlert { id: number; productId: number; productName: string; email: string | null; phone: string | null; language: string; status: string; createdAt: string; notifiedAt: string | null; notifiedBy: string | null }
export interface StockDemand { productId: number; productName: string; waiting: number; stock: string }
export interface StoreSettings {
  enabled: boolean; paused: boolean; pauseMessage: string | null; shopName: string | null; introText: string | null; contactPhone: string | null;
  pickupAddress: string | null; pickupEnabled: boolean; deliveryEnabled: boolean; codEnabled: boolean; stockBuffer: number; minOrder: number;
  freeDeliveryOver: number | null; maxPendingPerPhone: number; maxPerIpPerHour: number; deliveryProductId: number | null; deliveryProductName: string | null; notifyEmail: string | null; unansweredAlertMinutes: number;
  onlinePaymentsEnabled: boolean; paymentProvider: string | null; paymentExpiryMinutes: number; accountsEnabled: boolean;
  textProvider: string | null; textChannel: string; textCountryCode: string; textDailyLimit: number; notifyPhone: string | null; returnWindowDays: number;
  cartRemindersEnabled: boolean;
  loyaltyEnabled: boolean; loyaltyPointsPer100: number; loyaltyMaxPercent: number; whatsappNumber: string | null;
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
export interface PendingCounts { pending: number; overdue: number; alertMinutes: number; awaitingPayment?: number; refundsDue?: number; paymentsToReview?: number; returnsRequested?: number; returnsToRefund?: number; reviewsPending?: number; messagesNew?: number; open?: number; completed?: number; rejected?: number; cancelled?: number; all?: number }
/** Information pages (Terms, Privacy, Returns, Delivery) and the contact form. */
export interface InfoLink { slug: string; title: string }
export interface InfoPage { slug: string; title: string; body: string }
export interface InfoPageAdmin { slug: string; title: string; body: string; published: boolean; edited: boolean }
export interface ContactForm { name: string; email: string; phone: string; subject: string; message: string; orderCode: string; language: string; website: string }
export interface ContactMessage { id: number; name: string; email: string | null; phone: string | null; subject: string | null; message: string; orderCode: string | null; language: string;
  status: 'NEW' | 'ANSWERED'; note: string | null; handledBy: string | null; handledAt: string | null; createdAt: string; reply: string | null; repliedAt: string | null }
export interface StoreListing {
  id: number; productId: number; productName: string; sku: string | null; category: string | null; unitPrice: number; listed: boolean;
  webTitle: string | null; webDescription: string | null; onlinePrice: number | null; sortOrder: number; hasImage: boolean; stock: string;
  featured: boolean; bestSeller: boolean; specialOffer: boolean; newArrival: boolean; stockInfo: StoreStockInfo;
  /** The tax rate (percent) the shop adds on top of the price, or null when there is none or it is already in the price. */
  taxOnTopPercent: number | null;
  /** Variants: an option has variantParentId (its product) and variantLabel; a product with options has variantOptions and variantCount; variantOff = this option is not sold online. */
  variantParentId: number | null; variantLabel: string | null; variantOptions: string | null; variantOff: boolean; variantCount: number;
}
/** Why a product shows the stock it shows. tracked false = stock is not tracked for it, so the shop always shows it as in stock. */
export interface StoreStockInfo { tracked: boolean; inWarehouses: number | null; reserved: number | null; buffer: number; sellable: number | null }
/** Staff's wording of the front page (Shop settings > Front page). Links are one per line, written Label|address. */
export interface StorePageContent {
  announcementText: string | null; announcementLink: string | null; heroTitle: string | null; heroSubtitle: string | null; heroButtonText: string | null;
  heroButtonLink: string | null; heroImageUrl: string | null; aboutText: string | null; contactEmail: string | null; openingHours: string | null;
  footerLinks: string | null; socialLinks: string | null;
  brandColor: string | null; deliveryDaysMin: number | null; deliveryDaysMax: number | null; autoSlideRows: boolean;
}
export interface StoreBanner { id: number; fileName: string; sizeBytes: number; sortOrder: number; url: string }
/** Advertising (v1.43.0): the businesses that pay for ads, their ads, the ads the shop shows today and the report. */
export interface StoreAdvertiser { id: number; name: string; contactName: string | null; email: string | null; phone: string | null; notes: string | null; active: boolean; adCount: number }
export interface StoreAdvertiserForm { name: string; contactName: string; email: string; phone: string; notes: string; active: boolean }
export interface StoreAd { id: number; advertiserId: number; advertiserName: string; title: string; placements: string[]; linkUrl: string; altText: string | null; startsOn: string | null; endsOn: string | null;
  active: boolean; fee: number | null; feeNote: string | null; feePaid: boolean; sortOrder: number; fileName: string; sizeBytes: number; imageUrl: string; showingNow: boolean; views: number; clicks: number }
export interface StoreAdForm { advertiserId: number | null; title: string; placements: string[]; linkUrl: string; altText: string; startsOn: string; endsOn: string; active: boolean; fee: string; feeNote: string; feePaid: boolean }
export interface ShopAd { id: number; title: string; altText: string | null; imageUrl: string; clickUrl: string }
export interface AdReportRow { adId: number; advertiserId: number; advertiserName: string; title: string; views: number; clicks: number; clickRate: number;
  byPlacement: { placement: string; views: number; clicks: number }[]; fee: number | null; feeNote: string | null; feePaid: boolean }
export interface AdReport { from: string; to: string; views: number; clicks: number; clickRate: number; rows: AdReportRow[] }
export interface StoreReview {
  id: number; orderCode: string | null; productId: number; productName: string; reviewerName: string; rating: number; text: string | null;
  status: 'PENDING' | 'APPROVED' | 'HIDDEN'; createdAt: string; moderatedBy: string | null; ratingCounted: boolean;
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
  products(page: number, size: number, q: string, category: string, f?: ShopFilter): Observable<ShopPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) params = params.set('q', q.trim());
    if (category) params = params.set('category', category);
    if (f) {
      if (f.brand) params = params.set('brand', f.brand);
      if (f.minPrice !== null && f.minPrice !== undefined && `${f.minPrice}` !== '') params = params.set('minPrice', f.minPrice);
      if (f.maxPrice !== null && f.maxPrice !== undefined && `${f.maxPrice}` !== '') params = params.set('maxPrice', f.maxPrice);
      if (f.inStock) params = params.set('inStock', true);
      if (f.minRating > 0) params = params.set('minRating', f.minRating);
      if (f.sort) params = params.set('sort', f.sort);
    }
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
  /** A rating (stars) and optional review (words) of one product of a completed order; the stars count at once, the words wait for staff. The phone number is needed unless the customer is signed in. */
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
  payAgain(code: string, payment: string, payPhone: string | null, provider: string | null = null): Observable<ShopTrack> {
    return this.http.post<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/pay`, { payment, payPhone, provider });
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
  // ---- customers: saved addresses, wishlist and the invoice
  addressList(): Observable<ShopAddress[]> { return this.http.get<ShopAddress[]>(`${this.pub}/account/addresses`, { headers: this.shopHeaders() }); }
  addressAdd(b: { label: string; zoneId: number | null; address: string; isDefault: boolean }): Observable<ShopAddress[]> { return this.http.post<ShopAddress[]>(`${this.pub}/account/addresses`, b, { headers: this.shopHeaders() }); }
  addressUpdate(id: number, b: { label: string; zoneId: number | null; address: string; isDefault: boolean }): Observable<ShopAddress[]> { return this.http.put<ShopAddress[]>(`${this.pub}/account/addresses/${id}`, b, { headers: this.shopHeaders() }); }
  addressDelete(id: number): Observable<ShopAddress[]> { return this.http.delete<ShopAddress[]>(`${this.pub}/account/addresses/${id}`, { headers: this.shopHeaders() }); }
  /** The product ids on the signed-in customer's wishlist, newest first. */
  wishIds(): Observable<number[]> { return this.http.get<number[]>(`${this.pub}/account/wishlist`, { headers: this.shopHeaders() }); }
  wishAdd(id: number): Observable<number[]> { return this.http.put<number[]>(`${this.pub}/account/wishlist/${id}`, {}, { headers: this.shopHeaders() }); }
  wishRemove(id: number): Observable<number[]> { return this.http.delete<number[]>(`${this.pub}/account/wishlist/${id}`, { headers: this.shopHeaders() }); }
  wishMerge(ids: number[]): Observable<number[]> { return this.http.post<number[]>(`${this.pub}/account/wishlist/merge`, { ids }, { headers: this.shopHeaders() }); }
  /** The products behind these ids, as the shop has them today (listed ones only), for the wishlist page. */
  wished(ids: number[]): Observable<ShopProduct[]> { return this.http.get<ShopProduct[]>(`${this.pub}/wishlist`, { params: { ids: ids.join(',') } }); }
  /** The invoice of a completed order (PDF). The phone number of the order is needed unless the customer is signed in and the order is theirs. */
  invoicePdf(code: string, phone: string | null): Observable<Blob> {
    return this.http.post(`${this.pub}/orders/${encodeURIComponent(code)}/invoice`, { phone }, { headers: this.shopHeaders(), responseType: 'blob' });
  }
  /** The delivery code (while the order is out or ready) or the reception details: needs the phone number of the order unless signed in. */
  receiptInfo(code: string, phone: string | null): Observable<ShopReceipt> { return this.http.post<ShopReceipt>(`${this.pub}/orders/${encodeURIComponent(code)}/receipt`, { phone }, { headers: this.shopHeaders() }); }
  /** "I received my order": completes the order. signature is a PNG data URL, or null. */
  confirmReceived(code: string, body: { phone: string | null; signature: string | null; signedName: string | null }): Observable<ShopTrack> { return this.http.post<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/received`, body, { headers: this.shopHeaders() }); }
  accountOrders(): Observable<AccountOrder[]> { return this.http.get<AccountOrder[]>(`${this.pub}/account/orders`, { headers: this.shopHeaders() }); }
  accountReturns(): Observable<AccountReturn[]> { return this.http.get<AccountReturn[]>(`${this.pub}/account/returns`, { headers: this.shopHeaders() }); }
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
  /** driverId: 0 = delivery orders with no driver yet; another number = that driver's orders; null = all. */
  page(f: ListFilter, page: number, size: number, driverId: number | null = null): Observable<PageResult<StoreOrder>> {
    return pagedList<StoreOrder>(this.http, `${this.base}/orders`, f, page, size, driverId === null ? {} : { driverId: String(driverId) });
  }
  // ---- back-in-stock alerts
  /** SAVED = the shop will tell the customer; IN_STOCK = it can be bought now. */
  requestStockAlert(body: { productId: number; email: string | null; phone: string | null; language: string; website?: string }): Observable<{ state: string }> {
    return this.http.post<{ state: string }>(`${this.pub}/stock-alerts`, body, { headers: this.shopHeaders() });
  }
  stockDemand(): Observable<StockDemand[]> { return this.http.get<StockDemand[]>(`${this.base}/stock-alerts/demand`); }
  /** The filter's status (WAITING, NOTIFIED, FAILED or empty) and q apply; productId narrows to one product. */
  stockAlerts(f: ListFilter, page: number, size: number, productId: number | null): Observable<PageResult<StockAlert>> {
    return pagedList<StockAlert>(this.http, `${this.base}/stock-alerts`, f, page, size, productId === null ? {} : { productId: String(productId) });
  }
  stockAlertTold(id: number): Observable<StockAlert> { return this.http.post<StockAlert>(`${this.base}/stock-alerts/${id}/told`, {}); }
  stockAlertDelete(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/stock-alerts/${id}`); }
  stockAlertsNotify(): Observable<{ told: number }> { return this.http.post<{ told: number }>(`${this.base}/stock-alerts/notify`, {}); }
  // ---- drivers and delivery papers
  drivers(): Observable<Driver[]> { return this.http.get<Driver[]>(`${this.base}/drivers`); }
  saveDriver(id: number | null, body: { name: string; phone: string | null; notes: string | null; active: boolean }): Observable<Driver> {
    return id ? this.http.put<Driver>(`${this.base}/drivers/${id}`, body) : this.http.post<Driver>(`${this.base}/drivers`, body);
  }
  deleteDriver(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/drivers/${id}`); }
  assignDriver(id: number, driverId: number | null): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/driver`, { driverId }); }
  deliverySlip(id: number): Observable<Blob> { return this.http.get(`${this.base}/orders/${id}/delivery-slip`, { responseType: 'blob' }); }
  deliveryRun(driverId: number): Observable<Blob> { return this.http.get(`${this.base}/delivery-run`, { params: { driverId }, responseType: 'blob' }); }
  // ---- information pages and the contact form
  infoLinks(lang: string): Observable<InfoLink[]> { return this.http.get<InfoLink[]>(`${this.pub}/info`, { params: { lang } }); }
  infoPage(slug: string, lang: string): Observable<InfoPage> { return this.http.get<InfoPage>(`${this.pub}/info/${slug}`, { params: { lang } }); }
  sendContact(f: ContactForm): Observable<{ ok: boolean }> { return this.http.post<{ ok: boolean }>(`${this.pub}/contact`, f); }
  adminInfoPages(lang: string): Observable<InfoPageAdmin[]> { return this.http.get<InfoPageAdmin[]>(`${this.base}/info-pages`, { params: { lang } }); }
  saveInfoPage(slug: string, p: { title: string; body: string; published: boolean }, lang: string): Observable<InfoPageAdmin> {
    return this.http.put<InfoPageAdmin>(`${this.base}/info-pages/${slug}`, p, { params: { lang } });
  }
  restoreInfoPage(slug: string, lang: string): Observable<InfoPageAdmin> { return this.http.post<InfoPageAdmin>(`${this.base}/info-pages/${slug}/restore`, {}, { params: { lang } }); }
  messagesPage(f: ListFilter, page: number, size: number): Observable<PageResult<ContactMessage>> {
    return pagedList<ContactMessage>(this.http, `${this.base}/messages`, f, page, size, {});
  }
  messageAnswered(id: number, note: string): Observable<ContactMessage> { return this.http.post<ContactMessage>(`${this.base}/messages/${id}/answered`, { note }); }
  replyMessage(id: number, text: string): Observable<ContactMessage> { return this.http.post<ContactMessage>(`${this.base}/messages/${id}/reply`, { text }); }
  messageReopen(id: number): Observable<ContactMessage> { return this.http.post<ContactMessage>(`${this.base}/messages/${id}/reopen`, {}); }
  deleteMessage(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/messages/${id}`); }

  pendingCount(): Observable<PendingCounts> { return this.http.get<PendingCounts>(`${this.base}/orders/pending-count`); }
  accept(id: number, overrideShortage: boolean): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/accept`, { overrideShortage }); }
  reject(id: number, reason: string): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/reject`, { reason }); }
  pack(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/pack`, {}); }
  outForDelivery(id: number, driverId: number | null = null): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/out-for-delivery`, { driverId }); }
  readyForPickup(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/ready-for-pickup`, {}); }
  /** The goods are in the customer's hands: the delivery code the customer gave and/or the customer's signature (a PNG data URL). Completes the order. */
  receive(id: number, body: { code?: string | null; signature?: string | null; signedName?: string | null; locationId?: number | null }): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/receive`, body); }
  orderSignature(id: number): Observable<Blob> { return this.http.get(`${this.base}/orders/${id}/signature`, { responseType: 'blob' }); }
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
  listingDetails(productId: number): Observable<ListingDetails> { return this.http.get<ListingDetails>(`${this.base}/listings/${productId}/details`); }
  saveListingSpecs(productId: number, specs: ProductSpec[]): Observable<ListingDetails> { return this.http.put<ListingDetails>(`${this.base}/listings/${productId}/specs`, { specs }); }
  addListingImage(productId: number, file: File): Observable<ListingDetails> {
    const f = new FormData();
    f.append('file', file);
    return this.http.post<ListingDetails>(`${this.base}/listings/${productId}/images`, f);
  }
  deleteListingImage(id: number): Observable<ListingDetails> { return this.http.delete<ListingDetails>(`${this.base}/listing-images/${id}`); }
  moveListingImage(id: number, direction: 'up' | 'down'): Observable<ListingDetails> { return this.http.post<ListingDetails>(`${this.base}/listing-images/${id}/move?direction=${direction}`, {}); }
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
  advertisers(): Observable<StoreAdvertiser[]> { return this.http.get<StoreAdvertiser[]>(`${this.base}/advertisers`); }
  saveAdvertiser(id: number | null, f: StoreAdvertiserForm): Observable<StoreAdvertiser> { return this.http.post<StoreAdvertiser>(id ? `${this.base}/advertisers/${id}` : `${this.base}/advertisers`, f); }
  deleteAdvertiser(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/advertisers/${id}`); }
  ads(): Observable<StoreAd[]> { return this.http.get<StoreAd[]>(`${this.base}/ads`); }
  /** The picture is sent with the other fields as a form; on a change, leave file out to keep the picture. */
  saveAd(id: number | null, f: StoreAdForm, file: File | null): Observable<StoreAd> {
    const form = new FormData();
    form.append('advertiserId', String(f.advertiserId ?? ''));
    form.append('title', f.title);
    form.append('placements', f.placements.join(','));
    form.append('linkUrl', f.linkUrl);
    form.append('altText', f.altText);
    form.append('startsOn', f.startsOn);
    form.append('endsOn', f.endsOn);
    form.append('active', String(f.active));
    form.append('fee', f.fee);
    form.append('feeNote', f.feeNote);
    form.append('feePaid', String(f.feePaid));
    if (file) form.append('file', file);
    return this.http.post<StoreAd>(id ? `${this.base}/ads/${id}` : `${this.base}/ads`, form);
  }
  deleteAd(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/ads/${id}`); }
  adReport(from: string, to: string, advertiserId: number | null): Observable<AdReport> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (advertiserId) params = params.set('advertiserId', String(advertiserId));
    return this.http.get<AdReport>(`${this.base}/ads/report`, { params });
  }
  /** The ads the shop may show today, by space (HOME_TOP, PRODUCT, ORDER). */
  shopAds(): Observable<Record<string, ShopAd[]>> { return this.http.get<Record<string, ShopAd[]>>(`${this.pub}/ads`); }
  /** Tell the shop these ads were shown in this space (counted once per visitor and half hour). */
  /** Suggestions while typing in the search box. */
  suggest(q: string): Observable<ShopSuggest> { return this.http.get<ShopSuggest>(`${this.pub}/suggest`, { params: new HttpParams().set('q', q) }); }
  /** The products with these ids (from the visitor's own browser), for "recently viewed". */
  recent(ids: number[]): Observable<ShopProduct[]> { return this.http.get<ShopProduct[]>(`${this.pub}/recent`, { params: new HttpParams().set('ids', ids.join(',')) }); }
  productDetail(id: number): Observable<ProductDetail> { return this.http.get<ProductDetail>(`${this.pub}/products/${id}`); }
  adSeen(placement: string, ids: number[]): Observable<void> { return this.http.post<void>(`${this.pub}/ads/seen`, { placement, ids }); }
  reviewsPage(f: ListFilter, page: number, size: number): Observable<PageResult<StoreReview>> {
    return pagedList<StoreReview>(this.http, `${this.base}/reviews`, f, page, size, {});
  }
  approveReview(id: number): Observable<StoreReview> { return this.http.post<StoreReview>(`${this.base}/reviews/${id}/approve`, {}); }
  ratingOff(id: number): Observable<StoreReview> { return this.http.post<StoreReview>(`${this.base}/reviews/${id}/rating-off`, {}); }
  ratingOn(id: number): Observable<StoreReview> { return this.http.post<StoreReview>(`${this.base}/reviews/${id}/rating-on`, {}); }
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
