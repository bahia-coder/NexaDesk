import { CategoryRef, Instant } from '../../core/models/ticket.models';
import { Role } from '../../core/models/user.models';

// Mirrors com.nexadesk.user.dto.

/** An account as administrators see it (never a password hash). */
export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: Role;
  departmentId: number | null;
  departmentName: string | null;
  jobTitle: string | null;
  phone: string | null;
  avatarColor: string;
  active: boolean;
  mustChangePassword: boolean;
  lockedUntil: Instant | null;
  lastLoginAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface Department {
  id: number;
  name: string;
}

export interface UserFilter {
  search?: string;
  role?: Role;
  active?: boolean;
  departmentId?: number;
}

/** Identity fields; the role has its own endpoint (it ends the user's sessions). */
export interface UserFields {
  email: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  phone: string | null;
  departmentId: number | null;
}

/** Shown once to the admin, who passes it on; the user must change it at first sign-in. */
export interface TemporaryPassword {
  user: User;
  temporaryPassword: string;
}

export interface Profile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: Role;
  department: string | null;
  jobTitle: string | null;
  phone: string | null;
  avatarColor: string;
  lastLoginAt: Instant | null;
}

export type Specialties = CategoryRef[];

/** Same rule as the API (UserFieldRules.PHONE_REGEX). */
export const PHONE_PATTERN = /^[+0-9 ().-]{6,30}$/;

export type AccountState = 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'TEMPORARY_PASSWORD';

/** The one state worth showing in a list: disabled, then locked, then waiting for a password change. */
export function accountState(user: User, now: number): AccountState {
  if (!user.active) {
    return 'DISABLED';
  }
  if (user.lockedUntil && Date.parse(user.lockedUntil) > now) {
    return 'LOCKED';
  }
  return user.mustChangePassword ? 'TEMPORARY_PASSWORD' : 'ACTIVE';
}

export const ACCOUNT_STATE_LABELS: Record<AccountState, string> = {
  ACTIVE: 'Actif',
  DISABLED: 'Désactivé',
  LOCKED: 'Verrouillé',
  TEMPORARY_PASSWORD: 'Mot de passe temporaire',
};
