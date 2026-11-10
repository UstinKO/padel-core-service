import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { getHome } from '../api/discovery';
import { Button } from '../components/Button';
import { Catalogue } from '../components/Catalogue';
import { StatusView } from '../components/StatusView';

export function HomePage() {
  const { t, i18n } = useTranslation();
  const home = useQuery({
    queryKey: ['public-home', i18n.language],
    queryFn: ({ signal }) => getHome(signal),
    retry: false,
  });
  return (
    <div className="discovery page-stack">
      <section className="welcome-hero" aria-labelledby="welcome-title">
        <p className="kicker">{t('home.kicker')}</p>
        <h1 id="welcome-title">
          {t('home.title')}
          <br />
          <em>{t('home.titleAccent')}</em>
        </h1>
        <p>{t('home.lead')}</p>
        <a className="button button--primary" href="#events-catalog">
          {t('home.choose')}
        </a>
      </section>
      {home.isPending ? (
        <p role="status">{t('catalog.loading')}</p>
      ) : home.isError ? (
        <StatusView
          tone="error"
          title={t('state.errorTitle')}
          text={t('state.errorText')}
          action={<Button onClick={() => void home.refetch()}>{t('state.retry')}</Button>}
        />
      ) : (
        <dl className="home-stats">
          {(['totalClubs', 'totalPlayers', 'totalTournaments'] as const).map((key) => (
            <div key={key}>
              <dt>{t(`home.${key}`)}</dt>
              <dd>{new Intl.NumberFormat(i18n.language).format(home.data[key])}</dd>
            </div>
          ))}
        </dl>
      )}
      <Catalogue />
      <section className="welcome-start">
        <div>
          <p className="kicker">{t('home.stepsKicker')}</p>
          <h2>{t('home.stepsTitle')}</h2>
        </div>
        <ol>
          {[1, 2, 3].map((step) => (
            <li key={step}>
              <span aria-hidden="true">0{step}</span>
              <div>
                <h3>{t(`home.step${step}Title`)}</h3>
                <p>{t(`home.step${step}Text`)}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>
      <section className="welcome-faq">
        <div>
          <p className="kicker">{t('home.faqKicker')}</p>
          <h2>{t('home.faqTitle')}</h2>
          <Link to="/torneos">{t('nav.catalog')} →</Link>
        </div>
        <div>
          {[1, 2, 3].map((item) => (
            <details key={item}>
              <summary>{t(`home.faq${item}Title`)}</summary>
              <p>{t(`home.faq${item}Text`)}</p>
            </details>
          ))}
        </div>
      </section>
    </div>
  );
}

export function CatalogPage() {
  const { t } = useTranslation();
  return (
    <div className="page-stack">
      <header className="page-heading">
        <p className="kicker">{t('home.kicker')}</p>
        <h1>{t('catalog.pageTitle')}</h1>
        <p>{t('catalog.pageLead')}</p>
      </header>
      <Catalogue />
    </div>
  );
}
