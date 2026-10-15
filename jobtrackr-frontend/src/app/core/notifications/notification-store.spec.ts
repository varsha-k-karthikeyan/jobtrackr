import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { FollowUpNotification, Notifications } from '../models/notification.model';
import { followUpLabel, NotificationStore } from './notification-store';

const URL = `${environment.apiBaseUrl}/notifications`;

function item(applicationId: number, daysUntilDue: number): FollowUpNotification {
  return {
    applicationId,
    company: `Company ${applicationId}`,
    role: 'Angular Developer',
    status: 'APPLIED',
    followUpOn: '2026-10-15',
    daysUntilDue,
  };
}

const notifications: Notifications = {
  today: '2026-10-15',
  due: [item(1, -3), item(2, 0)],
  upcoming: [item(3, 2)],
};

describe('NotificationStore', () => {
  let store: NotificationStore;
  let http: HttpTestingController;

  async function load(): Promise<void> {
    const loading = store.load();
    http.expectOne(URL).flush(notifications);
    await loading;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(NotificationStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads due and upcoming follow-ups; only due ones count for the badge', async () => {
    expect(store.dueCount()).toBe(0);
    await load();

    expect(store.due().map((notification) => notification.applicationId)).toEqual([1, 2]);
    expect(store.upcoming().map((notification) => notification.applicationId)).toEqual([3]);
    expect(store.dueCount()).toBe(2);
  });

  it('keeps the last list when a refresh fails, without throwing', async () => {
    await load();
    const loading = store.load();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    await loading;

    expect(store.dueCount()).toBe(2);
  });

  it('dismiss removes only that follow-up once the server agrees', async () => {
    await load();
    const dismissing = store.dismiss(1);
    const request = http.expectOne(`${URL}/1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await dismissing;

    expect(store.due().map((notification) => notification.applicationId)).toEqual([2]);
    expect(store.upcoming()).toHaveLength(1);
  });

  it('dismissAll clears the due list but keeps upcoming', async () => {
    await load();
    const dismissing = store.dismissAll();
    const request = http.expectOne(URL);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await dismissing;

    expect(store.dueCount()).toBe(0);
    expect(store.upcoming()).toHaveLength(1);
  });

  it('drops a response that arrives after a logout', async () => {
    const loading = store.load();
    store.reset();
    http.expectOne(URL).flush(notifications);
    await loading;

    expect(store.dueCount()).toBe(0);
  });
});

describe('followUpLabel', () => {
  it('describes due, overdue and upcoming days', () => {
    expect(followUpLabel(0)).toBe('Due today');
    expect(followUpLabel(1)).toBe('Due tomorrow');
    expect(followUpLabel(3)).toBe('Due in 3 days');
    expect(followUpLabel(-1)).toBe('1 day overdue');
    expect(followUpLabel(-4)).toBe('4 days overdue');
  });
});
