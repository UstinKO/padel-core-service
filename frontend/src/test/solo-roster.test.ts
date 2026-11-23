import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fireEvent, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

const code = readFileSync(resolve('../src/main/resources/static/js/solo-roster.js'), 'utf8');
const sideCode = readFileSync(resolve('../src/main/resources/static/js/playing-side.js'), 'utf8');
const fragment = readFileSync(
  resolve('../src/main/resources/templates/fragments/solo-roster.html'),
  'utf8',
);
const a = {
  registrationId: 1,
  playerId: 101,
  name: 'Ana <script>',
  level: 'C7',
  playingSide: 'RIGHT',
  status: 'SOLO_SEARCH',
  reserve: true,
  attended: true,
  payments: [{ amount: 100, currency: 'ARS', status: 'PAID', method: 'CASH' }],
};
const b = {
  ...a,
  registrationId: 2,
  playerId: 102,
  name: 'Bea',
  attended: false,
  payments: [{ amount: 20, currency: 'USD', status: 'PAID', method: 'BANK_TRANSFER' }],
};
let calls: { path: string; body: Record<string, unknown> | undefined }[];
let failWrite: boolean;
let blockWrite: boolean;
let failPreview: boolean;
function response(value: unknown) {
  return {
    ok: true,
    redirected: false,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => value,
  };
}
beforeEach(async () => {
  calls = [];
  failWrite = false;
  blockWrite = false;
  failPreview = false;
  document.body.innerHTML =
    fragment.replace(/<script[\s\S]*?<\/script>/g, '') +
    '<form id="paymentForm"><input name="amount"><button type="submit">Save</button></form>';
  document.getElementById('solo-roster')!.dataset.eventId = '99';
  Object.assign(window, { soloRosterMessages: new Proxy({}, { get: (_, key) => String(key) }) });
  Object.assign(window, {
    playingSideMessages: {
      LEFT: 'Слева',
      RIGHT: 'Справа',
      EITHER: 'Обе стороны',
      missing: 'Сторона не указана',
    },
  });
  new Function(sideCode)();
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string, options: RequestInit) => {
      const body = options.body
        ? (JSON.parse(String(options.body)) as Record<string, unknown>)
        : undefined;
      calls.push({ path, body });
      if (path.endsWith('/preview') && failPreview) throw new Error('Please check the phone');
      if (path.endsWith('/preview'))
        return response({
          state: 'checked',
          participants: [a, b],
          resultingStatus: 'WAITLIST',
          levelOverrideRequired: false,
        });
      if (body) {
        if (blockWrite) await new Promise(() => {});
        if (failWrite) throw new Error('Connection lost');
        return response({ status: 'WAITLIST' });
      }
      if (path.includes('/search?'))
        return response([{ ...b, single: true, name: 'Bea <img onerror=alert(1)>' }]);
      return response({ singles: [a, b], cancelled: [], history: [] });
    }),
  );
  new Function(code)();
  await waitFor(() => expect(document.querySelectorAll('#solo-list article')).toHaveLength(2));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
async function reviewPair() {
  fireEvent.click(document.querySelector('#solo-list article button:nth-child(2)')!);
  fireEvent.change(document.getElementById('solo-single')!, { target: { value: '2' } });
  fireEvent.submit(document.getElementById('solo-form')!);
  await waitFor(() =>
    expect(document.getElementById('solo-preview')).not.toHaveAttribute('hidden'),
  );
}
test('preview shows both currencies and individual attendance without interpreting names as markup', async () => {
  expect(document.querySelector('#solo-list h3')).toHaveTextContent('Ana <script>');
  expect(document.querySelector('#solo-list script')).toBeNull();
  await reviewPair();
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('100 · ARS');
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('20 · USD');
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('present');
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('absent');
  expect(calls.at(-1)?.body).toMatchObject({
    action: 'PAIR',
    registrationId: 1,
    partnerRegistrationId: 2,
  });
});
test('unsaved payment changes prevent losing the form when opening a roster action', () => {
  fireEvent.input(document.querySelector('#paymentForm input')!, { target: { value: '500' } });
  fireEvent.click(document.querySelector('[data-roster-action=ADD]')!);
  expect(document.getElementById('solo-dialog')).not.toHaveAttribute('open');
  expect(document.getElementById('solo-notice')).toHaveTextContent('unsaved');
  expect(document.querySelector<HTMLInputElement>('#paymentForm input')!.value).toBe('500');
});
test('double click sends one mutation and keeps the preview while it is pending', async () => {
  await reviewPair();
  blockWrite = true;
  fireEvent.click(document.getElementById('solo-confirm')!);
  fireEvent.click(document.getElementById('solo-confirm')!);
  await waitFor(() =>
    expect(calls.filter((call) => call.body && !call.path.endsWith('/preview'))).toHaveLength(1),
  );
  expect(document.getElementById('solo-confirm')).toBeDisabled();
});
test('retry after a lost response sends the identical request ID and payload', async () => {
  await reviewPair();
  failWrite = true;
  fireEvent.click(document.getElementById('solo-confirm')!);
  await waitFor(() =>
    expect(document.getElementById('solo-error')).toHaveTextContent('Connection lost'),
  );
  fireEvent.click(document.getElementById('solo-confirm')!);
  await waitFor(() =>
    expect(calls.filter((call) => call.body && !call.path.endsWith('/preview'))).toHaveLength(2),
  );
  const writes = calls.filter((call) => call.body && !call.path.endsWith('/preview'));
  expect(writes[1].body).toEqual(writes[0].body);
  expect(document.getElementById('solo-dialog')).toHaveAttribute('open');
});

test('a preview error preserves guest fields and does not submit a mutation', async () => {
  fireEvent.click(document.querySelector('[data-roster-action=ADD]')!);
  fireEvent.change(document.getElementById('solo-mode')!, { target: { value: 'guest' } });
  fireEvent.input(document.getElementById('solo-first')!, { target: { value: 'Nora' } });
  fireEvent.input(document.getElementById('solo-last')!, { target: { value: 'Guest' } });
  fireEvent.input(document.getElementById('solo-phone')!, { target: { value: 'bad number' } });
  failPreview = true;
  fireEvent.submit(document.getElementById('solo-form')!);
  await waitFor(() =>
    expect(document.getElementById('solo-error')).toHaveTextContent('Please check the phone'),
  );
  expect(document.querySelector<HTMLInputElement>('#solo-first')!.value).toBe('Nora');
  expect(document.querySelector<HTMLInputElement>('#solo-phone')!.value).toBe('bad number');
  expect(calls.filter((call) => call.body && !call.path.endsWith('/preview'))).toHaveLength(0);
});

test('the source and single candidate retain their level and side before preview', async () => {
  fireEvent.click(document.querySelector('#solo-list article button:nth-child(2)')!);
  expect(document.getElementById('solo-source')).toHaveTextContent('Ana <script> · C7 · Справа');
  const candidate = document.querySelector<HTMLOptionElement>('#solo-single option[value="2"]')!;
  expect(candidate).toHaveTextContent('Bea · C7 · Справа · looking');
  fireEvent.change(document.getElementById('solo-single')!, { target: { value: '2' } });
  expect(document.getElementById('solo-single-selected')).toHaveTextContent('Bea · C7 · Справа');
  await reviewPair();
  expect(document.querySelectorAll('#solo-preview-people article')).toHaveLength(2);
  expect(document.getElementById('solo-level-warning')).toHaveAttribute('hidden');
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('100 · ARS');
  expect(document.getElementById('solo-preview-people')).toHaveTextContent('20 · USD');
  expect(calls.at(-1)?.body).toMatchObject({
    action: 'PAIR',
    partnerRegistrationId: 2,
    player: null,
  });
});

test.each([
  ['ru', { LEFT: 'Слева', RIGHT: 'Справа', EITHER: 'Обе стороны', missing: 'Сторона не указана' }],
  [
    'es',
    { LEFT: 'Izquierda', RIGHT: 'Derecha', EITHER: 'Ambos lados', missing: 'Lado sin indicar' },
  ],
  ['en', { LEFT: 'Left', RIGHT: 'Right', EITHER: 'Either side', missing: 'Side not specified' }],
])('search and selected profile use the shared side formatter in %s', async (_, labels) => {
  Object.assign(window, { playingSideMessages: labels });
  new Function(sideCode)();
  fireEvent.click(document.querySelector('#solo-list article button')!);
  fireEvent.input(document.getElementById('solo-search')!, { target: { value: 'Bea' } });
  await waitFor(() =>
    expect(document.getElementById('solo-search-results')).toHaveTextContent(
      `C7 · ${labels.RIGHT}`,
    ),
  );
  expect(document.querySelector('#solo-search-results img')).toBeNull();
  fireEvent.click(document.querySelector('#solo-search-results button')!);
  expect(document.getElementById('solo-selected')).toHaveTextContent(
    `Bea <img onerror=alert(1)> · C7 · ${labels.RIGHT}`,
  );
  fireEvent.submit(document.getElementById('solo-form')!);
  await waitFor(() =>
    expect(document.getElementById('solo-preview')).not.toHaveAttribute('hidden'),
  );
  expect(calls.at(-1)?.body?.player).toEqual({ id: 102 });
});

test.each([
  ['LEFT', 'Слева'],
  ['RIGHT', 'Справа'],
  ['EITHER', 'Обе стороны'],
  [null, 'Сторона не указана'],
])('a candidate with side %s preserves its own preference label', async (side, label) => {
  const original = b.playingSide;
  Object.assign(b, { playingSide: side });
  try {
    fireEvent.click(document.querySelector('#solo-list article button:nth-child(2)')!);
    expect(document.querySelector('#solo-single option[value="2"]')).toHaveTextContent(
      `C7 · ${label}`,
    );
    if (side === null)
      expect(document.querySelector('#solo-single option[value="2"]')).not.toHaveTextContent(
        'Обе стороны',
      );
  } finally {
    Object.assign(b, { playingSide: original });
  }
});
