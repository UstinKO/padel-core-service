import { mkdir, writeFile } from 'node:fs/promises';
import { scenarios } from '../src/data/scenarios.ts';
const base = 'https://one-padel-play-concept.evgeny-1800.chatgpt.site/';
const groups = [...new Set(scenarios.map(s => s.group))];
const link = s => `${base}#/preview/${s.id}`;
const text = ['1-PADEL · пользовательская часть · 0.7 · 27.10.2026', `Каталог всех экранов: ${base}#/review`, `${scenarios.length} примеров. Ссылки устанавливают демонстрационные данные. Реальный вход не требуется.`, ...groups.flatMap(g => ['\n' + g.toUpperCase(), ...scenarios.filter(s => s.group === g).map(s => `${s.title}\n${link(s)}${s.description ? '\n' + s.description : ''}\n`)])].join('\n\n');
const guide = `1-PADEL · инструкция просмотра · 0.7
Откройте ${base}#/review и выберите пример. Можно искать по названию.
Каждая ссылка /preview/ устанавливает нужную демосессию и заменяет локальные записи. Используйте вымышленные данные.
Обычная ссылка страницы использует уже выбранного игрока. Для воспроизводимого результата пересылайте именно ссылку из каталога.
Ошибки записи показываются после согласия с условиями и нажатия «Подтвердить запись»; ошибки входа и регистрации открываются сразу.
Окна: подтверждение и резерв имеют отдельные ссылки; история игрока открывается нажатием на имя в рейтинге; «Поделиться», меню и правила — одноимёнными кнопками.
Проверьте на телефоне: главная → мероприятие → регистрация → запись → кабинет → проведение. Затем три варианта парной записи, резерв, отмену и исправление формы.
Контакты и заявки сохраняются только в браузере, пароли не сохраняются. «Сбросить демоданные» очищает профиль и заявки.
Это дизайн с локальными сценариями. API, Google, письма, платежи и push не подключены. Нет реальных регистраций.
Лига и общий рейтинг показывают «В разработке». Интерфейс ES/RU/EN; язык браузера определяется автоматически, ручной выбор сохраняется. Документация комплекта на русском.
Юридические тексты требуют решения владельца. Административные страницы и исходники мобильного приложения в этот пакет не входят.
Материалы реализации — в отдельном ZIP, README.md. Архив не заменяет проверку интеграции в закрытом контуре.
`;
await mkdir('public/handoff', { recursive: true });
await Promise.all([
  writeFile('docs/LINKS.txt', text + '\n'),
  writeFile('public/handoff/links.txt', text + '\n'),
  writeFile('public/handoff/guide.txt', guide),
  writeFile('docs/SCENARIOS.json', JSON.stringify(scenarios.map(s => ({...s, url: link(s)})), null, 2) + '\n'),
  writeFile('docs/STATES.md', '# Реестр примеров 0.7\n\nСгенерирован из src/data/scenarios.ts командой pnpm handoff. Всего: ' + scenarios.length + '.\n\n' + groups.map(g => '## ' + g + '\n\n' + scenarios.filter(s => s.group === g).map(s => `- [${s.title}](${link(s)}) — \`${s.path}\`; демосессия: ${s.fixture || 'guest'}.${s.description ? ' ' + s.description : ''}`).join('\n')).join('\n\n') + '\n')
]);
console.log(`Generated ${scenarios.length} previews in ${groups.length} groups.`);
