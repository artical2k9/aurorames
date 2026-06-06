import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { MultiSelectModule } from 'primeng/multiselect';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageService } from 'primeng/api';
import {
  LucidePencil,
  LucideUserPlus,
  LucideUserX,
} from '@lucide/angular';
import { BreadcrumbService } from '../../../../shared/ui';
import {
  IamApiService,
  UserResponse,
  RoleResponse,
  CreateUserRequest,
} from '../../services/iam-api.service';

interface RoleOption { label: string; value: string; }

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    TableModule, DialogModule, ButtonModule,
    MultiSelectModule, InputTextModule, TagModule,
    ToastModule, MessageModule, SkeletonModule,
    LucidePencil, LucideUserPlus, LucideUserX,
  ],
  providers: [MessageService],
  template: `
    <div class="um">
      <p-toast />

      <!-- ── Header ──────────────────────────────────────────── -->
      <div class="um__header">
        <div>
          <h2 class="um__title">User Management</h2>
          <p class="um__subtitle">
            Manage users and role assignments. All changes are applied to Keycloak immediately.
          </p>
        </div>
        <p-button label="Add User" size="small" severity="primary"
                  [disabled]="loading"
                  (onClick)="openCreate()">
          <svg lucideUserPlus [size]="14" [strokeWidth]="2" style="margin-right:0.375rem"></svg>
        </p-button>
      </div>

      <!-- ── Loading skeleton ─────────────────────────────────── -->
      @if (loading) {
        @for (_ of [1,2,3,4,5]; track $index) {
          <p-skeleton height="2.75rem" styleClass="mb-1" />
        }
      } @else {

        <!-- ── Users table ────────────────────────────────────── -->
        <p-table [value]="users" [paginator]="users.length > 25" [rows]="25"
                 styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Roles</th>
              <th>Status</th>
              <th style="width:6rem">Actions</th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-user>
            <tr>
              <td>{{ user.firstName }} {{ user.lastName }}</td>
              <td>{{ user.email }}</td>
              <td>
                @for (role of user.roles; track role) {
                  <p-tag [value]="role" severity="secondary" styleClass="um__role-tag" />
                }
                @if (!user.roles?.length) {
                  <span style="color:var(--p-text-muted-color);font-size:0.75rem">—</span>
                }
              </td>
              <td>
                <span class="um__status"
                      [class.um__status--active]="user.enabled"
                      [class.um__status--disabled]="!user.enabled">
                  {{ user.enabled ? 'Active' : 'Disabled' }}
                </span>
              </td>
              <td>
                <div class="um__actions">
                  <button class="um__action-btn" title="Edit roles" (click)="openEdit(user)">
                    <svg lucidePencil [size]="14" [strokeWidth]="2"></svg>
                  </button>
                  @if (user.enabled) {
                    @if (pendingDeactivateId === user.id) {
                      <span class="um__confirm">
                        Deactivate?
                        <button class="um__confirm-btn um__confirm-btn--yes"
                                (click)="confirmDeactivate(user.id)">Yes</button>
                        <button class="um__confirm-btn um__confirm-btn--no"
                                (click)="pendingDeactivateId = null">No</button>
                      </span>
                    } @else {
                      <button class="um__action-btn um__action-btn--danger"
                              title="Deactivate user"
                              (click)="pendingDeactivateId = user.id">
                        <svg lucideUserX [size]="14" [strokeWidth]="2"></svg>
                      </button>
                    }
                  }
                </div>
              </td>
            </tr>
          </ng-template>

          <ng-template pTemplate="empty">
            <tr>
              <td colspan="5" class="um__empty">No users found for this organisation.</td>
            </tr>
          </ng-template>
        </p-table>

      }

      <!-- ── Create / Edit dialog ──────────────────────────────── -->
      <p-dialog [(visible)]="dialogVisible"
                [header]="editMode ? 'Edit User Roles' : 'Add User'"
                [modal]="true"
                [closable]="!saving"
                [style]="{ width: '480px' }">

        <form [formGroup]="form" class="um__form">
          @if (!editMode) {
            <div class="um__field-row">
              <div class="um__field">
                <label for="firstName">First Name *</label>
                <input pInputText id="firstName" formControlName="firstName"
                       [class.ng-invalid]="isInvalid('firstName')" />
              </div>
              <div class="um__field">
                <label for="lastName">Last Name *</label>
                <input pInputText id="lastName" formControlName="lastName"
                       [class.ng-invalid]="isInvalid('lastName')" />
              </div>
            </div>
            <div class="um__field">
              <label for="email">Email *</label>
              <input pInputText id="email" formControlName="email" type="email"
                     [class.ng-invalid]="isInvalid('email')" />
            </div>
          } @else {
            <div class="um__readonly-header">
              <strong>{{ selectedUser?.firstName }} {{ selectedUser?.lastName }}</strong>
              <span class="um__readonly-email">{{ selectedUser?.email }}</span>
            </div>
          }

          <div class="um__field">
            <label for="roles">Roles</label>
            <p-multiSelect id="roles"
                           formControlName="roles"
                           [options]="roleOptions"
                           optionLabel="label"
                           optionValue="value"
                           placeholder="Select roles…"
                           [filter]="true"
                           styleClass="w-full" />
          </div>

          @if (serverError) {
            <p-message severity="error" [text]="serverError" />
          }
        </form>

        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    [disabled]="saving" (onClick)="closeDialog()" />
          <p-button [label]="editMode ? 'Save Roles' : 'Create User'" size="small"
                    [loading]="saving" (onClick)="save()" />
        </ng-template>
      </p-dialog>

    </div>
  `,
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit {
  private readonly iam            = inject(IamApiService);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly fb             = inject(FormBuilder);
  private readonly toast          = inject(MessageService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  users: UserResponse[]   = [];
  roleOptions: RoleOption[] = [];
  loading               = true;
  dialogVisible         = false;
  editMode              = false;
  saving                = false;
  serverError: string | null = null;
  selectedUser: UserResponse | null = null;
  pendingDeactivateId: string | null = null;

  form: FormGroup = this.fb.group({
    firstName: ['', Validators.required],
    lastName:  ['', Validators.required],
    email:     ['', [Validators.required, Validators.email]],
    roles:     [[] as string[]],
  });

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Settings' },
      { label: 'User Management' },
    ]);
    this.load();
  }

  private load(): void {
    this.loading = true;
    forkJoin({
      users: this.iam.listUsers(),
      roles: this.iam.listRoles(),
    }).subscribe({
      next: ({ users, roles }) => {
        this.users = users;
        this.roleOptions = roles.map((r: RoleResponse) => ({ label: r.name, value: r.name }));
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.toast.add({ severity: 'error', summary: 'Load failed', detail: 'Could not load users or roles.' });
        this.cdr.detectChanges();
      },
    });
  }

  openCreate(): void {
    this.selectedUser = null;
    this.editMode     = false;
    this.serverError  = null;
    this.form.reset({ roles: [] });
    this.form.get('firstName')?.setValidators(Validators.required);
    this.form.get('lastName')?.setValidators(Validators.required);
    this.form.get('email')?.setValidators([Validators.required, Validators.email]);
    ['firstName', 'lastName', 'email'].forEach(f => this.form.get(f)?.updateValueAndValidity());
    this.dialogVisible = true;
  }

  openEdit(user: UserResponse): void {
    this.selectedUser = user;
    this.editMode     = true;
    this.serverError  = null;
    this.form.reset({ roles: [...user.roles] });
    ['firstName', 'lastName', 'email'].forEach(f => {
      this.form.get(f)?.clearValidators();
      this.form.get(f)?.updateValueAndValidity();
    });
    this.dialogVisible = true;
  }

  closeDialog(): void {
    this.dialogVisible = false;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving      = true;
    this.serverError = null;

    if (this.editMode && this.selectedUser) {
      const roles = this.form.value.roles as string[];
      this.iam.updateRoles(this.selectedUser.id, roles).subscribe({
        next: updated => {
          this.users = this.users.map(u => u.id === updated.id ? updated : u);
          this.saving        = false;
          this.dialogVisible = false;
          this.toast.add({ severity: 'success', summary: 'Roles updated' });
          this.cdr.detectChanges();
        },
        error: (err: HttpErrorResponse) => {
          this.serverError = err.error?.message ?? 'Failed to update roles.';
          this.saving = false;
          this.cdr.detectChanges();
        },
      });
    } else {
      const req = this.form.value as CreateUserRequest;
      this.iam.createUser(req).subscribe({
        next: created => {
          this.users         = [...this.users, created];
          this.saving        = false;
          this.dialogVisible = false;
          this.toast.add({
            severity: 'success',
            summary:  'User created',
            detail:   `${created.firstName} ${created.lastName} has been added.`,
          });
          this.cdr.detectChanges();
        },
        error: (err: HttpErrorResponse) => {
          this.serverError = err.error?.message ?? 'Failed to create user.';
          this.saving = false;
          this.cdr.detectChanges();
        },
      });
    }
  }

  confirmDeactivate(userId: string): void {
    this.iam.deactivateUser(userId).subscribe({
      next: () => {
        this.users = this.users.map(u =>
          u.id === userId ? { ...u, enabled: false } : u
        );
        this.pendingDeactivateId = null;
        this.toast.add({ severity: 'warn', summary: 'User deactivated', detail: 'The user has been disabled in Keycloak.' });
        this.cdr.detectChanges();
      },
      error: () => {
        this.pendingDeactivateId = null;
        this.toast.add({ severity: 'error', summary: 'Deactivate failed', detail: 'Could not deactivate this user.' });
        this.cdr.detectChanges();
      },
    });
  }

  isInvalid(field: string): boolean {
    const c = this.form.get(field);
    return !!(c?.invalid && c.touched);
  }
}
