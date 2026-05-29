import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [authGuard],
  },
  {
    path: 'item-master',
    loadComponent: () =>
      import('./features/item-master/pages/item-master-list/item-master-list.component')
        .then(m => m.ItemMasterListComponent),
    canActivate: [authGuard],
  },
];
