import { createContext, useContext, useRef, useState, type ReactNode } from 'react';
import type { User, Registration, Event, PairSelection } from './domain/types';
import { demoRegistrationGateway } from './services/registration';
import { scenarioState } from './data/scenarios';
const key = '1padel-react-demo-v1';
type State = {
    user: User | null;
    registrations: Registration[];
};
function restore(): State { try {
    const v = JSON.parse(localStorage.getItem(key) || '{}');
    if (v.user && typeof v.user.id === 'string' && typeof v.user.first === 'string' && typeof v.user.last === 'string' && Array.isArray(v.registrations))
        return { user: v.user, registrations: v.registrations.filter((r: Registration) => r && typeof r.eventId === 'string' && ['confirmed', 'waiting', 'offered', 'cancelled', 'completed', 'solo-search', 'solo-later'].includes(r.status)).map((r: Registration) => r.status === 'offered' ? { ...r, status: 'confirmed' as const, participationStatus: 'pending' as const, promotedFromReserve: true, position: undefined, offerExpiresAt: undefined } : r) };
}
catch { } return { user: null, registrations: [] }; }
interface AppState extends State {
    signIn: (u: User) => void;
    signOut: () => void;
    updateUser: (patch: Partial<Omit<User, 'id'>>) => void;
    updateRegistration: (eventId: string, patch: Partial<Registration>) => void;
    setDemoScenario: (name: string) => void;
    book: (e: Event, partner?: string | PairSelection) => Promise<Registration>;
    cancel: (id: string) => Promise<void>;
    notify: (s: string) => void;
}
const Context = createContext<AppState | null>(null);
export function AppProvider({ children }: {
    children: ReactNode;
}) {
    const [state, setState] = useState(restore);
    const stateRef = useRef(state);
    const [notice, setNotice] = useState('');
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const pending = useRef(new Map<string, Promise<Registration>>());
    function commit(next: State) { stateRef.current = next; setState(next); try {
        localStorage.setItem(key, JSON.stringify(next));
    }
    catch {
        notify('Хранилище недоступно. Записи сохранятся только до закрытия страницы.');
    } }
    function notify(s: string) { setNotice(s); clearTimeout(timer.current); timer.current = setTimeout(() => setNotice(''), 4500); }
    const api: AppState = { ...state, notify,
        signIn(user) { commit({ user, registrations: stateRef.current.user?.id === user.id ? stateRef.current.registrations : [] }); },
        signOut() { commit({ user: null, registrations: [] }); },
        updateUser(patch) { if (stateRef.current.user)
            commit({ ...stateRef.current, user: { ...stateRef.current.user, ...patch } }); },
        updateRegistration(id, patch) { commit({ ...stateRef.current, registrations: stateRef.current.registrations.map(r => r.eventId === id ? { ...r, ...patch, eventId: id } : r) }); },
        setDemoScenario(name) { commit(scenarioState(name)); },
        async book(e, partner) { const snapshot = stateRef.current; if (!snapshot.user)
            throw new Error('Нужен аккаунт.'); const existing = snapshot.registrations.find(r => r.eventId === e.id && !['cancelled', 'completed'].includes(r.status)); if (existing)
            return existing; const inFlight = pending.current.get(e.id); if (inFlight)
            return inFlight; const operation = demoRegistrationGateway.register(e, snapshot.user, snapshot.registrations, partner).then(r => { if (stateRef.current.user?.id !== snapshot.user?.id)
            throw new Error('Аккаунт изменился. Повторите проверку записи.'); commit({ ...stateRef.current, registrations: [...stateRef.current.registrations.filter(x => x.eventId !== e.id), r] }); return r; }).finally(() => pending.current.delete(e.id)); pending.current.set(e.id, operation); return operation; },
        async cancel(id) { const userId = stateRef.current.user?.id; await demoRegistrationGateway.cancel(id); if (userId === stateRef.current.user?.id)
            commit({ ...stateRef.current, registrations: stateRef.current.registrations.map(r => r.eventId === id ? { ...r, status: 'cancelled' } : r) }); } };
    return <Context.Provider value={api}>{children}<div id="toast" role="status" aria-live="polite" className={notice ? 'toast-visible' : ''}>{notice}</div></Context.Provider>;
}
export const useApp = () => { const c = useContext(Context); if (!c)
    throw new Error('AppProvider missing'); return c; };
