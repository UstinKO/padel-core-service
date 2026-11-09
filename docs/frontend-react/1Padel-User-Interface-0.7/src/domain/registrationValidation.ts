// Public format constraints from RegistroRequestDto. Abuse heuristics and uniqueness
// remain server decisions; never claim that client validation guarantees acceptance.
export type RegistrationFields = {
  first: string; last: string; email: string; phone: string; telegram: string;
  level: string; locale: string; password: string; confirm: string; terms: boolean;
};
export type RegistrationErrors = Partial<Record<keyof RegistrationFields, string>>;
export function nameError(value: string, label: string): string | undefined {
  const name = value.trim();
  if (!name) return label === 'Фамилия' ? 'Укажите фамилию.' : `Укажите ${label.toLowerCase()}.`;
  if (name.length < 3 || name.length > 100) return `${label}: от 3 до 100 символов. Если ваше настоящее имя короче, обратитесь к организатору — не добавляйте вымышленные буквы.`;
  if (!/^[\p{L} .'-]+$/u.test(name)) return 'Используйте буквы, пробел, дефис, точку или обычный апостроф. Цифры и эмодзи не подходят.';
}
export const validRegistrationPhone = (value: string) => /^\+[0-9\s-]{8,19}$/.test(value.trim()) && value.replace(/\D/g, '').length >= 8;
export function registrationErrors(f: RegistrationFields): RegistrationErrors {
  return {
    first: nameError(f.first, 'Имя'), last: nameError(f.last, 'Фамилия'),
    email: f.email.trim().length <= 150 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim()) ? undefined : 'Введите email в формате nombre@dominio.com, без пробелов, до 150 символов.',
    // Phone is mandatory in the new product brief (the legacy DTO allows phone OR Telegram).
    phone: validRegistrationPhone(f.phone) ? undefined : 'Введите телефон с + и кодом страны: +54 911 5555 0101. Только цифры, пробелы и дефисы, до 20 символов всего.',
    telegram: !f.telegram.trim() || /^@?[A-Za-z][A-Za-z0-9_]{4,31}$/.test(f.telegram.trim()) ? undefined : 'Введите username, а не ссылку: 5–32 латинских символа, начиная с буквы. Можно цифры и _. Знак @ необязателен.',
    level: f.level ? undefined : 'Выберите уровень или «Не знаю».',
    password: f.password.length >= 8 && f.password.length <= 100 ? undefined : 'Нужно от 8 до 100 символов. Заглавная буква, цифра и спецсимвол не обязательны.',
    confirm: !f.confirm ? 'Повторите пароль.' : f.confirm === f.password ? undefined : 'Пароли не совпадают. Повторите тот же пароль; проверьте раскладку и пробелы.',
    terms: f.terms ? undefined : 'Ознакомьтесь с условиями участия и отметьте согласие.'
  };
}
