import { HelpDeskPriority, HelpDeskSlaState, HelpDeskStatus, HelpDeskTicket } from '../core/models';

import { T } from '../core/i18n/t';
/** Shared look-ups for the Help Desk screens: badge classes, labels and deadline wording. */
export function hdPriorityClass(p: HelpDeskPriority | string): string {
  switch (p) {
    case 'URGENT': return 'badge-danger';
    case 'HIGH': return 'badge-warning';
    case 'MEDIUM': return 'badge-secondary';
    default: return 'badge-outline';
  }
}

export function hdStatusClass(s: HelpDeskStatus | string): string {
  switch (s) {
    case 'OPEN': return 'badge-warning';
    case 'IN_PROGRESS': return 'badge-secondary';
    case 'WAITING': return 'badge-outline';
    case 'RESOLVED': return 'badge-success';
    default: return 'badge-outline';
  }
}

export function hdStatusLabel(s: HelpDeskStatus | string): string {
  switch (s) {
    case 'IN_PROGRESS': return 'In progress';
    case 'WAITING': return 'Waiting for customer';
    case 'OPEN': return 'Open';
    case 'RESOLVED': return 'Resolved';
    case 'CLOSED': return 'Closed';
    case 'MERGED': return 'Merged';
    default: return String(s);
  }
}

export function hdSlaClass(s: HelpDeskSlaState | null): string {
  switch (s) {
    case 'BREACHED': case 'MISSED': return 'badge-danger';
    case 'DUE_SOON': return 'badge-warning';
    case 'MET': return 'badge-success';
    default: return 'badge-outline';
  }
}

export function hdSlaLabel(s: HelpDeskSlaState | null, kind: 'reply' | 'fix'): string {
  const what = kind === 'reply' ? 'First reply' : 'Resolution';
  switch (s) {
    case 'OK': return what + ' on track';
    case 'PAUSED': return what + ' clock paused';
    case 'DUE_SOON': return what + ' due soon';
    case 'BREACHED': return what + ' overdue';
    case 'MET': return what + ' on time';
    case 'MISSED': return what + ' was late';
    default: return '';
  }
}

/** The one badge a list row shows: the worst thing about the ticket's deadlines, or nothing. */
export function hdWorstSla(t: HelpDeskTicket): { label: string; cls: string } | null {
  if (t.responseState === 'BREACHED') return { get label() { return T('Reply overdue'); }, cls: 'badge-danger' };
  if (t.resolutionState === 'BREACHED') return { get label() { return T('Overdue'); }, cls: 'badge-danger' };
  if (t.resolutionState === 'DUE_SOON') return { get label() { return T('Due soon'); }, cls: 'badge-warning' };
  if (t.resolutionState === 'MISSED' || t.responseState === 'MISSED') return { get label() { return T('Was late'); }, cls: 'badge-outline' };
  if (t.resolutionState === 'PAUSED') return { get label() { return T('Clock paused'); }, cls: 'badge-outline' };
  return null;
}

/** "in 3 h", "2 d overdue" — a short, readable distance to a deadline. */
export function hdDistance(iso: string | null): string {
  if (!iso) return '';
  const diff = new Date(iso).getTime() - Date.now();
  const abs = Math.abs(diff);
  const mins = Math.round(abs / 60000);
  let text: string;
  if (mins < 90) text = mins + ' min';
  else if (mins < 60 * 36) text = Math.round(mins / 60) + ' h';
  else text = Math.round(mins / 1440) + ' d';
  return diff >= 0 ? 'in ' + text : text + ' ago';
}

export const HD_EVENT_LABELS: Record<string, string> = {
  CREATED: 'Ticket created', EDITED: 'Details changed', PRIORITY_CHANGED: 'Priority changed', ASSIGNED: 'Assigned', STARTED: 'Work started',
  WAITING: 'Waiting for the customer', RESUMED: 'Back to work', RESOLVED: 'Resolved', CLOSED: 'Closed', REOPENED: 'Reopened', AUTO_CLOSED: 'Closed automatically',
  REPLY_STAFF: 'Reply sent', REPLY_REQUESTER: 'Customer replied', NOTE: 'Internal note', FILE: 'File added', TAGS: 'Tags changed', LINKED: 'Links changed',
  MERGED: 'Merged', RATED: 'Customer rated the help', SLA_WARNING: 'Deadline close', SLA_BREACH: 'Deadline missed',
};
