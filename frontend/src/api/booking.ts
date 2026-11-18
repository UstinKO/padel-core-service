import { apiFetch, ApiError, cookie } from './client';

export interface Registration {
  registered: boolean;
  status?: string;
  waitlistPosition?: number | null;
  registrationId?: number;
  partnerId?: number | null;
  partnerFirstName?: string | null;
  partnerLastName?: string | null;
}

export interface BookingResult {
  success?: boolean;
  status?: string;
  code?: string;
  message?: string;
  needContact?: boolean;
  needLevel?: boolean;
}

export interface Partner {
  id: number;
  nombreCompleto: string;
  nivelJugador: string;
}

export interface Candidate {
  playerId: number;
  registrationId: number;
  playerName: string;
  nivel: string | null;
  shareContacts: boolean;
  telefono: string | null;
  telegramUsername: string | null;
}

export interface BookingTerms {
  token: string;
  freeUntil: string;
  pricePerPlayer: number;
  pairPrice: number;
  lateObligation: number;
  currency: string;
  regulationUrl: string;
}

export type PartnerInput =
  | { existingUserId: number }
  | { nombre: string; apellido: string; telefono: string; telegramUsername: string };

export const doublePath = (id: string) => `/api/tournaments/double/${id}`;
export const ownBooking = (id: string) =>
  apiFetch<Registration>(`/players/tournaments/${id}/my-registration`, { cache: 'no-store' });
export const searchPartners = (id: string, term: string, signal: AbortSignal) =>
  apiFetch<Partner[]>(`/api/players/search?query=${encodeURIComponent(term)}&tournamentId=${id}`, {
    cache: 'no-store',
    signal,
  });
export const searchCandidates = (id: string) =>
  apiFetch<Candidate[]>(`${doublePath(id)}/looking-for-partner`);
export const bookingTerms = (id: string) =>
  apiFetch<BookingTerms | undefined>(`${doublePath(id)}/pair-terms`, { cache: 'no-store' });

export async function sendBooking(path: string, data = {}) {
  const url = new URL(path, window.location.origin);
  if (url.origin !== window.location.origin) throw new TypeError('Invalid booking URL');
  const response = await fetch(url.pathname + url.search, {
    method: 'POST',
    credentials: 'same-origin',
    redirect: 'error',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': cookie('XSRF-TOKEN') ?? '',
    },
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(15000),
  });
  if (response.status >= 500) throw new ApiError(response.status, null);
  const result = (await response.json().catch(() => null)) as BookingResult | null;
  if (response.ok && result?.success !== true && result?.success !== false)
    throw new Error('Unknown booking result');
  if (!response.ok || !result?.success) throw new BookingError(response.status, result ?? {});
  return result;
}

export class BookingError extends Error {
  constructor(
    readonly status: number,
    readonly result: BookingResult,
  ) {
    super(result.code ?? 'BOOKING_REJECTED');
  }
}
