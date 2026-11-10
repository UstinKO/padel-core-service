import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';

import { AppLayout } from './components/AppLayout';
import { ComponentsPage } from './pages/ComponentsPage';
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
        { index: true, element: <PreviewPage /> },
        { path: 'components', element: <ComponentsPage /> },
        { path: '*', element: <PreviewPage /> },
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
