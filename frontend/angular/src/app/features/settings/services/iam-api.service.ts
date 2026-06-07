import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: string[];
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export interface RoleResponse {
  id: string;
  name: string;
  description: string;
  systemRole: boolean;
}

@Injectable({ providedIn: 'root' })
export class IamApiService {
  private readonly http = inject(HttpClient);

  listUsers(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>('/api/iam/users');
  }

  createUser(req: CreateUserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>('/api/iam/users', req);
  }

  updateRoles(userId: string, roles: string[]): Observable<UserResponse> {
    return this.http.put<UserResponse>(`/api/iam/users/${userId}/roles`, { roles });
  }

  deactivateUser(userId: string): Observable<void> {
    return this.http.post<void>(`/api/iam/users/${userId}/deactivate`, {});
  }

  listRoles(): Observable<RoleResponse[]> {
    return this.http.get<RoleResponse[]>('/api/iam/roles');
  }
}
