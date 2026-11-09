import { StrictMode, Component, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import '@fontsource/manrope/400.css';
import '@fontsource/manrope/500.css';
import '@fontsource/manrope/600.css';
import '@fontsource/manrope/700.css';
import '@fontsource/manrope/800.css';
import './styles/styles.css';
import './styles/welcome.css';
import './styles/guest-flow.css';
import './styles/catalog.css';
import './styles/react.css';
import './styles/auth.css';
import { App } from './App';
import { AppProvider } from './AppContext';
class ErrorBoundary extends Component<{
    children: ReactNode;
}, {
    failed: boolean;
}> {
    state = { failed: false };
    static getDerivedStateFromError() { return { failed: true }; }
    render() { return this.state.failed ? <main className="wrap view-space"><h1>Не удалось открыть страницу</h1><p>Ваши демозаписи сохранены в этом браузере. Попробуйте обновить страницу.</p><button className="btn" onClick={() => location.reload()}>Обновить</button></main> : this.props.children; }
}
createRoot(document.getElementById('root')!).render(<StrictMode><ErrorBoundary><HashRouter><AppProvider><App /></AppProvider></HashRouter></ErrorBoundary></StrictMode>);
import './styles/pages.css';
