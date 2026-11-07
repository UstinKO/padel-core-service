import { AuthError } from './auth.ts';
import { eventDate } from './schedule.ts';
import type { Language } from './schedule.ts';

export type PairOffer = {
  token: string; version: number; eventName: string; startsAt: string; freeUntil: string;
  pricePerPlayer: number; pairPrice: number; lateObligation: number; currency: string;
  category: string | null; regulationUrl: string | null;
};
export type PairCancellation = {
  token: string; legacy: boolean; late: boolean; canLeave: boolean; canReplace: boolean; freeUntil: string | null;
  currency: string | null; obligation: number | null; alreadyPaid: number | null;
  remainingDue: number | null; refundable: number | null; paymentInstructions: string | null; organizerContact: string | null;
};

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new AuthError('UNAVAILABLE');
  return value as Record<string, unknown>;
}
function validMoney(value: unknown) { return typeof value === 'number' && Number.isFinite(value) && value >= 0; }
function validDate(value: unknown) { return typeof value === 'string' && Number.isFinite(Date.parse(value)); }
function nullableText(value: unknown) { return value === null || typeof value === 'string'; }
function validToken(value: unknown) { return typeof value === 'string' && /^[0-9a-f-]{36}$/.test(value); }
function validCurrency(value: unknown) { return typeof value === 'string' && /^(?:[A-Z]{3}|USDT)$/.test(value); }

export function parsePairOffer(value: unknown): PairOffer | null {
  if (value === null) return null;
  const row = object(value);
  if (!validToken(row.token) || !Number.isSafeInteger(row.version) || typeof row.eventName !== 'string'
      || !validDate(row.startsAt) || !validDate(row.freeUntil) || !validCurrency(row.currency)
      || !nullableText(row.category) || !nullableText(row.regulationUrl)
      || ![row.pricePerPlayer, row.pairPrice, row.lateObligation].every(validMoney)) throw new AuthError('UNAVAILABLE');
  return row as PairOffer;
}

export function parseBookingTerms(value: unknown) {
  const row = object(value);
  if (typeof row.accepted !== 'boolean' || typeof row.canAccept !== 'boolean') throw new AuthError('UNAVAILABLE');
  return { terms: parsePairOffer(row.terms), accepted: row.accepted, canAccept: row.canAccept };
}

export function parsePairCancellation(value: unknown): PairCancellation {
  const row = object(value);
  if (!validToken(row.token) || typeof row.legacy !== 'boolean' || typeof row.late !== 'boolean' || typeof row.canLeave !== 'boolean'
      || !nullableText(row.paymentInstructions) || !nullableText(row.organizerContact)
      || (!row.legacy && (!validDate(row.freeUntil) || !validCurrency(row.currency)
        || ![row.obligation, row.alreadyPaid, row.remainingDue, row.refundable].every(validMoney)))) throw new AuthError('UNAVAILABLE');
  return { ...row, canReplace: row.canReplace === true } as PairCancellation;
}

export const pairTermsCopy = {
  ru: { title: 'Условия записи пары', accept: 'Принять новые условия', accepted: 'Условия приняты', cancelled: 'Пара снята с турнира', terms: 'Посмотреть условия', review: 'Отказаться от участия', legacy: 'Для этой записи действуют прежние условия.', free: 'Бесплатная отмена до (включительно)', player: 'Цена за игрока', pair: 'Цена за пару', late: 'При поздней отмене или неявке', obligation: 'Стоимость отмены', paid: 'Уже оплачено', due: 'К оплате', refund: 'К возврату через организатора', cancel: 'Отменить всю пару', cancelHelp: 'Оба игрока будут сняты с турнира.', leave: 'Выйти из пары', leaveHelp: 'Партнёр сохраняет место и может добавить замену.', left: 'Вы вышли из пары. Место партнёра сохранено.', back: 'Закрыть', contact: 'Свяжитесь с организатором.', failed: 'Не удалось выполнить действие. Обновите условия и попробуйте снова.', rules: 'Регламент', arrival: 'Приезжайте заранее. При опоздании первый матч может начаться со счёта 0:3 или быть засчитан как проигрыш.', replacement: 'Если место займёт и оплатит другая пара, организатор снимет обязательство и оформит возврат.' },
  es: { title: 'Condiciones de la pareja', accept: 'Aceptar las nuevas condiciones', accepted: 'Condiciones aceptadas', cancelled: 'Inscripción de la pareja cancelada', terms: 'Ver condiciones', review: 'Renunciar a mi participación', legacy: 'Se aplican las condiciones anteriores de esta inscripción.', free: 'Cancelación sin cargo hasta (inclusive)', player: 'Precio por jugador', pair: 'Precio por pareja', late: 'Cancelación tardía o inasistencia', obligation: 'Importe de cancelación', paid: 'Ya pagado', due: 'Pendiente de pago', refund: 'A devolver por la organización', cancel: 'Cancelar toda la pareja', cancelHelp: 'Ambos jugadores quedan fuera del torneo.', leave: 'Salir de la pareja', leaveHelp: 'Tu compañero conserva su lugar y puede agregar un reemplazo.', left: 'Saliste de la pareja. Tu compañero conserva su lugar.', back: 'Cerrar', contact: 'Contactá a la organización.', failed: 'No se pudo completar la acción. Actualizá las condiciones e intentá de nuevo.', rules: 'Reglamento', arrival: 'Lleguen con anticipación. Si llegan tarde, el primer partido puede comenzar 0:3 o darse por perdido.', replacement: 'Si otra pareja ocupa y paga el lugar, la organización anula el importe pendiente y gestiona la devolución.' },
  en: { title: 'Pair booking terms', accept: 'Accept the new terms', accepted: 'Terms accepted', cancelled: 'Pair registration cancelled', terms: 'View terms', review: 'Withdraw from the tournament', legacy: 'This booking is covered by its previous terms.', free: 'Free cancellation through', player: 'Price per player', pair: 'Price per pair', late: 'Late cancellation or no-show', obligation: 'Cancellation amount', paid: 'Already paid', due: 'Amount due', refund: 'Refund through the organizer', cancel: 'Cancel the whole pair', cancelHelp: 'Both players will be removed from the tournament.', leave: 'Leave the pair', leaveHelp: 'Your partner keeps their place and can add a replacement.', left: 'You left the pair. Your partner keeps their place.', back: 'Close', contact: 'Contact the organizer.', failed: 'Could not complete the action. Refresh the terms and retry.', rules: 'Rules', arrival: 'Arrive early. If you arrive late, the first match may start at 0:3 or be recorded as a loss.', replacement: 'If another pair takes and pays for your place, the organizer waives the amount due and handles any refund.' },
} as const;

const zone = 'America/Argentina/Buenos_Aires';
function money(value: number, currency: string, language: Language) {
  return currency === 'USDT' ? new Intl.NumberFormat(language).format(value) + ' USDT'
    : new Intl.NumberFormat(language, { style: 'currency', currency }).format(value);
}
const termsSectionTitles = {
  ru: { payment: 'Отмена и возврат', arrival: 'Прибытие и опоздание' },
  es: { payment: 'Cancelación y devolución', arrival: 'Llegada y demoras' },
  en: { payment: 'Cancellation and refunds', arrival: 'Arrival and lateness' },
};

export function offerSections(offer: PairOffer, language: Language) {
  const t = pairTermsCopy[language];
  const headings = termsSectionTitles[language];
  return {
    event: [offer.eventName, eventDate(offer.startsAt, zone, language), offer.category,
      t.player + ': ' + money(offer.pricePerPlayer, offer.currency, language),
      t.pair + ': ' + money(offer.pairPrice, offer.currency, language)]
      .filter((line): line is string => Boolean(line)),
    sections: [
      { title: headings.payment, lines: [
        t.free + ': ' + eventDate(offer.freeUntil, zone, language),
        t.late + ': ' + money(offer.lateObligation, offer.currency, language), t.replacement,
      ] },
      { title: headings.arrival, lines: [t.arrival] },
    ],
  };
}
export function cancellationLines(quote: PairCancellation, language: Language) {
  const t = pairTermsCopy[language];
  if (quote.legacy || !quote.currency || !quote.freeUntil) return [t.legacy];
  const amount = (label: string, value: number | null) => label + ': ' + money(value ?? 0, quote.currency!, language);
  return [t.free + ': ' + eventDate(quote.freeUntil, zone, language), amount(t.obligation, quote.obligation),
    amount(t.paid, quote.alreadyPaid), amount(t.due, quote.remainingDue), amount(t.refund, quote.refundable),
    (quote.remainingDue ?? 0) > 0 ? quote.paymentInstructions || quote.organizerContact || t.contact : ''].filter(Boolean);
}

export const pairExitCopy = {
  ru: {
    choose: 'Что сделать с записью пары?', self: 'Выйти самому', full: 'Снять всю пару',
    inReserve: 'Партнёр сохранит место в очереди и сможет добавить замену.',
    leftInReserve: 'Вы вышли из пары. Место партнёра в очереди сохранено.',
    confirmSelf: 'Подтвердить мой выход', confirmFull: 'Подтвердить отмену пары',
    replace: 'У меня уже есть замена', replaceTitle: 'Кто сыграет вместо вас?',
    replaceHelp: 'Укажите игрока, с которым уже договорились. Он займёт ваше место в паре.',
    first: 'Имя', last: 'Фамилия', phone: 'Телефон или WhatsApp',
    selectedPlayer: 'Выбран игрок из базы', changePlayer: 'Выбрать другого игрока',
    confirmReplacement: 'Подтвердить замену', replaced: 'Вы вышли из пары. Замена записана.',
    back: 'Назад', loading: 'Загружаем варианты…', retry: 'Обновить варианты',
  },
  es: {
    choose: '¿Qué querés hacer con la inscripción?', self: 'Salir yo de la pareja', full: 'Dar de baja a toda la pareja',
    inReserve: 'Tu compañero conserva su lugar en la lista de espera y puede agregar un reemplazo.',
    leftInReserve: 'Saliste de la pareja. Tu compañero conserva su lugar en la lista de espera.',
    confirmSelf: 'Confirmar mi salida', confirmFull: 'Confirmar la baja de la pareja',
    replace: 'Ya tengo un reemplazo', replaceTitle: '¿Quién va a jugar en tu lugar?',
    replaceHelp: 'Ingresá los datos del jugador con quien ya acordaste. Va a ocupar tu lugar en la pareja.',
    first: 'Nombre', last: 'Apellido', phone: 'Teléfono o WhatsApp',
    selectedPlayer: 'Jugador seleccionado', changePlayer: 'Elegir otro jugador',
    confirmReplacement: 'Confirmar reemplazo', replaced: 'Saliste de la pareja. El reemplazo quedó inscrito.',
    back: 'Volver', loading: 'Cargando opciones…', retry: 'Actualizar opciones',
  },
  en: {
    choose: 'What should happen to the pair booking?', self: 'Withdraw just myself', full: 'Withdraw the whole pair',
    inReserve: 'Your partner keeps their place on the waitlist and can add a replacement.',
    leftInReserve: 'You left the pair. Your partner keeps their place on the waitlist.',
    confirmSelf: 'Confirm my withdrawal', confirmFull: 'Confirm pair cancellation',
    replace: 'I already have a replacement', replaceTitle: 'Who will play in your place?',
    replaceHelp: 'Enter the details of the player who agreed to replace you. They will take your place in the pair.',
    first: 'First name', last: 'Last name', phone: 'Phone or WhatsApp',
    selectedPlayer: 'Selected player', changePlayer: 'Choose another player',
    confirmReplacement: 'Confirm replacement', replaced: 'You left the pair. Your replacement is registered.',
    back: 'Back', loading: 'Loading options…', retry: 'Refresh options',
  },
} as const;

export const replacePartnerCopy = {
  ru: {
    title: 'Заменить партнёра', currentPartner: 'Текущий партнёр',
    help: 'Ваше место в турнире сохраняется. Выберите игрока, с которым договорились о замене.',
    submit: 'Подтвердить замену', complete: 'Партнёр заменён. Ваше место сохранено.',
    notice: 'Вас заменили в паре', noticeBody: '{booker} заменил вас на игрока {replacement}. Вы не участвуете в турнире.', dismiss: 'Прочитано',
    failed: 'Не удалось получить результат. Обновите карточку и проверьте состав пары.',
    refresh: 'Обновить карточку', close: 'Закрыть',
  },
  es: {
    title: 'Cambiar de compañero', currentPartner: 'Compañero actual',
    help: 'Conservás tu lugar en el torneo. Elegí al jugador con quien acordaste el cambio.',
    submit: 'Confirmar cambio', complete: 'El compañero cambió. Conservás tu lugar.',
    notice: 'Te reemplazaron en la pareja', noticeBody: '{booker} confirmó el reemplazo por {replacement}. Ya no participás en el torneo.', dismiss: 'Marcar como leído',
    failed: 'No pudimos obtener el resultado. Actualizá la inscripción y revisá la pareja.',
    refresh: 'Actualizar inscripción', close: 'Cerrar',
  },
  en: {
    title: 'Replace my partner', currentPartner: 'Current partner',
    help: 'You keep your place in the tournament. Choose the player who agreed to join you.',
    submit: 'Confirm replacement', complete: 'Partner replaced. You keep your place.',
    notice: 'You were replaced in the pair', noticeBody: '{booker} confirmed {replacement} as your replacement. You are no longer taking part in the tournament.', dismiss: 'Mark as read',
    failed: 'We could not get the result. Refresh the booking and check your partner.',
    refresh: 'Refresh booking', close: 'Close',
  },
} as const;
