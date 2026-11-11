export type SpaSessionUserType = 'PLAYER' | 'OWNER';

export interface SpaSession {
  authenticated: boolean;
  userType: SpaSessionUserType | null;
  id: number | null;
  fullName: string | null;
  email: string | null;
  roles: string[];
  clubId: number | null;
  hasValidContact: boolean | null;
  preferredLocale: string | null;
}

export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  fieldErrors?: { field: string; code: string; message: string }[];
  retryAfter?: number;
  blockedUntil?: string;
  canonicalUrl?: string;
}
