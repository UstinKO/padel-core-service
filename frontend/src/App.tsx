import { lazy, Suspense } from 'react';
import { AppErrorBoundary, RouteErrorPage, SystemPage } from './components/SystemState';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';

import { AccountPage } from './pages/AccountPages';
import { AppLayout } from './components/AppLayout';
import { ComponentsPage } from './pages/ComponentsPage';
import { HomePage, CatalogPage } from './pages/DiscoveryPages';
import { EventPage } from './pages/EventPage';
import { ClubsPage, ClubPage } from './pages/ClubPages';
import { NotFoundPage } from './components/EntityState';
import { PreviewPage } from './pages/PreviewPage';

const LivePage = lazy(() =>
  import('./pages/LivePage').then((module) => ({ default: module.LivePage })),
);

const LegalPage = lazy(() =>
  import('./pages/LegalPages').then((module) => ({ default: module.LegalPage })),
);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchOnWindowFocus: false },
  },
});

const basename = window.location.pathname.startsWith('/preview') ? '/preview' : '/';
const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AppLayout />,
      errorElement: <RouteErrorPage />,
      children: [
        { index: true, element: <HomePage /> },
        { path: 'torneos', element: <CatalogPage /> },
        { path: 'torneo/:id', element: <EventPage /> },
        ...(
          [
            ['torneo/:id/king-of-court', 'KING_OF_COURT', false],
            ['tournaments/king-of-court/:id', 'KING_OF_COURT', true],
            ['tournaments/americano/:id', 'AMERICANO', false],
            ['tournaments/team-playoff/:id', 'TEAM_PLAYOFF', false],
          ] as const
        ).flatMap(([path, expected, alias]) =>
          ['', '/ranking', '/history'].map((suffix) => ({
            path: path + suffix,
            element: (
              <Suspense fallback={<p role="status">…</p>}>
                <LivePage expected={expected} alias={alias} />
              </Suspense>
            ),
          })),
        ),
        { path: 'clubs', element: <ClubsPage /> },
        { path: 'clubs/:id', element: <ClubPage /> },
        { path: 'login', element: <AccountPage key="login" mode="login" /> },
        { path: 'players/registro', element: <AccountPage key="register" mode="register" /> },
        {
          path: 'recuperar-password/solicitar',
          element: <AccountPage key="request" mode="request" />,
        },
        { path: 'recuperar-password', element: <AccountPage key="reset" mode="reset" /> },
        { path: 'perfil', element: <AccountPage key="profile" mode="profile" /> },
        ...(['terminos', 'privacidad', 'cookies'] as const).map((page) => ({
          path: page,
          element: (
            <Suspense fallback={<p role="status">…</p>}>
              <LegalPage page={page} />
            </Suspense>
          ),
        })),
        { path: 'error/401', element: <SystemPage kind="session" /> },
        { path: 'error/403', element: <SystemPage kind="forbidden" /> },
        { path: 'error/429', element: <SystemPage kind="rateLimit" /> },
        {
          path: 'error/503',
          element: <SystemPage kind="unavailable" retry={() => window.location.reload()} />,
        },
        {
          path: 'error/offline',
          element: <SystemPage kind="offline" retry={() => window.location.reload()} />,
        },
        { path: 'foundation', element: <PreviewPage /> },
        { path: 'components', element: <ComponentsPage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ],
  { basename },
);

export function App() {
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </AppErrorBoundary>
  );
}
