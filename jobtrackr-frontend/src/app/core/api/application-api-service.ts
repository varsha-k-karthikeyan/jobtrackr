import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/error-interceptor';
import {
  Application,
  ApplicationStatus,
  Board,
  CreateApplicationRequest,
  MoveApplicationRequest,
  UpdateApplicationRequest,
} from '../models/application.model';
import { ApplicationDetail, Note, NoteRequest } from '../models/note.model';
import { Page } from '../models/page.model';
import { Stats } from '../models/stats.model';

/** Query for GET /applications. Empty values are left out of the URL. */
export interface ApplicationListQuery {
  status?: ApplicationStatus | null;
  searchText?: string | null;
  /** Salary range in LPA: keeps jobs whose own range overlaps [salaryFrom, salaryTo]. Either may be omitted. */
  salaryFrom?: number | null;
  salaryTo?: number | null;
  /** Zero-based page index. */
  page?: number;
  size?: number;
  /** Spring's format: "property,direction", e.g. "appliedOn,desc". */
  sort?: string | null;
}

/** Per-call options. */
export interface RequestOptions {
  /** The caller shows the error itself (e.g. a dialog handling 409), so no global snackbar. */
  skipErrorToast?: boolean;
}

// Thin, stateless HTTP layer: one typed method per backend endpoint, nothing else.
// Board state and its rules live in ApplicationStore. The only direct caller outside the store is the
// list page's list(): a page of search results is a view of the server, not shared state.
@Injectable({ providedIn: 'root' })
export class ApplicationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;
  private readonly applicationsUrl = `${environment.apiBaseUrl}/applications`;

  getBoard(options?: RequestOptions): Observable<Board> {
    return this.http.get<Board>(`${this.applicationsUrl}/board`, { context: toContext(options) });
  }

  list(query: ApplicationListQuery): Observable<Page<Application>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Application>>(this.applicationsUrl, { params });
  }

  get(id: number, options?: RequestOptions): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`${this.applicationsUrl}/${id}`, { context: toContext(options) });
  }

  create(request: CreateApplicationRequest, options?: RequestOptions): Observable<Application> {
    return this.http.post<Application>(this.applicationsUrl, request, { context: toContext(options) });
  }

  update(id: number, request: UpdateApplicationRequest, options?: RequestOptions): Observable<Application> {
    return this.http.put<Application>(`${this.applicationsUrl}/${id}`, request, { context: toContext(options) });
  }

  delete(id: number, options?: RequestOptions): Observable<void> {
    return this.http.delete<void>(`${this.applicationsUrl}/${id}`, { context: toContext(options) });
  }

  move(id: number, request: MoveApplicationRequest, options?: RequestOptions): Observable<Application> {
    return this.http.patch<Application>(`${this.applicationsUrl}/${id}/move`, request, { context: toContext(options) });
  }

  addNote(applicationId: number, request: NoteRequest, options?: RequestOptions): Observable<Note> {
    return this.http.post<Note>(`${this.applicationsUrl}/${applicationId}/notes`, request, { context: toContext(options) });
  }

  deleteNote(noteId: number, options?: RequestOptions): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/notes/${noteId}`, { context: toContext(options) });
  }

  getStats(): Observable<Stats> {
    return this.http.get<Stats>(`${this.baseUrl}/stats`);
  }
}

function toContext(options?: RequestOptions): HttpContext {
  return new HttpContext().set(SKIP_ERROR_TOAST, options?.skipErrorToast ?? false);
}
