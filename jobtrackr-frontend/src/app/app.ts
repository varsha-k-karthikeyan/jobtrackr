import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// Root component: only a router outlet. The toolbar lives in the Shell layout route,
// so the login and register pages can render without it.
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: '<router-outlet />',
})
export class App {}
