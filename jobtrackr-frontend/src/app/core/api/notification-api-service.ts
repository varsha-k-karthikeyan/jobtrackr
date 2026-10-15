import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/error-interceptor';
import { Notifications } from '../models/notification.model';

// Thin, stateless HTTP layer for /notifications. State lives in NotificationStore.
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly notificationsUrl = `${environment.apiBaseUrl}/notifications`;

  /** Background refresh: a failure must not pop a snackbar every time the board changes. */
  get(): Observable<Notifications> {
    return this.http.get<Notifications>(this.notificationsUrl, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true),
    });
  }

  /** Hides one due follow-up until its date changes. Not a delete of the application. */
  dismiss(applicationId: number): Observable<void> {
    return this.http.delete<void>(`${this.notificationsUrl}/${applicationId}`);
  }

  dismissAll(): Observable<void> {
    return this.http.delete<void>(this.notificationsUrl);
  }
}
