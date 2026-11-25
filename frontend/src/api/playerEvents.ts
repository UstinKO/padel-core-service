import { jsonPost } from './account';
import { apiFetch } from './client';

export type EventAction =
  'WITHDRAW' | 'CANCEL_PAIR' | 'CHANGE_CONTACT_VISIBILITY' | 'SEARCH_REPLACEMENT';
export type Decision = 'CONFIRM' | 'DECLINE';

export interface Participation {
  requestId: string;
  playerName: string;
  status: string;
  delegated: boolean;
  availableActions: Decision[];
}

export interface PlayerEvent {
  event: {
    id: number;
    name: string;
    type: string;
    status: string;
    modality: string;
    gender: string;
    level: string | null;
    startsAt: string;
    clubName: string | null;
  };
  registrationId: number;
  version: number;
  registrationStatus: string;
  mode: 'INDIVIDUAL' | 'SOLO' | 'PAIR' | null;
  partnerName: string | null;
  queuedAt: string | null;
  queuePosition: number | null;
  participationStatus: string;
  participation: Participation[];
  replacementNeeded: boolean;
  replacementSearch: boolean;
  shareContacts: boolean;
  paymentStatus: string | null;
  availableActions: EventAction[];
  cancellations: {
    eventId: string;
    cancelledAt: string | null;
    initiator: string;
    reasonCode: string;
    organizerComment: string | null;
  }[];
}

interface Overview {
  upcoming: PlayerEvent[];
  history: PlayerEvent[];
}

interface CancellationQuote {
  token: string;
  currency: string | null;
  obligation: number | null;
  alreadyPaid: number | null;
  remainingDue: number | null;
  refundable: number | null;
}

export const playerEventsKey = ['player-events'] as const;
const root = '/api/players/me/events';
const pairCancellation = (id: number) => `/api/tournaments/double/${id}/pair-cancellation`;

export const getPlayerEvents = (signal?: AbortSignal) => apiFetch<Overview>(root, { signal });
export const getCancellationQuote = (id: number, signal?: AbortSignal) =>
  apiFetch<CancellationQuote>(pairCancellation(id), { signal });
export const withdraw = (entry: PlayerEvent) =>
  jsonPost<void>(`${root}/${entry.registrationId}/withdraw`, { version: entry.version });
export const cancelPair = (id: number, token: string) =>
  jsonPost<CancellationQuote>(pairCancellation(id), { token });
export const answer = (id: string, decision: Decision) =>
  jsonPost<unknown>(`/api/players/me/participation/${id}`, { decision });
export const setVisibility = (entry: PlayerEvent) =>
  jsonPost<void>(
    `${root}/${entry.registrationId}/contact-visibility`,
    {
      version: entry.version,
      shareContacts: !entry.shareContacts,
    },
    'PUT',
  );
export const setReplacementSearch = (entry: PlayerEvent) =>
  jsonPost<void>(
    `/api/players/me/tournaments/${entry.event.id}/replacement-search`,
    {
      enabled: !entry.replacementSearch,
    },
    'PUT',
  );
