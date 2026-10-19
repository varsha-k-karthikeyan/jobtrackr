import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificationApiService } from '../api/notification-api-service';
import { AuthService } from '../auth/auth-service';
import { formatDate } from '@angular/common';
import { FollowUpNotification, InterviewNotification, Notifications } from '../models/notification.model';

// The bell in the header: follow-ups and upcoming interviews. The server computes the lists from the cards;
// this store only holds the latest copy and applies dismissals (follow-ups only: an interview leaves the list
// by itself once it starts).
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly api = inject(NotificationApiService);

  private readonly notificationsState = signal<Notifications | null>(null);
  // Bumped by reset(): a response that arrives after a logout belongs to the previous user and is dropped
  private session = 0;

  readonly due = computed<FollowUpNotification[]>(() => this.notificationsState()?.due ?? []);
  readonly upcoming = computed<FollowUpNotification[]>(() => this.notificationsState()?.upcoming ?? []);
  readonly interviews = computed<InterviewNotification[]>(() => this.notificationsState()?.interviews ?? []);
  /** The badge number: due and overdue follow-ups, plus interviews still ahead today. */
  readonly badgeCount = computed(
    () => this.due().length + this.interviews().filter((interview) => interview.daysUntil === 0).length,
  );

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

/** "Today · 7:45 PM", "Tomorrow · 10:00 AM", "Thu 22 Oct · 2:30 PM" (the time in the browser's zone). */
export function interviewLabel(interview: InterviewNotification): string {
  const time = formatDate(interview.interviewAt, 'h:mm a', 'en-US');
  if (interview.daysUntil === 0) {
    return `Today · ${time}`;
  }
  if (interview.daysUntil === 1) {
    return `Tomorrow · ${time}`;
  }
  return `${formatDate(interview.interviewAt, 'EEE d MMM', 'en-US')} · ${time}`;
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
