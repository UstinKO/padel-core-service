import { Client, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type Listener = { changed: () => void; connected: () => void };
export type Connection = 'idle' | 'reconnecting' | 'connected';

export class LiveConnection {
  private client: Client | null = null;
  private listeners = new Map<string, Set<Listener>>();
  private subscriptions = new Map<string, StompSubscription>();
  private observers = new Set<() => void>();
  private status: Connection = 'idle';
  private connectedAt = 0;
  connectedTime = () => this.connectedAt;
  private stopTimer: ReturnType<typeof setTimeout> | undefined;
  private stopping: Promise<void> | null = null;
  constructor(
    private factory = () =>
      new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5000,
        connectionTimeout: 10000,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 10000,
      }),
  ) {}
  state = () => this.status;
  observe = (listener: () => void) => {
    this.observers.add(listener);
    return () => {
      this.observers.delete(listener);
    };
  };
  private update(status: Connection) {
    this.status = status;
    this.observers.forEach((listener) => listener());
  }
  subscribe(topic: string, changed: () => void, connected: () => void) {
    const listener = { changed, connected };
    const listeners = this.listeners.get(topic) ?? new Set<Listener>();
    listeners.add(listener);
    this.listeners.set(topic, listeners);
    clearTimeout(this.stopTimer);
    this.start();
    if (this.client?.connected) this.subscribeTopic(topic);
    return () => {
      listeners.delete(listener);
      if (!listeners.size) {
        this.listeners.delete(topic);
        if (this.client?.connected) this.subscriptions.get(topic)?.unsubscribe();
        this.subscriptions.delete(topic);
      }
      if (!this.listeners.size)
        this.stopTimer = setTimeout(() => {
          void this.stop();
        }, 0);
    };
  }
  private subscribeTopic(topic: string) {
    if (!this.client?.connected || this.subscriptions.has(topic)) return;
    this.subscriptions.set(
      topic,
      this.client.subscribe(topic, () =>
        this.listeners.get(topic)?.forEach((listener) => listener.changed()),
      ),
    );
  }
  private start() {
    if (this.client || this.stopping || !this.listeners.size) return;
    const client = this.factory();
    this.client = client;
    this.update('reconnecting');
    client.onConnect = () => {
      if (this.client !== client) return;
      this.connectedAt = Date.now();
      this.subscriptions.clear();
      for (const [topic, listeners] of this.listeners) {
        this.subscribeTopic(topic);
        listeners.forEach((listener) => listener.connected());
      }
      this.update('connected');
    };
    const lost = () => {
      if (this.client === client) this.update('reconnecting');
    };
    client.onWebSocketClose = lost;
    client.onStompError = lost;
    client.onWebSocketError = lost;
    client.activate();
  }
  private async stop() {
    if (this.listeners.size || !this.client) return;
    const client = this.client;
    this.client = null;
    this.subscriptions.clear();
    this.update('idle');
    this.stopping = client.deactivate({ force: true });
    try {
      await this.stopping;
    } finally {
      this.stopping = null;
      this.start();
    }
  }
}
export const liveConnection = new LiveConnection();
