import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { Page } from '../../core/models/page.models';
import { CategoryRef } from '../../core/models/ticket.models';
import { Role } from '../../core/models/user.models';
import {
  Department,
  Profile,
  TemporaryPassword,
  User,
  UserFields,
  UserFilter,
} from './users.models';

/** Account administration (admin only on the API) and the signed-in user's own profile. */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  /** Errors shown by the calling form or action. */
  private readonly own = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  list(filter: UserFilter, page: number, size: number, sort: string): Observable<Page<User>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<User>>('/api/users', { params });
  }

  get(id: number): Observable<User> {
    return this.http.get<User>(`/api/users/${id}`);
  }

  create(fields: UserFields, role: Role): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>('/api/users', { ...fields, role }, this.own);
  }

  update(id: number, fields: UserFields): Observable<User> {
    return this.http.put<User>(`/api/users/${id}`, fields, this.own);
  }

  changeRole(id: number, role: Role): Observable<User> {
    return this.http.put<User>(`/api/users/${id}/role`, { role }, this.own);
  }

  deactivate(id: number): Observable<User> {
    return this.http.post<User>(`/api/users/${id}/deactivate`, null, this.own);
  }

  activate(id: number): Observable<User> {
    return this.http.post<User>(`/api/users/${id}/activate`, null, this.own);
  }

  resetPassword(id: number): Observable<TemporaryPassword> {
    return this.http.post<TemporaryPassword>(`/api/users/${id}/reset-password`, null, this.own);
  }

  specialties(id: number): Observable<CategoryRef[]> {
    return this.http.get<CategoryRef[]>(`/api/users/${id}/specialties`);
  }

  setSpecialties(id: number, categoryIds: number[]): Observable<CategoryRef[]> {
    return this.http.put<CategoryRef[]>(`/api/users/${id}/specialties`, { categoryIds }, this.own);
  }

  departments(): Observable<Department[]> {
    return this.http.get<Department[]>('/api/departments');
  }

  profile(): Observable<Profile> {
    return this.http.get<Profile>('/api/profile');
  }

  updateProfile(body: {
    jobTitle: string | null;
    phone: string | null;
    avatarColor: string;
  }): Observable<Profile> {
    return this.http.put<Profile>('/api/profile', body, this.own);
  }
}
