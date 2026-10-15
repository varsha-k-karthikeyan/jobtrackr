// Mirrors the backend records in jobtrackr-backend/.../notification/dto. Field names match the JSON exactly.
import { ApplicationStatus, IsoDate } from './application.model';

/** FollowUpNotification: one follow-up in the bell menu. */
export interface FollowUpNotification {
  applicationId: number;
  company: string;
  role: string;
  status: ApplicationStatus;
  followUpOn: IsoDate;
  /** Computed by the server in its configured zone: 0 = due today, -3 = three days overdue, 2 = in two days. */
  daysUntilDue: number;
}

/** GET /notifications. `due` (today or overdue, most overdue first) drives the badge; `upcoming` is info only. */
export interface Notifications {
  today: IsoDate;
  due: FollowUpNotification[];
  upcoming: FollowUpNotification[];
}
