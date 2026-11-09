import type { Event, Registration, User, PairSelection } from '../domain/types';
/** Presentation adapter. Replace with existing server operations, including CSRF/auth and capacity checks. */
export interface RegistrationGateway {
    register(event: Event, user: User, existing: Registration[], partner?: string | PairSelection): Promise<Registration>;
    cancel(eventId: string): Promise<void>;
}
export const demoRegistrationGateway: RegistrationGateway = {
    async register(event, user, existing, selection) {
        const previous = existing.find(r => r.eventId === event.id && !['cancelled', 'completed'].includes(r.status));
        if (previous)
            return previous;
        if (!user)
            throw new Error('Войдите, чтобы записаться.');
        const pair: PairSelection | undefined = typeof selection === 'string' ? { mode: 'with-partner', partner: selection } : selection;
        const waiting = event.max - event.filled < (event.format === 'pairs' ? 2 : 1);
        const base = { eventId: event.id, createdAt: new Date().toISOString() };
        if (event.format === 'pairs') {
            if (!pair)
                throw new Error('Выберите способ регистрации.');
            if (pair.mode !== 'with-partner') {
                if (waiting || (pair.mode === 'add-later' && event.partnerDeadlinePassed))
                    return { ...base, status: 'waiting', partnerMode: pair.mode, allowContact: !!pair.allowContact };
                return { ...base, status: pair.mode === 'search' ? 'solo-search' : 'solo-later', partnerMode: pair.mode, allowContact: !!pair.allowContact, partnerDeadline: '29 октября · 19:00' };
            }
            const partner = pair.partner?.trim() || [pair.first, pair.last].filter(Boolean).join(' ');
            if (partner.split(/\s+/).length < 2)
                throw new Error('Укажите имя и фамилию партнёра.');
            if (pair.partnerId === user.id)
                throw new Error('Вы не можете выбрать себя партнёром.');
            return { ...base, status: waiting ? 'waiting' : 'confirmed', partner, partnerId: pair.partnerId, partnerMode: pair.mode, position: waiting ? 3 : undefined, paymentStatus: 'unpaid' };
        }
        return { ...base, status: waiting ? 'waiting' : 'confirmed', position: waiting ? 3 : undefined, paymentStatus: waiting ? undefined : 'unpaid' };
    },
    async cancel() { }
};
