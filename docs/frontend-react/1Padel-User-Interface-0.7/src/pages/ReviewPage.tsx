import { useLocale } from '../i18n/react';
import { translate } from '../i18n/translate';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useApp } from '../AppContext';
import { scenarios } from '../data/scenarios';
import { Icon } from '../components/Icon';
export function PreviewPage() { const { id } = useParams(); const { setDemoScenario } = useApp(); const navigate = useNavigate(); const opened = useRef(''); useEffect(() => { if (opened.current === id)
    return; opened.current = id || ''; const scenario = scenarios.find(s => s.id === (id === 'verify-email' ? 'register' : id === 'offer-expired' ? 'offer-unavailable' : id)); if (!scenario) {
    navigate('/review', { replace: true });
    return;
} setDemoScenario(scenario.fixture || 'guest'); navigate(scenario.path, { replace: true, state: scenario.navigationState }); }, [id, setDemoScenario, navigate]); return <div className="state-panel" role="status">Открываем пример…</div>; }
export function ReviewPage() { const { signOut, notify } = useApp(); const [query, setQuery] = useState(''); const locale=useLocale(); const shown = scenarios.filter(s => (s.title + ' ' + s.group+' '+translate(s.title,locale)+' '+translate(s.group,locale)).toLowerCase().includes(query.toLowerCase())); const groups = [...new Set(shown.map(s => s.group))]; return <section className="review-page view-space"><div className="eyebrow">1-Padel · пользовательская часть · 0.7 · 27 октября 2026</div><h1 className="page-title">Все экраны.<br />Все пути к игре.</h1><p className="review-lead">Каждая ссылка открывает готовый пример с нужным статусом игрока. Настоящий вход не нужен. Данные вымышлены, заявки и платежи не отправляются на действующий сайт.</p><div className="review-actions"><Link className="btn" to="/preview/home">Начать как новый посетитель <Icon name="arrow"/></Link><Link className="btn secondary" to="/preview/games">Открыть кабинет игрока</Link><button className="text-btn" onClick={() => { signOut(); notify('Демопрофиль и записи сброшены'); }}>Сбросить демоданные</button></div><p className="review-lead">{scenarios.length} примеров · страницы, состояния и окна. Это пользовательская часть; административное проведение турниров в этот комплект не входит.</p><div className="review-actions"><a className="btn secondary" href="handoff/links.txt" download>Скачать все ссылки</a><a className="text-btn" href="handoff/guide.txt" download>Как проверять макет</a></div><label className="review-search">Найти экран<input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="Например: Google, пары, резерв"/></label>{groups.map(group => <section key={group} className="review-group"><h2>{group}</h2><div className="review-grid">{shown.filter(s => s.group === group).map(s => <article className="review-card" key={s.id}><h3>{s.title}</h3>{s.description && <p>{s.description}</p>}<Link className="text-btn" to={'/preview/' + s.id}>Открыть пример <Icon name="arrow"/></Link><button className="text-btn small-link" onClick={async () => { const url = location.origin + location.pathname + '?lang='+locale+'#/preview/' + s.id; try {
    await navigator.clipboard.writeText(url);
    notify('Ссылка на этот пример скопирована');
}
catch {
    notify('Откройте пример и скопируйте адрес из браузера');
} }}>Копировать ссылку</button></article>)}</div></section>)}{!shown.length && <p className="state-panel">Такого примера нет. Попробуйте другое слово.</p>}<p className="prototype-note">React + TypeScript. Экран проверки служебный: перед запуском нужно подключить действующие API, права доступа и серверные проверки. Демонстрационные сценарии удаляются из пользовательской навигации.</p></section>; }
