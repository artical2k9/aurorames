import { Routes } from '@angular/router';
import { AppShellComponent } from './layout';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'bom',
        loadComponent: () =>
          import('./features/bom/pages/bom-browser/bom-browser.component')
            .then(m => m.BomBrowserComponent),
      },
      {
        path: 'bom/:itemId',
        loadComponent: () =>
          import('./features/bom/pages/bom-list/bom-list.component')
            .then(m => m.BomListComponent),
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard),
      },
      {
        path: 'item-master',
        loadComponent: () =>
          import('./features/item-master/pages/item-master-list/item-master-list.component')
            .then(m => m.ItemMasterListComponent),
      },
      {
        path: 'item-master/new',
        loadComponent: () =>
          import('./features/item-master/pages/item-master-create/item-master-create.component')
            .then(m => m.ItemMasterCreateComponent),
      },
      {
        path: 'item-master/:id/edit',
        loadComponent: () =>
          import('./features/item-master/pages/item-master-edit/item-master-edit.component')
            .then(m => m.ItemMasterEditComponent),
      },
      {
        path: 'item-master/:id',
        loadComponent: () =>
          import('./features/item-master/pages/item-master-detail/item-master-detail.component')
            .then(m => m.ItemMasterDetailComponent),
      },
      {
        path: 'item-master/:itemId/boms',
        loadComponent: () =>
          import('./features/bom/pages/bom-list/bom-list.component')
            .then(m => m.BomListComponent),
      },
      {
        path: 'boms/:bomId/explosion',
        loadComponent: () =>
          import('./features/bom/pages/bom-explosion/bom-explosion.component')
            .then(m => m.BomExplosionComponent),
      },
      {
        path: 'boms/:bomId',
        loadComponent: () =>
          import('./features/bom/pages/bom-authoring/bom-authoring.component')
            .then(m => m.BomAuthoringComponent),
      },
      {
        path: 'ecos/:ecoId',
        loadComponent: () =>
          import('./features/eco/pages/eco-detail/eco-detail.component')
            .then(m => m.EcoDetailComponent),
      },
      {
        path: 'ecos',
        loadComponent: () =>
          import('./features/eco/pages/eco-list/eco-list.component')
            .then(m => m.EcoListComponent),
      },
      {
        path: 'master-data/udf',
        loadComponent: () =>
          import('./features/master-data/pages/udf-admin/udf-admin.component')
            .then(m => m.UdfAdminComponent),
      },
      {
        path: 'settings/users',
        loadComponent: () =>
          import('./features/settings/pages/user-management/user-management.component')
            .then(m => m.UserManagementComponent),
      },
      {
        path: 'settings/uom',
        loadComponent: () =>
          import('./features/settings/pages/uom-management/uom-management.component')
            .then(m => m.UomManagementComponent),
      },
      {
        path: 'labour/employees',
        loadComponent: () =>
          import('./features/labour/pages/employee-list/employee-list.component')
            .then(m => m.EmployeeListComponent),
      },
      {
        path: 'labour/employees/:id',
        loadComponent: () =>
          import('./features/labour/pages/employee-detail/employee-detail.component')
            .then(m => m.EmployeeDetailComponent),
      },
      {
        path: 'labour/skills',
        loadComponent: () =>
          import('./features/labour/pages/skill-list/skill-list.component')
            .then(m => m.SkillListComponent),
      },
      {
        path: 'labour/certifications',
        loadComponent: () =>
          import('./features/labour/pages/certification-list/certification-list.component')
            .then(m => m.CertificationListComponent),
      },
      {
        path: 'quality/inspection-plans',
        loadComponent: () =>
          import('./features/inspection-plans/pages/inspection-plan-list/inspection-plan-list.component')
            .then(m => m.InspectionPlanListComponent),
      },
      {
        path: 'quality/inspection-plans/:id',
        loadComponent: () =>
          import('./features/inspection-plans/pages/inspection-plan-detail/inspection-plan-detail.component')
            .then(m => m.InspectionPlanDetailComponent),
      },
      {
        path: 'engineering/work-instructions',
        loadComponent: () =>
          import('./features/work-instructions/pages/work-instruction-list/work-instruction-list.component')
            .then(m => m.WorkInstructionListComponent),
      },
      {
        path: 'engineering/work-instructions/:id',
        loadComponent: () =>
          import('./features/work-instructions/pages/work-instruction-detail/work-instruction-detail.component')
            .then(m => m.WorkInstructionDetailComponent),
      },
    ],
  },
];
