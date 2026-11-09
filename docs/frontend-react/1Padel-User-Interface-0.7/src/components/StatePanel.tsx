import { Link } from 'react-router-dom';
import { Icon } from './Icon';
export function StatePanel({ title, text, loading = false, onRetry, action = 'Попробовать ещё раз' }: {
    title: string;
    text: string;
    loading?: boolean;
    onRetry?: () => void;
    action?: string;
}) { return <div className={`state-panel ${loading ? 'is-loading' : ''}`} role={loading ? 'status' : undefined}><span className="empty-symbol"><Icon name={loading ? 'clock' : 'info'}/></span><h2>{title}</h2><p>{text}</p>{onRetry && <button className="btn secondary" onClick={onRetry}>{action}</button>}<Link className="text-btn" to="/events">К мероприятиям <Icon name="arrow"/></Link></div>; }
