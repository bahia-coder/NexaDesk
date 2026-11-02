// Types mirroring the user DTOs of the API (backend: com.nexadesk.user).

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employé',
  TECHNICIAN: 'Technicien',
  ADMIN: 'Administrateur',
};
