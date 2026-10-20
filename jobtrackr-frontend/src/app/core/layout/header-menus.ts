import { Injectable, signal } from '@angular/core';

// Lets a page open a header menu that the shell owns (the board's "N follow-ups due" pill opens the bell).
// UI state only: a page asks, the shell reacts by opening its own menu trigger, so neither component needs a
// reference to the other and the notification data stays in NotificationStore.
@Injectable({ providedIn: 'root' })
export class HeaderMenus {
  /** Bumped on every request; the shell opens the bell when it changes (0 = never asked). */
  readonly bellRequests = signal(0);

  openBell(): void {
    this.bellRequests.update((count) => count + 1);
  }
}
