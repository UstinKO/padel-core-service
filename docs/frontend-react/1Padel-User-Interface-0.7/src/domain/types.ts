export type Format = 'king' | 'americano' | 'pairs' | 'training' | 'open';
export type Category = 'Мужской' | 'Женский' | 'Микст';
export interface Event {
    id: string;
    title: string;
    format: Format;
    symbol: string;
    tone: string;
    level: string;
    gender: Category;
    day: number;
    date: string;
    week: string;
    time: string;
    club: string;
    address: string;
    price: number;
    filled: number;
    max: number;
    duration: string;
    end: string;
    rounds: number;
    total: number;
    cancel: string;
    live?: boolean;
    partnerDeadlinePassed?: boolean;
}
export interface User {
    id: string;
    first: string;
    last: string;
    email?: string;
    phone?: string;
    telegram?: string;
    level?: string;
    preferredLocale?: 'ru' | 'es' | 'en';
    emailVerified?: boolean;
    authProvider?: 'email' | 'google';
    preview?: boolean;
}
export interface PairSelection {
    mode: 'with-partner' | 'add-later' | 'search';
    partner?: string;
    partnerId?: string;
    first?: string;
    last?: string;
    phone?: string;
    telegram?: string;
    allowContact?: boolean;
}
export type RegistrationStatus = 'confirmed' | 'waiting' | 'offered' | 'cancelled' | 'completed' | 'solo-search' | 'solo-later';
export interface Registration {
    eventId: string;
    status: RegistrationStatus;
    partner?: string;
    partnerId?: string;
    partnerMode?: PairSelection['mode'];
    allowContact?: boolean;
    partnerDeadline?: string;
    proposalTo?: string;
    proposalState?: 'sent' | 'accepted' | 'declined';
    proposalStatus?: 'pending' | 'accepted' | 'declined' | 'expired';
    paymentStatus?: 'unpaid' | 'paid' | 'pending' | 'failed' | 'refunded';
    /** Legacy field: no automatic expiry is applied to participation confirmations. */
    offerExpiresAt?: string;
    participationStatus?: 'pending' | 'confirmed';
    promotedFromReserve?: boolean;
    readyAt?: string;
    position?: number;
    createdAt: string;
}
export interface Filters {
    type: 'all' | 'tournament' | 'training' | 'open';
    gender: 'all' | Category;
    level: string;
    club: string;
}
