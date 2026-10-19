// Mirrors the backend records in jobtrackr-backend/.../notification/dto. Field names match the JSON exactly.
import { ApplicationStatus, IsoDate, IsoInstant } from './application.model';

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

/** InterviewNotification: one interview still ahead, in the bell menu. */
export interface InterviewNotification {
  applicationId: number;
  company: string;
  role: string;
  status: ApplicationStatus;
  /** Exact instant; shown in the browser's zone. */
  interviewAt: IsoInstant;
  /** Computed by the server in its configured zone: 0 = today, 1 = tomorrow. */
  daysUntil: number;
}

/**
 * GET /notifications. `due` (today or overdue, most overdue first) can be dismissed; `upcoming` is info only;
 * `interviews` are those still ahead, earliest first. The badge counts due follow-ups plus today's interviews.
 */
export interface Notifications {
  today: IsoDate;
  due: FollowUpNotification[];
  upcoming: FollowUpNotification[];
  interviews: InterviewNotification[];
}
