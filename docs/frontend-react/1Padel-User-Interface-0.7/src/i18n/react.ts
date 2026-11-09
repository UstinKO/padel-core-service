import { useSyncExternalStore } from 'react';
import { getLocale, subscribeLocale } from './store';
export const useLocale = () => useSyncExternalStore(subscribeLocale, getLocale, getLocale);
