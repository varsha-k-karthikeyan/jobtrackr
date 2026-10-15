import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificationApiService } from '../api/notification-api-service';
import { AuthService } from '../auth/auth-service';
import { FollowUpNotification, Notifications } from '../models/notification.model';

// Follow-up reminders for the bell in the header. The server computes the list from the follow-up dates;
// this store only holds the latest copy and applies dismissals.
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly api = inject(NotificationApiService);

  private readonly notificationsState = signal<Notifications | null>(null);
  // Bumped by reset(): a response that arrives after a logout belongs to the previous user and is dropped
  private session = 0;

  readonly due = computed<FollowUpNotification[]>(() => this.notificationsState()?.due ?? []);
  readonly upcoming = computed<FollowUpNotification[]>(() => this.notificationsState()?.upcoming ?? []);
  /** The badge number: only due and overdue follow-ups count. */
  readonly dueCount = computed(() => this.due().length);

  constructor() {
    inject(AuthService).onLogout(() => this.reset());
  }

  /** Never throws: the bell is secondary, so a failed refresh keeps the last list instead of interrupting. */
  async load(): Promise<void> {
    const session = this.session;
    try {
      const notifications = await firstValueFrom(this.api.get());
      if (session === this.session) {
        this.notificationsState.set(notifications);
      }
    } catch {
      // Keep what is shown; the next board change or menu open tries again
    }
  }

  async dismiss(applicationId: number): Promise<void> {
    await firstValueFrom(this.api.dismiss(applicationId));
    this.notificationsState.update((notifications) =>
      notifications
        ? { ...notifications, due: notifications.due.filter((item) => item.applicationId !== applicationId) }
        : notifications,
    );
  }

  async dismissAll(): Promise<void> {
    await firstValueFrom(this.api.dismissAll());
    this.notificationsState.update((notifications) => (notifications ? { ...notifications, due: [] } : notifications));
  }

  reset(): void {
    this.session++;
    this.notificationsState.set(null);
  }
}

/** "Due today", "Due tomorrow", "Due in 3 days", "1 day overdue", "4 days overdue". */
export function followUpLabel(daysUntilDue: number): string {
  if (daysUntilDue === 0) {
    return 'Due today';
  }
  if (daysUntilDue === 1) {
    return 'Due tomorrow';
  }
  if (daysUntilDue > 1) {
    return `Due in ${daysUntilDue} days`;
  }
  const overdue = -daysUntilDue;
  return `${overdue} ${overdue === 1 ? 'day' : 'days'} overdue`;
}
