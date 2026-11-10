import { apiFetch } from './client';

export const eventTypes = [
  'KING_OF_COURT',
  'AMERICANO',
  'AMERICANO_TEAMS',
  'CANCHA_ABIERTA',
  'PADEL_CLINIC',
] as const;
export const genders = ['MASCULINO', 'FEMENINO', 'MIXTO'] as const;
export const levels = [
  'C9',
  'C8',
  'C7',
  'C6',
  'C5',
  'C4',
  'C9_C8',
  'C8_C7',
  'C7_C6',
  'C6_C5',
  'C5_C4',
  'D9',
  'D8',
  'D7',
  'D6',
  'D5',
  'D4',
  'D9_D8',
  'D7_D8',
  'D7_D6',
  'D6_D5',
  'D5_D4',
  'SUMA_16',
  'SUMA_15',
  'SUMA_14',
  'SUMA_13',
  'SUMA_12',
  'SUMA_11',
  'SUMA_10',
  'TODOS',
  'PRINCIPIANTES',
] as const;
export interface PublicEvent {
  id: number;
  name: string;
  club: { id: number; name: string | null; address: string | null };
  type: (typeof eventTypes)[number];
  modality: 'INDIVIDUAL' | 'DOBLES';
  gender: (typeof genders)[number];
  level: string | null;
  startsAt: string;
  timeZone: string;
  duration: string | null;
  price: number;
  currency: string;
  status: 'BORRADOR' | 'PUBLICADO' | 'REGISTRO_ABIERTO' | 'CERRADO' | 'FINALIZADO' | 'CANCELADO';
  registrationOpen: boolean;
  capacity: number;
  capacityUnit: 'PLAYERS' | 'PAIRS';
  occupied: number;
  available: number;
  waitlistCount: number;
  websiteUrl: string;
}
export interface PublicClub {
  id: number;
  name: string;
  address: string | null;
  city: string | null;
}
export interface CatalogPage {
  items: PublicEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface HomeData {
  totalClubs: number;
  totalPlayers: number;
  totalTournaments: number;
  upcomingTournaments: PublicEvent[];
}
export const getHome = (signal: AbortSignal) => apiFetch<HomeData>('/api/public/home', { signal });
export const getClubs = (signal: AbortSignal) =>
  apiFetch<PublicClub[]>('/api/public/clubs', { signal });
export const getCatalog = (params: URLSearchParams, signal: AbortSignal) =>
  apiFetch<CatalogPage>(`/api/public/catalog?${params}`, { signal });

export function buenosAiresToday(date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Argentina/Buenos_Aires',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

export function catalogParams(search: URLSearchParams): URLSearchParams {
  const params = new URLSearchParams();
  for (const key of ['clubId', 'type', 'modality', 'gender', 'level', 'status', 'from', 'to']) {
    const value = search.get(key);
    if (value) params.set(key, value);
  }
  if (!params.has('from')) params.set('from', buenosAiresToday());
  const page = search.get('page') ?? '0';
  params.set(
    'page',
    /^\d+$/.test(page) && Number.isSafeInteger(Number(page)) && Number(page) <= 2147483647
      ? page
      : '0',
  );
  params.set('size', '12');
  return params;
}
