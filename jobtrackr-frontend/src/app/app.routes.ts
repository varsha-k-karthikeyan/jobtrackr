import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth-guards';

// Every page is lazy-loaded. /login and /register render without the shell (no toolbar) and only for
// signed-out users; /board and /list render inside the shell and only for signed-in users.
export const routes: Routes = [
  {
    path: 'login',
    title: 'Log in · JobTrackr',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Create account · JobTrackr',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register-page/register-page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    // On the parent, so the guard runs before the shell's chunk is even downloaded
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'board',
        title: 'Board · JobTrackr',
        loadComponent: () =>
          import('./features/board/board-page/board-page').then((m) => m.BoardPage),
      },
      {
        path: 'list',
        title: 'List · JobTrackr',
        loadComponent: () => import('./features/list/list-page/list-page').then((m) => m.ListPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'board' },
    ],
  },
  { path: '**', redirectTo: 'board' },
];
