(() => {
    const link = document.getElementById('calendarReturn');
    const eventId = location.pathname.match(/^\/admin\/tournaments\/(\d+)$/)?.[1];
    if (!link || !eventId) return;
    const key = `admin-calendar-return-${eventId}`;
    let source = new URLSearchParams(location.search).get('calendarReturn');
    try {source ||= sessionStorage.getItem(key);} catch { /* Настройки браузера могут запрещать хранение. */ }
    if (!source) return;
    let target;
    try {target = new URL(source, location.origin);} catch {return;}
    if (target.origin !== location.origin || target.pathname !== '/admin/tournaments' || target.searchParams.get('view') !== 'calendar') return;
    try {sessionStorage.setItem(key,target.pathname + target.search);} catch { /* Возврат по ссылке остаётся доступен. */ }
    link.href = target.pathname + target.search; link.hidden = false; link.style.display = '';
})();
