import { Application, IsoInstant } from './application.model';

/** NoteResponse */
export interface Note {
  id: number;
  body: string;
  createdAt: IsoInstant;
}

/** POST /applications/{id}/notes */
export interface NoteRequest {
  body: string;
}

/** GET /applications/{id}: the card nested under `application`, notes newest first. */
export interface ApplicationDetail {
  application: Application;
  notes: Note[];
}
