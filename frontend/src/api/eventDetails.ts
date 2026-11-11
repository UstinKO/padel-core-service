import type { PublicEvent } from './discovery';
import { apiFetch } from './client';

export interface Participant {
  id: string;
  names: string[];
  seekingPartner: boolean;
}
export interface EventDetails {
  event: PublicEvent & {
    description: string | null;
    cancellationDeadline: string | null;
    participants: { confirmed: Participant[]; waitlist: Participant[] };
  };
  organizer: { contact: string; whatsappUrl: string } | null;
  format:
    | 'KING_OF_COURT'
    | 'AMERICANO'
    | 'TEAM_PAIRS'
    | 'TEAM_AMERICANO'
    | 'TEAM_PLAYOFF'
    | 'CANCHA_ABIERTA'
    | 'PADEL_CLINIC';
  phase: 'NOT_STARTED' | 'LIVE' | 'QUALIFICATION' | 'PLAYOFF' | 'FINISHED' | 'CANCELLED';
  actions: { type: 'REGISTRATION_INFO' | 'LIVE' | 'CALENDAR'; url: string }[];
  canonicalUrl: string;
  calendarUrl: string;
  pngUrl: string | null;
}
export const getEventDetails = (id: string, signal: AbortSignal) =>
  apiFetch<EventDetails>(`/api/public/events/${id}`, { signal });
export function validEntityId(value: string): boolean {
  return /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}
export function safeWebUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value, window.location.origin);
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}
export function canonicalUrl(value: string, path: string): string | null {
  const safe = safeWebUrl(value);
  if (!safe) return null;
  const url = new URL(safe);
  if (url.pathname !== path) return null;
  url.search = '';
  url.hash = '';
  return url.href;
}
