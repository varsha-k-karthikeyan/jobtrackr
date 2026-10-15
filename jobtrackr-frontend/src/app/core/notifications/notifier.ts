import { inject, Injectable, Injector } from '@angular/core';

/**
 * Shows a snackbar message, loading Angular Material's snackbar code on first use.
 * The HTTP interceptors run on every page and are part of the initial bundle; importing MatSnackBar
 * there directly would put the snackbar and its overlay machinery into every first page load, although
 * a message is only needed when something fails. Feature pages that are lazy-loaded anyway can keep
 * using MatSnackBar directly.
 */
@Injectable({ providedIn: 'root' })
export class Notifier {
  private readonly injector = inject(Injector);

  show(message: string, durationMs = 6000): void {
    import('@angular/material/snack-bar')
      .then(({ MatSnackBar }) => this.injector.get(MatSnackBar).open(message, 'Dismiss', { duration: durationMs }))
      // The import is asynchronous: if the app (or a test) was torn down meanwhile, the injector is
      // destroyed and there is nowhere left to show the message. Nothing to do in that case.
      .catch(() => undefined);
  }
}
