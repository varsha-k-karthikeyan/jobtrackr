import { Component, computed, effect, inject, untracked } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { openApplicationDrawer } from '../../../shared/components/application-drawer/application-drawer';
import { AuthService } from '../../auth/auth-service';
import { DEMO_CREDENTIALS } from '../../auth/demo-credentials';
import { Notifier } from '../../notifications/notifier';
import { followUpLabel, interviewLabel, NotificationStore } from '../../notifications/notification-store';
import { ApplicationStore } from '../../state/application-store';

// Layout for the signed-in part of the app: toolbar with the notification bell and the user menu, then the
// routed page.
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatTabsModule,
    MatToolbarModule,
    MatBadgeModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly applications = inject(ApplicationStore);
  private readonly notifications = inject(NotificationStore);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly user = this.auth.user;
  // Null until /me answers; the menu shows a neutral label meanwhile
  protected readonly displayName = computed(() => {
    const user = this.user();
    return user ? (user.fullName ?? user.email) : 'Signed in';
  });

  protected readonly due = this.notifications.due;
  protected readonly upcoming = this.notifications.upcoming;
  protected readonly interviews = this.notifications.interviews;
  protected readonly badgeCount = this.notifications.badgeCount;
  protected readonly notificationsLabel = computed(() => {
    const count = this.badgeCount();
    return count === 0 ? 'Notifications' : `Notifications, ${count} for today`;
  });
  protected readonly label = followUpLabel;
  protected readonly interviewLabel = interviewLabel;

  constructor() {
    // Runs once on sign-in, then after every card change (a new follow-up date, a card moved to Offer)
    effect(() => {
      this.applications.revision();
      untracked(() => void this.notifications.load());
    });
  }

  protected refreshNotifications(): void {
    void this.notifications.load();
  }

  protected openApplication(applicationId: number): void {
    openApplicationDrawer(this.dialog, applicationId);
  }

  protected dismiss(applicationId: number): void {
    // A failure is already shown by the error interceptor; the item simply stays in the list
    this.notifications.dismiss(applicationId).catch(() => undefined);
  }

  protected dismissAll(): void {
    this.notifications.dismissAll().catch(() => undefined);
  }

  protected readonly isDemoAccount = computed(() => this.user()?.email === DEMO_CREDENTIALS.email);

  /** Flips the reminder-email switch. Nothing is sent from the next minute on (the job runs every minute). */
  protected toggleEmailReminders(): void {
    const enabled = !this.user()?.emailRemindersEnabled;
    // A failure is already shown by the error interceptor, and the switch keeps its old state
    this.auth.setEmailReminders(enabled).subscribe({
      next: () =>
        this.notifier.show(
          enabled
            ? 'Reminder emails are on: the 8:00 AM digest and a reminder 30 minutes before each interview.'
            : 'Reminder emails are turned off',
        ),
      error: () => undefined,
    });
  }

  protected changePassword(): void {
    // Loaded on first use: most sessions never open this dialog
    void import('../../../shared/components/change-password-dialog/change-password-dialog').then(
      ({ openChangePasswordDialog }) =>
        openChangePasswordDialog(this.dialog)
          .afterClosed()
          .subscribe((changed) => {
            if (changed) {
              this.notifier.show('Password changed. Other devices have been signed out.');
            }
          }),
    );
  }

  protected logout(): void {
    this.auth.logout();
  }
}
