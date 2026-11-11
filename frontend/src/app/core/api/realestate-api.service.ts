import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PageParams, PageResult, getPaged } from './paged';
import { Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { API_BASE } from '../constants';
import { InstallmentFrequency, InstallmentPlanDetail, InstallmentPreview, InterestMethod } from './installment-api.service';

import { T } from '../i18n/t';
export type PropertyType = 'HOUSE' | 'APARTMENT' | 'VILLA' | 'LAND' | 'COMMERCIAL' | 'OFFICE' | 'WAREHOUSE' | 'OTHER';
export type ListingPurpose = 'SALE' | 'RENT' | 'BOTH';
export type PropertyStatus = 'DRAFT' | 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'LET' | 'WITHDRAWN';
export type LeadStatus = 'NEW' | 'CONTACTED' | 'VIEWING' | 'NEGOTIATING' | 'WON' | 'LOST';
export type LeadInterest = 'BUY' | 'RENT' | 'EITHER';
export type LeadSource = 'WALK_IN' | 'PHONE' | 'WEBSITE' | 'REFERRAL' | 'SOCIAL' | 'SIGNBOARD' | 'OTHER';
export type ViewingStatus = 'SCHEDULED' | 'DONE' | 'CANCELLED' | 'NO_SHOW';
export type NoteKind = 'NOTE' | 'CALL' | 'MEETING' | 'EMAIL' | 'WHATSAPP';
export type DealStage = 'OFFER' | 'RESERVED' | 'AGREEMENT' | 'COMPLETED' | 'CANCELLED';
export type MilestoneKind = 'DEPOSIT' | 'INSTALMENT' | 'BALANCE' | 'OTHER';
export type CommissionBasis = 'SHARE_PERCENT' | 'FIXED';
export type CommissionStatus = 'PLANNED' | 'POSTED' | 'CANCELLED';
export type PayeeKind = 'STAFF' | 'EXTERNAL';

export const PROPERTY_TYPES: { value: PropertyType; label: string }[] = [
  { value: 'HOUSE', get label() { return T('House'); } }, { value: 'APARTMENT', get label() { return T('Apartment'); } }, { value: 'VILLA', get label() { return T('Villa'); } }, { value: 'LAND', get label() { return T('Land / plot'); } },
  { value: 'COMMERCIAL', get label() { return T('Commercial'); } }, { value: 'OFFICE', get label() { return T('Office'); } }, { value: 'WAREHOUSE', get label() { return T('Warehouse'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export const PURPOSE_LABEL: Record<ListingPurpose, string> = { SALE: 'For sale', RENT: 'For rent', BOTH: 'Sale or rent' };
export const PROPERTY_STATUS_LABEL: Record<PropertyStatus, string> = { DRAFT: 'Draft', AVAILABLE: 'Available', RESERVED: 'Reserved', SOLD: 'Sold', LET: 'Let', WITHDRAWN: 'Withdrawn' };
export const LEAD_STATUS_LABEL: Record<LeadStatus, string> = { NEW: 'New', CONTACTED: 'Contacted', VIEWING: 'Viewing', NEGOTIATING: 'Negotiating', WON: 'Won', LOST: 'Lost' };
export const INTEREST_LABEL: Record<LeadInterest, string> = { BUY: 'Buy', RENT: 'Rent', EITHER: 'Buy or rent' };
export const SOURCE_LABEL: Record<LeadSource, string> = { WALK_IN: 'Walk-in', PHONE: 'Phone', WEBSITE: 'Website', REFERRAL: 'Referral', SOCIAL: 'Social media', SIGNBOARD: 'Signboard', OTHER: 'Other' };
export const VIEWING_STATUS_LABEL: Record<ViewingStatus, string> = { SCHEDULED: 'Scheduled', DONE: 'Done', CANCELLED: 'Cancelled', NO_SHOW: 'No-show' };
export const NOTE_KIND_LABEL: Record<NoteKind, string> = { NOTE: 'Note', CALL: 'Call', MEETING: 'Meeting', EMAIL: 'E-mail', WHATSAPP: 'WhatsApp' };

export const DEAL_STAGE_LABEL: Record<DealStage, string> = { OFFER: 'Offer', RESERVED: 'Reserved', AGREEMENT: 'Agreement signed', COMPLETED: 'Completed', CANCELLED: 'Cancelled' };
export const MILESTONE_KIND_LABEL: Record<MilestoneKind, string> = { DEPOSIT: 'Deposit', INSTALMENT: 'Instalment', BALANCE: 'Balance', OTHER: 'Other' };
export const MILESTONE_STATUS_LABEL: Record<string, string> = { NOT_INVOICED: 'Not invoiced', DRAFT: 'Invoice drafted', ISSUED: 'Invoiced', PAID: 'Paid', VOID: 'Invoice voided' };

export function typeLabel(t: string): string { return PROPERTY_TYPES.find((x) => x.value === t)?.label ?? t; }

export interface Option { id: number; name: string; }

export type DocSubject = 'PROPERTY' | 'PROJECT' | 'UNIT' | 'DEAL' | 'UNIT_SALE' | 'LEAD' | 'VALUATION';
export type ReDocCategory = 'TITLE_DEED' | 'SALE_AGREEMENT' | 'SURVEY' | 'PERMIT' | 'ID_DOCUMENT' | 'VALUATION_REPORT' | 'FLOOR_PLAN' | 'OTHER';
export type ValuationPurpose = 'SALE' | 'MORTGAGE' | 'INSURANCE' | 'REVIEW' | 'OTHER';
export type ValuationMethod = 'COMPARABLE' | 'INCOME' | 'COST' | 'OTHER';
export const DOC_CATEGORIES: { value: ReDocCategory; label: string }[] = [
  { value: 'TITLE_DEED', get label() { return T('Title deed'); } }, { value: 'SALE_AGREEMENT', get label() { return T('Sale agreement'); } }, { value: 'SURVEY', get label() { return T('Survey / plan'); } }, { value: 'PERMIT', get label() { return T('Permit / licence'); } },
  { value: 'ID_DOCUMENT', get label() { return T('ID document'); } }, { value: 'VALUATION_REPORT', get label() { return T('Valuation report'); } }, { value: 'FLOOR_PLAN', get label() { return T('Floor plan'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export const VALUATION_PURPOSES: { value: ValuationPurpose; label: string }[] = [
  { value: 'SALE', get label() { return T('Sale'); } }, { value: 'MORTGAGE', get label() { return T('Mortgage'); } }, { value: 'INSURANCE', get label() { return T('Insurance'); } }, { value: 'REVIEW', get label() { return T('Review'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export const VALUATION_METHODS: { value: ValuationMethod; label: string }[] = [
  { value: 'COMPARABLE', get label() { return T('Comparable sales'); } }, { value: 'INCOME', get label() { return T('Income'); } }, { value: 'COST', get label() { return T('Cost'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export function docCategoryLabel(c: string): string { return DOC_CATEGORIES.find((x) => x.value === c)?.label ?? c; }

export interface ReDocument {
  id: number; subjectType: DocSubject; subjectId: number; subjectLabel: string | null; name: string; category: ReDocCategory; description: string | null; expiryDate: string | null;
  expiryState: 'NONE' | 'OK' | 'SOON' | 'EXPIRED'; daysToExpiry: number | null; fileName: string; contentType: string; sizeBytes: number; uploadedByName: string | null; createdAt: string;
}
export interface DocumentPayload { name?: string | null; category?: ReDocCategory | null; description?: string | null; expiryDate?: string | null; clearExpiry?: boolean; }
export interface ValuationDto {
  id: number; propertyId: number; propertyCode: string | null; propertyTitle: string | null; valuationDate: string; valuerName: string; valuerFirm: string | null; purpose: ValuationPurpose;
  method: ValuationMethod; marketValue: number; forcedSaleValue: number | null; monthlyRent: number | null; validUntil: string | null; expired: boolean; current: boolean;
  askingPrice: number | null; vsAskingPercent: number | null; notes: string | null; documents: number; createdByName: string | null; createdAt: string;
}
export interface ValuationPayload {
  propertyId?: number | null; valuationDate?: string | null; valuerName?: string | null; valuerFirm?: string | null; purpose?: ValuationPurpose | null; method?: ValuationMethod | null;
  marketValue?: number | null; forcedSaleValue?: number | null; monthlyRent?: number | null; validUntil?: string | null; clearValidUntil?: boolean; notes?: string | null;
}

export interface ReSettings {
  areas: string[]; viewingMinutes: number; staleLeadDays: number; websiteEnabled: boolean; websiteTitle: string | null; websiteIntro: string | null;
  websitePhone: string | null; websiteEmail: string | null; enquiriesPerPhonePerDay: number; defaultCommissionPercent: number | null; commissionAccountId: number | null;
  followUpEmailEnabled: boolean; followUpLastRun: string | null; followUpLastSummary: string | null; mailConfigured: boolean;
  viewingReminderEnabled: boolean; viewingReminderHours: number; offerTerms: string | null; reservationTerms: string | null; agreementTerms: string | null;
}
export interface ReSettingsPayload {
  areas: string; viewingMinutes: number; staleLeadDays: number; websiteEnabled: boolean; websiteTitle: string; websiteIntro: string; websitePhone: string; websiteEmail: string;
  enquiriesPerPhonePerDay: number; defaultCommissionPercent: number; commissionAccountId: number; followUpEmailEnabled?: boolean; viewingReminderEnabled?: boolean; viewingReminderHours?: number;
  offerTerms?: string; reservationTerms?: string; agreementTerms?: string;
}

export interface PropertyRow {
  id: number; code: string; title: string; type: PropertyType; purpose: ListingPurpose; status: PropertyStatus; area: string | null; bedrooms: number | null; bathrooms: number | null;
  sizeSqm: number | null; salePrice: number | null; rentPrice: number | null; agentName: string | null; coverPhotoId: number | null; photos: number; listedDate: string | null;
  daysListed: number; featured: boolean; publicListing: boolean; openLeads: number; upcomingViewings: number;
}
export interface PhotoDto { id: number; fileName: string; sizeBytes: number; cover: boolean; }
export interface PropertyDto {
  id: number; code: string; title: string; type: PropertyType; purpose: ListingPurpose; status: PropertyStatus; description: string | null; area: string | null; address: string | null;
  titleReference: string | null; latitude: number | null; longitude: number | null; bedrooms: number | null; bathrooms: number | null; parkingSpaces: number | null;
  sizeSqm: number | null; plotSqm: number | null; yearBuilt: number | null; furnished: boolean; features: string | null; salePrice: number | null; rentPrice: number | null;
  negotiable: boolean; featured: boolean; publicListing: boolean; ownerName: string | null; ownerPhone: string | null; ownerEmail: string | null; agentUserId: number | null; agentName: string | null;
  listedDate: string | null; createdByName: string | null; createdAt: string; photos: PhotoDto[]; viewings: ViewingDto[]; leads: LeadRow[]; canManage: boolean;
}
export interface PropertyPayload {
  title: string; type: PropertyType; purpose: ListingPurpose; description?: string | null; area?: string | null; address?: string | null; titleReference?: string | null;
  latitude?: number | null; longitude?: number | null; bedrooms?: number | null; bathrooms?: number | null; parkingSpaces?: number | null; sizeSqm?: number | null;
  plotSqm?: number | null; yearBuilt?: number | null; furnished?: boolean; features?: string | null; salePrice?: number | null; rentPrice?: number | null; negotiable?: boolean;
  featured?: boolean; publicListing?: boolean; ownerName?: string | null; ownerPhone?: string | null; ownerEmail?: string | null; agentUserId?: number | null;
}
export interface PropertyFilter extends PageParams {
  q?: string; status?: string; type?: string; purpose?: string; agentId?: number | null; area?: string; minPrice?: number | null; maxPrice?: number | null; bedrooms?: number | null;
}

export interface LeadRow {
  id: number; name: string; phone: string | null; email: string | null; interest: LeadInterest; status: LeadStatus; source: LeadSource; budgetMin: number | null; budgetMax: number | null;
  preferredArea: string | null; propertyId: number | null; propertyCode: string | null; assignedName: string | null; assignedUserId: number | null; nextFollowUp: string | null;
  followUpOverdue: boolean; stale: boolean; lastContactAt: string | null; createdAt: string; viewings: number;
}
export interface NoteDto { id: number; kind: NoteKind; text: string; createdByName: string; createdAt: string; }
export interface ReminderLine { viewingId: number; propertyCode: string | null; propertyTitle: string | null; leadName: string | null; startAt: string; clientEmail: string | null; client: string; agentName: string | null; agentEmail: string | null; agent: string; }
export interface ReminderResult { preview: boolean; mailConfigured: boolean; viewings: number; clientsSent: number; agentsSent: number; skipped: number; failed: number; summary: string; lines: ReminderLine[]; }
export interface RemindOne { status: 'SENT' | 'NO_EMAIL' | 'NOT_CONFIGURED' | 'FAILED'; email: string | null; message: string; }

/** The follow-up board: open leads by when the next call is due. */
export interface FollowUpBoard {
  agentId: number | null; agentName: string | null; all: boolean; upcomingDays: number; staleDays: number; mailEnabled: boolean;
  overdue: LeadRow[]; today: LeadRow[]; upcoming: LeadRow[]; quiet: LeadRow[];
}
/** Unit-sale collections: buyers who owe money, in three lists. */
export interface CollectionRow {
  saleId: number; saleCode: string; projectId: number; projectName: string; unitNo: string; buyerName: string; buyerPhone: string | null; buyerEmail: string | null;
  agentUserId: number | null; agentName: string | null; basis: 'PLAN' | 'INVOICE'; agreedPrice: number; paid: number; outstanding: number; overdueAmount: number;
  overdueInstallments: number; oldestDaysOverdue: number; nextDueDate: string | null; nextDueAmount: number | null; planId: number | null;
  lastNoteKind: SaleNoteKind | null; lastNoteText: string | null; lastNoteAt: string | null; promiseDate: string | null; promiseBroken: boolean;
}
export interface CollectionBoard {
  today: string; soonDays: number; overdueTotal: number; dueSoonTotal: number; unscheduledTotal: number; installmentsModuleOn: boolean; mailConfigured: boolean;
  overdue: CollectionRow[]; dueSoon: CollectionRow[]; unscheduled: CollectionRow[];
}
export type SaleNoteKind = 'CALL' | 'PROMISE' | 'NOTE' | 'REMINDER' | 'STATEMENT';
export interface SaleNote { id: number; kind: SaleNoteKind; text: string; promiseDate: string | null; createdByName: string | null; createdAt: string; }
export interface MailedBuyer { sentTo: string; message: string; }

/** Commission payouts: what each agent has earned, what has gone to the accounts and what has been paid. */
export type PayoutState = 'PLANNED' | 'TO_POST' | 'AWAITING_APPROVAL' | 'TO_PAY' | 'PAID' | 'REJECTED';
export interface ShareLine {
  dealId: number; dealCode: string; propertyCode: string | null; propertyTitle: string | null; completedDate: string | null; salePrice: number | null; commissionId: number; role: string;
  amount: number; state: PayoutState; expenseId: number | null; paidOn: string | null;
}
export interface PayeeStatement {
  key: string; payeeName: string; payeeKind: 'STAFF' | 'EXTERNAL'; payeeUserId: number | null; hasEmail: boolean; earned: number; paid: number; owed: number; toPost: number;
  awaitingApproval: number; toPay: number; planned: number; rejected: number; lines: ShareLine[];
}
export interface PayoutBoard {
  from: string | null; to: string | null; expensesModuleOn: boolean; accountSet: boolean; mailConfigured: boolean; earned: number; paid: number; owed: number; toPost: number; planned: number;
  sharesToPost: number; payeeOptions: { key: string; name: string }[]; payees: PayeeStatement[];
}
export interface PostDueResult { posted: number; failed: number; amount: number; problems: string[]; message: string; }
export interface PayoutFilter { from?: string | null; to?: string | null; payee?: string | null; openOnly?: boolean; }

/** Importing leads or properties from a spreadsheet. Row numbers are the spreadsheet's own. */
export interface ImportRow { row: number; label: string; message: string; }
export interface ImportResult {
  kind: string; dryRun: boolean; fileName: string; rows: number; ok: number; skipped: number; problems: ImportRow[]; notes: ImportRow[]; sample: string[]; columns: string[];
  ignoredColumns: string[]; message: string;
}

export interface DigestAgent { userId: number; name: string; email: string | null; overdue: number; today: number; quiet: number; status: 'SENT' | 'WOULD_SEND' | 'NO_EMAIL' | 'NOT_CONFIGURED' | 'FAILED'; }
export interface DigestResult { preview: boolean; mailConfigured: boolean; agents: number; sent: number; skipped: number; failed: number; unassigned: number; summary: string; details: DigestAgent[]; }

export interface LeadDto {
  id: number; name: string; phone: string | null; email: string | null; interest: LeadInterest; status: LeadStatus; source: LeadSource; budgetMin: number | null; budgetMax: number | null;
  preferredArea: string | null; preferredType: PropertyType | null; minBedrooms: number | null; requirements: string | null; propertyId: number | null; propertyCode: string | null;
  propertyTitle: string | null; assignedUserId: number | null; assignedName: string | null; nextFollowUp: string | null; lastContactAt: string | null; lostReason: string | null;
  customerId: number | null; createdByName: string | null; createdAt: string; notes: NoteDto[]; viewings: ViewingDto[]; canManage: boolean;
}
export interface LeadPayload {
  name: string; phone?: string | null; email?: string | null; interest: LeadInterest; source: LeadSource; budgetMin?: number | null; budgetMax?: number | null;
  preferredArea?: string | null; preferredType?: PropertyType | null; minBedrooms?: number | null; requirements?: string | null; propertyId?: number | null;
  assignedUserId?: number | null; nextFollowUp?: string | null; force?: boolean;
}
export interface LeadFilter extends PageParams { q?: string; status?: string; interest?: string; assignedId?: number | null; propertyId?: number | null; dueOnly?: boolean; openOnly?: boolean; }
export interface Match { property: PropertyRow; score: number; reasons: string[]; }

export interface ViewingDto {
  id: number; propertyId: number; propertyCode: string | null; propertyTitle: string | null; propertyArea: string | null; leadId: number; leadName: string | null; leadPhone: string | null;
  agentUserId: number | null; agentName: string | null; startAt: string; endAt: string; status: ViewingStatus; feedback: string | null; canManage: boolean; clientRemindedAt: string | null; leadHasEmail: boolean;
}
export interface ViewingPayload { propertyId: number; leadId: number; agentUserId?: number | null; startAt: string; endAt?: string | null; force?: boolean; }

export interface Count { label: string; value: number; }
export interface ReOverview {
  properties: number; available: number; reserved: number; soldOrLet: number; availableSaleValue: number; availableMonthlyRent: number; newLeadsThisMonth: number; openLeads: number;
  followUpsOverdue: number; viewingsToday: number; viewingsNext7Days: number; propertiesByStatus: Count[]; leadsByStatus: Count[]; leadsBySource: Count[];
  upcomingViewings: ViewingDto[]; needAttention: LeadRow[]; openDeals: number; openDealValue: number; soldThisMonth: number; soldValueThisMonth: number; commissionToPost: number;
  unitsAvailable: number; unitsReserved: number; unitsSoldThisMonth: number; unitSalesValueThisMonth: number; unitReceivable: number;
  documentsExpiring: number; valuationsExpired: number;
}

export interface DealRow {
  id: number; code: string; propertyId: number; propertyCode: string | null; propertyTitle: string | null; buyerName: string; buyerCustomerId: number | null; agentName: string | null;
  agentUserId: number | null; stage: DealStage; listPrice: number | null; offerAmount: number; agreedPrice: number | null; paid: number; outstanding: number | null; commissionTotal: number;
  offerDate: string | null; offerValidUntil: string | null; reservationExpiry: string | null; expectedCompletion: string | null; completedDate: string | null; overdue: boolean; createdAt: string;
}
export interface MilestoneDto {
  id: number; kind: MilestoneKind; label: string; amount: number; dueDate: string | null; invoiceId: number | null; invoiceNumber: string | null; status: string; paid: number;
  outstanding: number; overdue: boolean;
}
export interface CommissionDto {
  id: number; role: string; payeeKind: PayeeKind; payeeUserId: number | null; payeeName: string; basis: CommissionBasis; sharePercent: number | null; fixedAmount: number | null;
  amount: number; status: CommissionStatus; due: boolean; expenseId: number | null; expenseStatus: string | null; expensePaid: boolean; note: string | null;
}
export interface FeeInvoiceDto { invoiceId: number; invoiceNumber: string; status: string; total: number; paid: number | null; }
export interface DealDto {
  id: number; code: string; propertyId: number; propertyCode: string | null; propertyTitle: string | null; propertyArea: string | null; leadId: number | null; leadName: string | null;
  buyerCustomerId: number | null; buyerName: string; buyerPhone: string | null; buyerEmail: string | null; agentUserId: number | null; agentName: string | null; stage: DealStage;
  listPrice: number | null; offerAmount: number; agreedPrice: number | null; offerDate: string | null; offerValidUntil: string | null; reservationDeposit: number | null;
  reservationExpiry: string | null; agreementDate: string | null; expectedCompletion: string | null; completedDate: string | null; commissionPercent: number | null;
  commissionFixed: number | null; commissionTotal: number; commissionAllocated: number; commissionKept: number; sellerCustomerId: number | null; sellerName: string | null;
  feeInvoice: FeeInvoiceDto | null; notes: string | null; cancelReason: string | null; createdByName: string | null; createdAt: string; scheduled: number; invoiced: number; paid: number;
  outstanding: number | null; milestones: MilestoneDto[]; commissions: CommissionDto[]; canManage: boolean;
}
export interface DealPayload {
  propertyId?: number | null; leadId?: number | null; buyerName?: string | null; buyerPhone?: string | null; buyerEmail?: string | null; buyerCustomerId?: number | null;
  offerAmount?: number | null; offerDate?: string | null; offerValidUntil?: string | null; agentUserId?: number | null; commissionPercent?: number | null; commissionFixed?: number | null;
  agreedPrice?: number | null; expectedCompletion?: string | null; notes?: string | null;
}
export interface DealFilter extends PageParams { q?: string; stage?: string; propertyId?: number | null; agentId?: number | null; openOnly?: boolean; }
export interface CommissionPayload {
  role?: string; payeeKind?: PayeeKind; payeeUserId?: number | null; payeeName?: string | null; basis?: CommissionBasis; sharePercent?: number | null; fixedAmount?: number | null;
  note?: string | null;
}

/** Real Estate: properties, leads and viewings (GET/PUT/POST under /api/realestate). Photos are fetched as blobs because an img tag cannot send the sign-in token. */
export type ProjectStatus = 'PLANNING' | 'SELLING' | 'COMPLETED' | 'ARCHIVED';
export type UnitKind = 'PLOT' | 'APARTMENT' | 'TOWNHOUSE' | 'VILLA' | 'SHOP' | 'OFFICE' | 'OTHER';
export type UnitStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'WITHHELD';
export type UnitSaleStage = 'RESERVED' | 'SOLD' | 'COMPLETED' | 'CANCELLED';
export const PROJECT_STATUS_LABEL: Record<ProjectStatus, string> = { PLANNING: 'Planning', SELLING: 'Selling', COMPLETED: 'Completed', ARCHIVED: 'Archived' };
export const UNIT_KINDS: { value: UnitKind; label: string }[] = [
  { value: 'PLOT', get label() { return T('Plot'); } }, { value: 'APARTMENT', get label() { return T('Apartment'); } }, { value: 'TOWNHOUSE', get label() { return T('Townhouse'); } }, { value: 'VILLA', get label() { return T('Villa'); } },
  { value: 'SHOP', get label() { return T('Shop'); } }, { value: 'OFFICE', get label() { return T('Office'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export const UNIT_STATUS_LABEL: Record<UnitStatus, string> = { AVAILABLE: 'Available', RESERVED: 'Reserved', SOLD: 'Sold', WITHHELD: 'Withheld' };
export const UNIT_SALE_STAGE_LABEL: Record<UnitSaleStage, string> = { RESERVED: 'Reserved', SOLD: 'Agreement signed', COMPLETED: 'Paid in full', CANCELLED: 'Cancelled' };
export function unitKindLabel(k: string): string { return UNIT_KINDS.find((x) => x.value === k)?.label ?? k; }

export interface ProjectRow {
  id: number; code: string; name: string; area: string | null; developerName: string | null; status: ProjectStatus; launchDate: string | null; handoverDate: string | null;
  totalUnits: number; available: number; reserved: number; sold: number; withheld: number; stockValue: number; soldValue: number;
}
export interface ProjectStats { stockValue: number; soldValue: number; collected: number; outstanding: number; minPrice: number | null; maxPrice: number | null; }
export interface UnitRow {
  id: number; projectId: number; projectCode: string | null; projectName: string | null; unitNo: string; block: string | null; kind: UnitKind; floor: number | null; bedrooms: number | null;
  bathrooms: number | null; sizeSqm: number | null; listPrice: number; pricePerSqm: number | null; status: UnitStatus; saleId: number | null; saleCode: string | null; buyerName: string | null;
}
export interface ProjectDto {
  id: number; code: string; name: string; area: string | null; address: string | null; developerName: string | null; status: ProjectStatus; launchDate: string | null; handoverDate: string | null;
  description: string | null; createdByName: string | null; createdAt: string; totalUnits: number; available: number; reserved: number; sold: number; withheld: number;
  stats: ProjectStats; blocks: string[]; units: UnitRow[];
}
export interface PriceChangeDto { id: number; at: string; oldPrice: number | null; newPrice: number; reason: string | null; changedByName: string | null; }
export interface UnitDto {
  id: number; projectId: number; projectCode: string | null; projectName: string | null; unitNo: string; block: string | null; kind: UnitKind; floor: number | null; bedrooms: number | null;
  bathrooms: number | null; sizeSqm: number | null; listPrice: number; pricePerSqm: number | null; status: UnitStatus; notes: string | null; createdAt: string; priceLocked: boolean;
  sales: UnitSaleRow[]; priceHistory: PriceChangeDto[];
}
export interface PriceLineDto { unitId: number; unitNo: string; block: string | null; oldPrice: number; newPrice: number; }
export interface PriceAdjustResult { applied: boolean; changed: number; lines: PriceLineDto[]; }
export interface BulkResult { created: number; unitNos: string[]; }
export interface ProjectPayload { name?: string; area?: string; address?: string; developerName?: string; status?: ProjectStatus; launchDate?: string | null; handoverDate?: string | null; description?: string; }
export interface UnitPayload {
  unitNo?: string; block?: string; kind?: UnitKind; floor?: number | null; bedrooms?: number | null; bathrooms?: number | null; sizeSqm?: number | null; listPrice?: number | null;
  status?: UnitStatus; notes?: string; reason?: string;
}
export interface BulkUnitsPayload {
  prefix: string; from: number | null; to: number | null; padding: number | null; block: string; kind: UnitKind; floor: number | null; bedrooms: number | null; bathrooms: number | null;
  sizeSqm: number | null; listPrice: number | null;
}
export interface PriceAdjustPayload { percent: number | null; roundTo: number | null; block: string | null; kind: UnitKind | null; reason: string | null; preview: boolean; }
export interface UnitSaleRow {
  id: number; code: string; unitId: number; unitNo: string | null; projectId: number; projectCode: string | null; projectName: string | null; buyerName: string; buyerCustomerId: number | null;
  agentName: string | null; stage: UnitSaleStage; listPrice: number; agreedPrice: number; paid: number; outstanding: number; reservedOn: string | null; reservationExpiry: string | null;
  agreementDate: string | null; completedDate: string | null; invoiceNumber: string | null; overdue: boolean; createdAt: string;
}
export interface SaleInvoiceDto { id: number; invoiceNumber: string; status: string; total: number; paid: number; outstanding: number; }
export interface UnitSaleDto {
  id: number; code: string; unitId: number; unitNo: string | null; unitKind: string | null; block: string | null; projectId: number; projectCode: string | null; projectName: string | null;
  leadId: number | null; leadName: string | null; buyerCustomerId: number | null; buyerName: string; buyerPhone: string | null; buyerEmail: string | null; agentUserId: number | null;
  agentName: string | null; stage: UnitSaleStage; listPrice: number; discount: number; agreedPrice: number; reservedOn: string | null; reservationExpiry: string | null;
  agreementDate: string | null; completedDate: string | null; notes: string | null; cancelReason: string | null; createdByName: string | null; createdAt: string;
  invoice: SaleInvoiceDto | null; plan: InstallmentPlanDetail | null; overdue: boolean; canEdit: boolean;
}
export interface UnitSalePayload {
  unitId?: number | null; leadId?: number | null; buyerName?: string | null; buyerPhone?: string | null; buyerEmail?: string | null; buyerCustomerId?: number | null; agentUserId?: number | null;
  agreedPrice?: number | null; reservationExpiry?: string | null; notes?: string | null;
}
export interface UnitPlanPayload {
  deposit: number | null; depositMethod: string | null; numberOfInstallments: number | null; frequency: InstallmentFrequency; firstDueDate: string | null; interestMethod: InterestMethod;
  interestRate: number | null; graceDays: number | null; lateFeeFixed: number | null; lateFeePercent: number | null; remindersEnabled: boolean; wholeUnits: boolean | null; notes: string | null;
}

@Injectable({ providedIn: 'root' })
export class RealEstateApiService {
  private base = `${API_BASE}/realestate`;
  private photoCache = new Map<string, string>();

  constructor(private http: HttpClient) {}

  private params(o: Record<string, unknown>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(o)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
    return p;
  }

  settings(): Observable<ReSettings> { return this.http.get<ReSettings>(`${this.base}/settings`); }
  saveSettings(p: ReSettingsPayload): Observable<ReSettings> { return this.http.put<ReSettings>(`${this.base}/settings`, p); }
  agents(): Observable<Option[]> { return this.http.get<Option[]>(`${this.base}/agents`); }
  overview(): Observable<ReOverview> { return this.http.get<ReOverview>(`${this.base}/overview`); }

  properties(f: PropertyFilter): Observable<PropertyRow[]> { return this.http.get<PropertyRow[]>(`${this.base}/properties`, { params: this.params({ ...f }) }); }
  property(id: number): Observable<PropertyDto> { return this.http.get<PropertyDto>(`${this.base}/properties/${id}`); }
  createProperty(p: PropertyPayload): Observable<PropertyDto> { return this.http.post<PropertyDto>(`${this.base}/properties`, p); }
  updateProperty(id: number, p: PropertyPayload): Observable<PropertyDto> { return this.http.put<PropertyDto>(`${this.base}/properties/${id}`, p); }
  setPropertyStatus(id: number, status: PropertyStatus): Observable<PropertyDto> { return this.http.put<PropertyDto>(`${this.base}/properties/${id}/status`, { status }); }
  deleteProperty(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/properties/${id}`); }
  addPhoto(id: number, file: File): Observable<PropertyDto> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<PropertyDto>(`${this.base}/properties/${id}/photos`, form);
  }
  setCover(id: number, photoId: number): Observable<PropertyDto> { return this.http.put<PropertyDto>(`${this.base}/properties/${id}/photos/${photoId}/cover`, {}); }
  deletePhoto(id: number, photoId: number): Observable<PropertyDto> { return this.http.delete<PropertyDto>(`${this.base}/properties/${id}/photos/${photoId}`); }
  /** An object URL for a photo (cached; photos never change under the same id). */
  photoUrl(propertyId: number, photoId: number): Observable<string> {
    const key = `${propertyId}/${photoId}`;
    const hit = this.photoCache.get(key);
    if (hit) return of(hit);
    return this.http.get(`${this.base}/properties/${propertyId}/photos/${photoId}`, { responseType: 'blob' }).pipe(
      map((b) => URL.createObjectURL(b)),
      tap((u) => this.photoCache.set(key, u)),
    );
  }

  leads(f: LeadFilter): Observable<LeadRow[]> { return this.http.get<LeadRow[]>(`${this.base}/leads`, { params: this.params({ ...f }) }); }
  lead(id: number): Observable<LeadDto> { return this.http.get<LeadDto>(`${this.base}/leads/${id}`); }
  createLead(p: LeadPayload): Observable<LeadDto> { return this.http.post<LeadDto>(`${this.base}/leads`, p); }
  updateLead(id: number, p: LeadPayload): Observable<LeadDto> { return this.http.put<LeadDto>(`${this.base}/leads/${id}`, p); }
  setLeadStatus(id: number, status: LeadStatus, lostReason: string | null): Observable<LeadDto> { return this.http.put<LeadDto>(`${this.base}/leads/${id}/status`, { status, lostReason }); }
  addNote(id: number, p: { kind: NoteKind; text: string; nextFollowUp: string | null }): Observable<LeadDto> { return this.http.post<LeadDto>(`${this.base}/leads/${id}/notes`, p); }
  deleteNote(id: number, noteId: number): Observable<LeadDto> { return this.http.delete<LeadDto>(`${this.base}/leads/${id}/notes/${noteId}`); }
  convertLead(id: number): Observable<LeadDto> { return this.http.post<LeadDto>(`${this.base}/leads/${id}/convert`, {}); }
  matches(id: number): Observable<Match[]> { return this.http.get<Match[]>(`${this.base}/leads/${id}/matches`); }
  deleteLead(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/leads/${id}`); }

  followUps(f: { agentId?: number | null; all?: boolean; days?: number }): Observable<FollowUpBoard> { return this.http.get<FollowUpBoard>(`${this.base}/follow-ups`, { params: this.params({ ...f }) }); }
  setFollowUp(id: number, nextFollowUp: string | null): Observable<LeadDto> { return this.http.put<LeadDto>(`${this.base}/leads/${id}/follow-up`, { nextFollowUp }); }
  sendFollowUpEmails(preview: boolean): Observable<DigestResult> { return this.http.post<DigestResult>(`${this.base}/follow-ups/send`, {}, { params: this.params({ preview }) }); }

  collections(f: { projectId?: number | null; agentId?: number | null; days?: number }): Observable<CollectionBoard> { return this.http.get<CollectionBoard>(`${this.base}/collections`, { params: this.params({ ...f }) }); }
  saleNotes(id: number): Observable<SaleNote[]> { return this.http.get<SaleNote[]>(`${this.base}/unit-sales/${id}/collection-notes`); }
  addSaleNote(id: number, p: { kind: SaleNoteKind; text: string; promiseDate: string | null }): Observable<SaleNote> { return this.http.post<SaleNote>(`${this.base}/unit-sales/${id}/collection-notes`, p); }
  remindBuyer(id: number): Observable<MailedBuyer> { return this.http.post<MailedBuyer>(`${this.base}/unit-sales/${id}/remind`, {}); }
  emailStatement(id: number): Observable<MailedBuyer> { return this.http.post<MailedBuyer>(`${this.base}/unit-sales/${id}/statement/email`, {}); }

  payouts(f: PayoutFilter): Observable<PayoutBoard> { return this.http.get<PayoutBoard>(`${this.base}/commissions/payouts`, { params: this.params({ ...f }) }); }
  postDueCommissions(f: PayoutFilter): Observable<PostDueResult> { return this.http.post<PostDueResult>(`${this.base}/commissions/post-due`, {}, { params: this.params({ ...f }) }); }
  emailCommissionStatement(f: PayoutFilter): Observable<MailedBuyer> { return this.http.post<MailedBuyer>(`${this.base}/commissions/statement/email`, {}, { params: this.params({ ...f }) }); }

  importFile(kind: 'leads' | 'properties', file: File, dryRun: boolean, listThem: boolean): Observable<ImportResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ImportResult>(`${this.base}/import/${kind}`, body, { params: this.params({ dryRun: dryRun ? 'true' : 'false', listThem: listThem ? 'true' : '' }) });
  }
  importTemplate(kind: 'leads' | 'properties', format: 'xlsx' | 'csv'): Observable<Blob> { return this.http.get(`${this.base}/import/${kind}/template`, { params: this.params({ format }), responseType: 'blob' }); }

  remindViewing(id: number): Observable<RemindOne> { return this.http.post<RemindOne>(`${this.base}/viewings/${id}/remind`, {}); }
  sendViewingReminders(preview: boolean): Observable<ReminderResult> { return this.http.post<ReminderResult>(`${this.base}/viewings/reminders/send`, {}, { params: this.params({ preview }) }); }

  deals(f: DealFilter): Observable<DealRow[]> { return this.http.get<DealRow[]>(`${this.base}/deals`, { params: this.params({ ...f }) }); }
  deal(id: number): Observable<DealDto> { return this.http.get<DealDto>(`${this.base}/deals/${id}`); }
  createDeal(p: DealPayload): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals`, p); }
  updateDeal(id: number, p: DealPayload): Observable<DealDto> { return this.http.put<DealDto>(`${this.base}/deals/${id}`, p); }
  reserveDeal(id: number, p: { agreedPrice: number | null; deposit: number | null; reservationExpiry: string | null; expectedCompletion: string | null }): Observable<DealDto> {
    return this.http.post<DealDto>(`${this.base}/deals/${id}/reserve`, p);
  }
  dealAgreement(id: number, p: { agreementDate: string | null; expectedCompletion: string | null }): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/agreement`, p); }
  completeDeal(id: number, force: boolean, completedDate: string | null): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/complete`, { force, completedDate }); }
  cancelDeal(id: number, reason: string): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/cancel`, { reason }); }
  deleteDeal(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/deals/${id}`); }
  addMilestone(id: number, p: { kind: MilestoneKind; label: string; amount: number; dueDate: string | null }): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/milestones`, p); }
  updateMilestone(id: number, mid: number, p: { kind?: MilestoneKind; label?: string; amount?: number; dueDate?: string | null }): Observable<DealDto> {
    return this.http.put<DealDto>(`${this.base}/deals/${id}/milestones/${mid}`, p);
  }
  deleteMilestone(id: number, mid: number): Observable<DealDto> { return this.http.delete<DealDto>(`${this.base}/deals/${id}/milestones/${mid}`); }
  dealPlan(id: number, p: { depositPercent: number; instalments: number; firstDue: string | null; everyMonths: number }): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/plan`, p); }
  invoiceMilestone(id: number, mid: number, issue: boolean): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/milestones/${mid}/invoice`, { issue }); }
  issueMilestone(id: number, mid: number): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/milestones/${mid}/issue`, {}); }
  addCommission(id: number, p: CommissionPayload): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/commissions`, p); }
  updateCommission(id: number, cid: number, p: CommissionPayload): Observable<DealDto> { return this.http.put<DealDto>(`${this.base}/deals/${id}/commissions/${cid}`, p); }
  deleteCommission(id: number, cid: number): Observable<DealDto> { return this.http.delete<DealDto>(`${this.base}/deals/${id}/commissions/${cid}`); }
  postCommission(id: number, cid: number, accountId: number | null): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/commissions/${cid}/post`, { accountId }); }
  unpostCommission(id: number, cid: number): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/commissions/${cid}/unpost`, {}); }
  dealSeller(id: number): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/seller`, {}); }
  dealFeeInvoice(id: number, issue: boolean): Observable<DealDto> { return this.http.post<DealDto>(`${this.base}/deals/${id}/fee-invoice`, { issue }); }

  viewings(f: { from?: string; to?: string; agentId?: number | null; status?: string }): Observable<ViewingDto[]> { return this.http.get<ViewingDto[]>(`${this.base}/viewings`, { params: this.params({ ...f }) }); }
  schedule(p: ViewingPayload): Observable<ViewingDto> { return this.http.post<ViewingDto>(`${this.base}/viewings`, p); }
  reschedule(id: number, p: Partial<ViewingPayload>): Observable<ViewingDto> { return this.http.put<ViewingDto>(`${this.base}/viewings/${id}`, p); }
  setViewingStatus(id: number, status: ViewingStatus, feedback: string | null): Observable<ViewingDto> { return this.http.put<ViewingDto>(`${this.base}/viewings/${id}/status`, { status, feedback }); }
  projects(f: { q?: string; status?: string }): Observable<ProjectRow[]> { return this.http.get<ProjectRow[]>(`${this.base}/projects`, { params: this.params({ ...f }) }); }
  project(id: number): Observable<ProjectDto> { return this.http.get<ProjectDto>(`${this.base}/projects/${id}`); }
  createProject(p: ProjectPayload): Observable<ProjectDto> { return this.http.post<ProjectDto>(`${this.base}/projects`, p); }
  updateProject(id: number, p: ProjectPayload): Observable<ProjectDto> { return this.http.put<ProjectDto>(`${this.base}/projects/${id}`, p); }
  deleteProject(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/projects/${id}`); }
  addUnit(projectId: number, p: UnitPayload): Observable<UnitDto> { return this.http.post<UnitDto>(`${this.base}/projects/${projectId}/units`, p); }
  addUnitsBulk(projectId: number, p: BulkUnitsPayload): Observable<BulkResult> { return this.http.post<BulkResult>(`${this.base}/projects/${projectId}/units/bulk`, p); }
  adjustPrices(projectId: number, p: PriceAdjustPayload): Observable<PriceAdjustResult> { return this.http.post<PriceAdjustResult>(`${this.base}/projects/${projectId}/prices`, p); }
  units(f: { q?: string; projectId?: number | null; status?: string; kind?: string; block?: string; availableOnly?: boolean }): Observable<UnitRow[]> {
    return this.http.get<UnitRow[]>(`${this.base}/units`, { params: this.params({ ...f }) });
  }
  unit(id: number): Observable<UnitDto> { return this.http.get<UnitDto>(`${this.base}/units/${id}`); }
  updateUnit(id: number, p: UnitPayload): Observable<UnitDto> { return this.http.put<UnitDto>(`${this.base}/units/${id}`, p); }
  deleteUnit(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/units/${id}`); }

  unitSales(f: { q?: string; stage?: string; projectId?: number | null; openOnly?: boolean }): Observable<UnitSaleRow[]> { return this.http.get<UnitSaleRow[]>(`${this.base}/unit-sales`, { params: this.params({ ...f }) }); }
  // Paged lists for the list screens (the plain methods still return every row, for pickers and reports).
  propertiesPage(f: PropertyFilter): Observable<PageResult<PropertyRow>> { return getPaged<PropertyRow>(this.http, `${this.base}/properties`, this.params({ ...f })); }
  leadsPage(f: LeadFilter): Observable<PageResult<LeadRow>> { return getPaged<LeadRow>(this.http, `${this.base}/leads`, this.params({ ...f })); }
  dealsPage(f: DealFilter): Observable<PageResult<DealRow>> { return getPaged<DealRow>(this.http, `${this.base}/deals`, this.params({ ...f })); }
  projectsPage(f: { q?: string; status?: string } & PageParams): Observable<PageResult<ProjectRow>> { return getPaged<ProjectRow>(this.http, `${this.base}/projects`, this.params({ ...f })); }
  unitsPage(f: { q?: string; projectId?: number | null; status?: string; kind?: string; block?: string; availableOnly?: boolean } & PageParams): Observable<PageResult<UnitRow>> { return getPaged<UnitRow>(this.http, `${this.base}/units`, this.params({ ...f })); }
  unitSalesPage(f: { q?: string; stage?: string; projectId?: number | null; openOnly?: boolean } & PageParams): Observable<PageResult<UnitSaleRow>> { return getPaged<UnitSaleRow>(this.http, `${this.base}/unit-sales`, this.params({ ...f })); }
  unitSale(id: number): Observable<UnitSaleDto> { return this.http.get<UnitSaleDto>(`${this.base}/unit-sales/${id}`); }
  reserveUnit(p: UnitSalePayload): Observable<UnitSaleDto> { return this.http.post<UnitSaleDto>(`${this.base}/unit-sales`, p); }
  updateUnitSale(id: number, p: UnitSalePayload): Observable<UnitSaleDto> { return this.http.put<UnitSaleDto>(`${this.base}/unit-sales/${id}`, p); }
  unitSaleAgreement(id: number, agreementDate: string | null): Observable<UnitSaleDto> { return this.http.post<UnitSaleDto>(`${this.base}/unit-sales/${id}/agreement`, { agreementDate }); }
  unitSalePlanPreview(id: number, p: UnitPlanPayload): Observable<InstallmentPreview> { return this.http.post<InstallmentPreview>(`${this.base}/unit-sales/${id}/plan/preview`, p); }
  unitSalePlan(id: number, p: UnitPlanPayload): Observable<UnitSaleDto> { return this.http.post<UnitSaleDto>(`${this.base}/unit-sales/${id}/plan`, p); }
  completeUnitSale(id: number, force: boolean, completedDate: string | null): Observable<UnitSaleDto> { return this.http.post<UnitSaleDto>(`${this.base}/unit-sales/${id}/complete`, { force, completedDate }); }
  cancelUnitSale(id: number, reason: string): Observable<UnitSaleDto> { return this.http.post<UnitSaleDto>(`${this.base}/unit-sales/${id}/cancel`, { reason }); }
  deleteUnitSale(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/unit-sales/${id}`); }

  documents(f: { subjectType?: DocSubject | null; subjectId?: number | null; category?: string; q?: string; expiryOnly?: boolean }): Observable<ReDocument[]> {
    return this.http.get<ReDocument[]>(`${this.base}/documents`, { params: this.params({ ...f }) });
  }
  uploadDocument(subjectType: DocSubject, subjectId: number, file: File, d: DocumentPayload): Observable<ReDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('subjectType', subjectType);
    form.append('subjectId', String(subjectId));
    if (d.name) form.append('name', d.name);
    if (d.category) form.append('category', d.category);
    if (d.description) form.append('description', d.description);
    if (d.expiryDate) form.append('expiryDate', d.expiryDate);
    return this.http.post<ReDocument>(`${this.base}/documents`, form);
  }
  updateDocument(id: number, d: DocumentPayload): Observable<ReDocument> { return this.http.put<ReDocument>(`${this.base}/documents/${id}`, d); }
  deleteDocument(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/documents/${id}`); }
  /** The file is fetched with the sign-in token and handed to the browser as a download. */
  downloadDocument(d: { id: number; fileName: string }): Observable<void> {
    return this.http.get(`${this.base}/documents/${d.id}/file`, { responseType: 'blob' }).pipe(
      map((b) => {
        const url = URL.createObjectURL(b);
        const a = document.createElement('a');
        a.href = url;
        a.download = d.fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
      }),
    );
  }

  valuations(f: { propertyId?: number | null; currentOnly?: boolean; expiredOnly?: boolean }): Observable<ValuationDto[]> { return this.http.get<ValuationDto[]>(`${this.base}/valuations`, { params: this.params({ ...f }) }); }
  createValuation(p: ValuationPayload): Observable<ValuationDto> { return this.http.post<ValuationDto>(`${this.base}/valuations`, p); }
  updateValuation(id: number, p: ValuationPayload): Observable<ValuationDto> { return this.http.put<ValuationDto>(`${this.base}/valuations/${id}`, p); }
  deleteValuation(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/valuations/${id}`); }
}
