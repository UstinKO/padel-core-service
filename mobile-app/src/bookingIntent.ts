const key = 'onepadel.pendingIndividual.v1';
// Только ID выбранного события, не подтверждение и не личные данные.
export function saveBookingIntent(id: number | null) {
  try {
    if (typeof window === 'undefined') return;
    if (id === null) window.sessionStorage.removeItem(key);
    else window.sessionStorage.setItem(key, JSON.stringify({ id, at: Date.now() }));
  } catch { /* Вход доступен и без sessionStorage; пользователь выберет событие снова. */ }
}
export function readBookingIntent(): number | null {
  try {
    if (typeof window === 'undefined') return null;
    const raw = window.sessionStorage.getItem(key);
    if (!raw) return null;
    const value = JSON.parse(raw);
    if (Number.isSafeInteger(value?.id) && value.id > 0 && typeof value.at === 'number' && Date.now() - value.at >= 0 && Date.now() - value.at < 1800000) return value.id;
  } catch { /* Повреждённые/недоступные данные не создают запись. */ }
  return null;
}
