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
import { CheckboxModule } from 'primeng/checkbox';
import { MessageService } from 'primeng/api';
import {
  LucideUserPen,
  LucideUserPlus,
  LucideUserX,
  LucideUserKey,
  LucideTriangleAlert,
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
    ToastModule, MessageModule, SkeletonModule, CheckboxModule,
    LucideUserPen, LucideUserPlus, LucideUserX,
    LucideUserKey, LucideTriangleAlert,
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
              <th style="width:8rem">Actions</th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-user>
            <tr>
              <td>
                {{ user.firstName }} {{ user.lastName }}
                @if (user.passwordChangeRequired) {
                  <svg lucideTriangleAlert [size]="13" [strokeWidth]="2"
                       class="um__pw-badge"
                       title="Password change required"></svg>
                }
              </td>
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
                  <button class="um__action-btn" title="Edit user" (click)="openEdit(user)">
                    <svg lucideUserPen [size]="14" [strokeWidth]="2"></svg>
                  </button>
                  <button class="um__action-btn" title="Reset password"
                          (click)="openEdit(user, true)">
                    <svg lucideUserKey [size]="14" [strokeWidth]="2"></svg>
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
                [header]="editMode ? 'Edit User' : 'Add User'"
                [modal]="true"
                [closable]="!saving"
                [style]="{ width: '520px' }"
                [contentStyle]="{ 'max-height': '70vh', 'overflow-y': 'auto' }">

        <form [formGroup]="form" class="um__form">

          <!-- ── Identity fields (create only) ──────────────────── -->
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
            <div class="um__field-row">
              <div class="um__field">
                <label for="employeeNumber">Employee Number *</label>
                <input pInputText id="employeeNumber" formControlName="employeeNumber"
                       [class.ng-invalid]="isInvalid('employeeNumber')" />
              </div>
              <div class="um__field">
                <label for="hireDate">Hire Date</label>
                <input pInputText id="hireDate" formControlName="hireDate" type="date" />
              </div>
            </div>
            <p class="um__hint">A linked employee record is created automatically.</p>
          } @else {
            <div class="um__readonly-header">
              <strong>{{ selectedUser?.firstName }} {{ selectedUser?.lastName }}</strong>
              <span class="um__readonly-email">{{ selectedUser?.email }}</span>
            </div>
          }

          <!-- ── Roles ───────────────────────────────────────────── -->
          <div class="um__field">
            <label for="roles">Roles</label>
            <p-multiSelect id="roles"
                           formControlName="roles"
                           [options]="roleOptions"
                           optionLabel="label"
                           optionValue="value"
                           placeholder="Select roles…"
                           [filter]="true"
                           appendTo="body"
                           styleClass="w-full" />
          </div>

          <!-- ── Set initial password (create only) ─────────────── -->
          @if (!editMode) {
            <div class="um__field">
              <label class="um__checkbox-label">
                <p-checkbox formControlName="setInitialPassword"
                            [binary]="true"
                            inputId="setInitialPassword" />
                <span>Set initial password</span>
              </label>
            </div>

            @if (form.value.setInitialPassword) {
              <div class="um__field">
                <label for="initialPassword">Initial Password *</label>
                <input pInputText id="initialPassword" type="password"
                       formControlName="initialPassword"
                       placeholder="Min 8 characters"
                       [class.ng-invalid]="isInvalid('initialPassword')" />
              </div>
              <div class="um__field">
                <label for="confirmPassword">Confirm Password *</label>
                <input pInputText id="confirmPassword" type="password"
                       formControlName="confirmPassword"
                       placeholder="Repeat password"
                       [class.ng-invalid]="isInvalid('confirmPassword') || passwordMismatch" />
                @if (passwordMismatch) {
                  <span class="um__field-error">Passwords do not match</span>
                }
              </div>
              <div class="um__field">
                <label class="um__checkbox-label">
                  <p-checkbox formControlName="temporaryPassword"
                              [binary]="true"
                              inputId="temporaryPassword" />
                  <span>Temporary — user must change on first login</span>
                </label>
              </div>
            }
          }

          <!-- ── Reset password section (edit only) ─────────────── -->
          @if (editMode) {
            <hr class="um__divider" />
            <p class="um__section-label">Reset Password</p>
            <div class="um__field">
              <label for="resetPassword">New Password</label>
              <input pInputText id="resetPassword" type="password"
                     formControlName="resetPassword"
                     placeholder="Leave blank to keep current password"
                     [class.ng-invalid]="isInvalid('resetPassword')" />
            </div>
            @if (form.value.resetPassword) {
              <div class="um__field">
                <label for="confirmResetPassword">Confirm New Password</label>
                <input pInputText id="confirmResetPassword" type="password"
                       formControlName="confirmResetPassword"
                       placeholder="Repeat new password"
                       [class.ng-invalid]="resetPasswordMismatch" />
                @if (resetPasswordMismatch) {
                  <span class="um__field-error">Passwords do not match</span>
                }
              </div>
              <div class="um__field">
                <label class="um__checkbox-label">
                  <p-checkbox formControlName="temporaryReset"
                              [binary]="true"
                              inputId="temporaryReset" />
                  <span>Temporary — user must change on first login</span>
                </label>
              </div>
            }
          }

          @if (serverError) {
            <p-message severity="error" [text]="serverError" />
          }
        </form>

        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    [disabled]="saving" (onClick)="closeDialog()" />
          <p-button [label]="editMode ? 'Save' : 'Create User'" size="small"
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
    firstName:          ['', Validators.required],
    lastName:           ['', Validators.required],
    email:              ['', [Validators.required, Validators.email]],
    employeeNumber:     ['', Validators.required],
    hireDate:           [''],
    roles:              [[] as string[]],
    // create-only
    setInitialPassword: [false],
    initialPassword:    [''],
    confirmPassword:    [''],
    temporaryPassword:  [true],
    // edit-only
    resetPassword:      [''],
    confirmResetPassword: [''],
    temporaryReset:     [true],
  });

  get passwordMismatch(): boolean {
    const v = this.form.value;
    return v.setInitialPassword && !!v.initialPassword &&
           v.initialPassword !== v.confirmPassword;
  }

  get resetPasswordMismatch(): boolean {
    const v = this.form.value;
    return !!v.resetPassword && v.resetPassword !== v.confirmResetPassword;
  }

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
        this.toast.add({ severity: 'error', summary: 'Load failed',
          detail: 'Could not load users or roles.' });
        this.cdr.detectChanges();
      },
    });
  }

  openCreate(): void {
    this.selectedUser = null;
    this.editMode     = false;
    this.serverError  = null;
    this.form.reset({
      roles: [], setInitialPassword: false,
      temporaryPassword: true, temporaryReset: true,
    });
    ['firstName', 'lastName', 'email', 'employeeNumber'].forEach(f => {
      this.form.get(f)?.setValidators(
        f === 'email' ? [Validators.required, Validators.email] : Validators.required);
      this.form.get(f)?.updateValueAndValidity();
    });
    this.dialogVisible = true;
  }

  openEdit(user: UserResponse, focusPassword = false): void {
    this.selectedUser = user;
    this.editMode     = true;
    this.serverError  = null;
    this.form.reset({
      roles: [...user.roles],
      resetPassword: '', confirmResetPassword: '',
      temporaryReset: true,
    });
    ['firstName', 'lastName', 'email', 'employeeNumber'].forEach(f => {
      this.form.get(f)?.clearValidators();
      this.form.get(f)?.updateValueAndValidity();
    });
    this.dialogVisible = true;
    if (focusPassword) {
      setTimeout(() => {
        const el = document.getElementById('resetPassword');
        el?.focus();
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 150);
    }
  }

  closeDialog(): void {
    this.dialogVisible = false;
  }

  save(): void {
    if (!this.canSave()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving      = true;
    this.serverError = null;

    if (this.editMode && this.selectedUser) {
      this.saveEdit();
    } else {
      this.saveCreate();
    }
  }

  private saveCreate(): void {
    const v = this.form.value;
    const req: CreateUserRequest = {
      email: v.email, firstName: v.firstName, lastName: v.lastName, roles: v.roles,
      employeeNumber: v.employeeNumber,
    };
    if (v.hireDate) {
      req.hireDate = v.hireDate;
    }
    if (v.setInitialPassword && v.initialPassword) {
      req.initialPassword  = v.initialPassword;
      req.temporaryPassword = v.temporaryPassword;
    }
    this.iam.createUser(req).subscribe({
      next: created => {
        this.users         = [...this.users, created];
        this.saving        = false;
        this.dialogVisible = false;
        this.toast.add({
          severity: 'success', summary: 'User created',
          detail: `${created.firstName} ${created.lastName} has been added.`,
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

  private saveEdit(): void {
    const v    = this.form.value;
    const user = this.selectedUser!;
    const roles = v.roles as string[];
    const newPassword: string = v.resetPassword ?? '';

    const roleUpdate$ = this.iam.updateRoles(user.id, roles);

    if (newPassword.trim()) {
      const pwUpdate$ = this.iam.setPassword(user.id, {
        newPassword: newPassword.trim(),
        temporary:   v.temporaryReset,
      });
      forkJoin({ roles: roleUpdate$, pw: pwUpdate$ }).subscribe({
        next: ({ roles: updated }) => {
          this.onEditSaved(updated as UserResponse);
        },
        error: (err: HttpErrorResponse) => {
          this.serverError = err.error?.message ?? 'Failed to save changes.';
          this.saving = false;
          this.cdr.detectChanges();
        },
      });
    } else {
      roleUpdate$.subscribe({
        next: updated => { this.onEditSaved(updated); },
        error: (err: HttpErrorResponse) => {
          this.serverError = err.error?.message ?? 'Failed to update roles.';
          this.saving = false;
          this.cdr.detectChanges();
        },
      });
    }
  }

  private onEditSaved(updated: UserResponse): void {
    this.users = this.users.map(u => u.id === updated.id ? updated : u);
    this.saving        = false;
    this.dialogVisible = false;
    this.toast.add({ severity: 'success', summary: 'User saved' });
    this.cdr.detectChanges();
  }

  confirmDeactivate(userId: string): void {
    this.iam.deactivateUser(userId).subscribe({
      next: () => {
        this.users = this.users.map(u =>
          u.id === userId ? { ...u, enabled: false } : u
        );
        this.pendingDeactivateId = null;
        this.toast.add({ severity: 'warn', summary: 'User deactivated',
          detail: 'The user has been disabled in Keycloak.' });
        this.cdr.detectChanges();
      },
      error: () => {
        this.pendingDeactivateId = null;
        this.toast.add({ severity: 'error', summary: 'Deactivate failed',
          detail: 'Could not deactivate this user.' });
        this.cdr.detectChanges();
      },
    });
  }

  isInvalid(field: string): boolean {
    const c = this.form.get(field);
    return !!(c?.invalid && c.touched);
  }

  private canSave(): boolean {
    if (this.form.invalid) return false;
    if (!this.editMode) {
      const v = this.form.value;
      if (v.setInitialPassword) {
        if (!v.initialPassword || v.initialPassword.length < 8) return false;
        if (v.initialPassword !== v.confirmPassword) return false;
      }
    } else {
      if (this.resetPasswordMismatch) return false;
      const rp = this.form.value.resetPassword ?? '';
      if (rp.trim() && rp.trim().length < 8) return false;
    }
    return true;
  }
}
