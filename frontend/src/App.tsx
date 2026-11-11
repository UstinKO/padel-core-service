import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';

import { AppLayout } from './components/AppLayout';
import { ComponentsPage } from './pages/ComponentsPage';
import { HomePage, CatalogPage } from './pages/DiscoveryPages';
import { EventPage } from './pages/EventPage';
import { ClubsPage, ClubPage } from './pages/ClubPages';
import { NotFoundPage } from './components/EntityState';
import { PreviewPage } from './pages/PreviewPage';

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
      children: [
        { index: true, element: <HomePage /> },
        { path: 'torneos', element: <CatalogPage /> },
        { path: 'torneo/:id', element: <EventPage /> },
        { path: 'clubs', element: <ClubsPage /> },
        { path: 'clubs/:id', element: <ClubPage /> },
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
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
