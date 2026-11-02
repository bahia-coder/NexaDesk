import { Role } from '../models/user.models';

/** The signed-in user (backend: user.dto.CurrentUserDto). */
export interface CurrentUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: Role;
  department: string | null;
  jobTitle: string | null;
  avatarColor: string;
  /** Temporary password: every API call but the password change is refused until it is changed. */
  mustChangePassword: boolean;
}

/** Body of login, refresh and password change. The refresh token itself stays in an httpOnly cookie. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: CurrentUser;
}

/** One-click demo login offered on the sign-in page (backend profile "demo" only). */
export interface DemoAccount {
  role: Role;
  label: string;
  email: string;
  password: string;
}
