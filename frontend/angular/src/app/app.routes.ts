import { Routes } from '@angular/router';
import { AppShellComponent } from './layout';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
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
    ],
  },
];
