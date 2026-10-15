import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApplicationApiService, RequestOptions } from '../api/application-api-service';
import { problemMessage } from '../api/problem-message';
import { AuthService } from '../auth/auth-service';
import {
  Application,
  APPLICATION_STATUSES,
  ApplicationStatus,
  Board,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '../models/application.model';
import { ApplicationDetail, Note } from '../models/note.model';
import {
  appendCard,
  emptyBoard,
  findCard,
  moveCard,
  normalizeBoard,
  removeCard,
  replaceCard,
} from './board-state';

/** Outcome of a drag: the board page turns this into the right message. */
export type MoveResult = 'moved' | 'unchanged' | 'rolled-back' | 'reloaded';

// Single source of truth for the signed-in user's applications. Components read signals and call
// methods; only this class talks to ApplicationApiService and changes state, always immutably.
//
// Mutations return Promises (eager, and await-able from components) rather than cold Observables:
// forgetting to subscribe to a store mutation would silently do nothing.
@Injectable({ providedIn: 'root' })
export class ApplicationStore {
  private readonly api = inject(ApplicationApiService);

  private readonly boardState = signal<Board>(emptyBoard());
  private readonly loadingState = signal(false);
  private readonly loadedState = signal(false);
  private readonly errorState = signal<string | null>(null);
  private readonly detailState = signal<ApplicationDetail | null>(null);

  // Bumped by reset(). A response that arrives after a logout belongs to the previous user and is dropped.
  private session = 0;

  readonly board = this.boardState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  /** True once the board has been fetched at least once for this session. */
  readonly loaded = this.loadedState.asReadonly();
  /** Message of the last failed board load, for the page's error state. */
  readonly error = this.errorState.asReadonly();
  /** The card open in the details drawer, with its notes. */
  readonly detail = this.detailState.asReadonly();

  readonly counts = computed(() => {
    const board = this.boardState();
    return Object.fromEntries(APPLICATION_STATUSES.map((status) => [status, board[status].length])) as Record<
      ApplicationStatus,
      number
    >;
  });
  readonly total = computed(() => Object.values(this.counts()).reduce((sum, count) => sum + count, 0));

  constructor() {
    // Wipe everything on logout, so one user's data can never flash up for the next
    inject(AuthService).onLogout(() => this.reset());
  }

  async loadBoard(): Promise<void> {
    const session = this.session;
    this.loadingState.set(true);
    this.errorState.set(null);
    try {
      // The page shows its own error state with a retry button, so no snackbar for this call
      const board = await firstValueFrom(this.api.getBoard({ skipErrorToast: true }));
      if (session === this.session) {
        this.boardState.set(normalizeBoard(board));
        this.loadedState.set(true);
      }
    } catch (error: unknown) {
      if (session === this.session) {
        this.errorState.set(problemMessage(error));
      }
    } finally {
      if (session === this.session) {
        this.loadingState.set(false);
      }
    }
  }

  async create(request: CreateApplicationRequest, options?: RequestOptions): Promise<Application> {
    const session = this.session;
    const created = await firstValueFrom(this.api.create(request, options));
    if (session === this.session) {
      this.boardState.update((board) => appendCard(board, created));
    }
    return created;
  }

  /**
   * Saves the edit form. A changed status moves the card to the bottom of the new column.
   * Errors (including 409 for a stale version) are rethrown for the caller to handle.
   */
  async update(id: number, request: UpdateApplicationRequest, options?: RequestOptions): Promise<Application> {
    const session = this.session;
    const updated = await firstValueFrom(this.api.update(id, request, options));
    if (session === this.session) {
      this.boardState.update((board) => {
        const previous = findCard(board, id);
        // A status change moved the card to the bottom of its new column on the server; mirror that
        // (removeCard closes the gap in the old column, appendCard gives it the same last position)
        return previous && previous.status !== updated.status
          ? appendCard(removeCard(board, id), updated)
          : replaceCard(board, updated);
      });
      this.syncDetail(updated);
    }
    return updated;
  }

  async delete(id: number, options?: RequestOptions): Promise<void> {
    const session = this.session;
    await firstValueFrom(this.api.delete(id, options));
    if (session === this.session) {
      this.boardState.update((board) => removeCard(board, id));
      if (this.detailState()?.application.id === id) {
        this.detailState.set(null);
      }
    }
  }

  /**
   * Optimistic move: the board changes immediately, then the server is told. On failure the board goes
   * back to how it was; on 404/409 (the card changed or vanished elsewhere) it is reloaded instead.
   */
  async move(id: number, targetStatus: ApplicationStatus, targetIndex: number): Promise<MoveResult> {
    const session = this.session;
    const before = this.boardState();
    const after = moveCard(before, id, targetStatus, targetIndex);
    if (after === before) {
      return 'unchanged';
    }
    this.boardState.set(after);
    // The index after clamping is exactly the backend's `position` (zero-based, in the target column)
    const position = findCard(after, id)?.position ?? targetIndex;

    try {
      const saved = await firstValueFrom(
        this.api.move(id, { status: targetStatus, position }, { skipErrorToast: true }),
      );
      if (session === this.session) {
        // Take the server's copy: it carries the moved card's new version
        this.boardState.update((board) => replaceCard(board, saved));
        this.syncDetail(saved);
      }
      return 'moved';
    } catch (error: unknown) {
      if (session !== this.session) {
        return 'rolled-back';
      }
      const changedElsewhere =
        error instanceof HttpErrorResponse && (error.status === 404 || error.status === 409);
      // Restore the snapshot only if nothing else changed the board meanwhile; otherwise the snapshot
      // is stale (another move is in flight), so ask the server for the truth instead
      if (!changedElsewhere && this.boardState() === after) {
        this.boardState.set(before);
        return 'rolled-back';
      }
      await this.loadBoard();
      return 'reloaded';
    }
  }

  /**
   * After a 409 the local copy is stale: fetch the card's current version and resync the board
   * (it may also have moved). Resolves to null if the card was deleted elsewhere.
   */
  async reloadCard(id: number): Promise<Application | null> {
    try {
      const detail = await firstValueFrom(this.api.get(id, { skipErrorToast: true }));
      await this.loadBoard();
      this.syncDetail(detail.application);
      return detail.application;
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        await this.loadBoard();
        return null;
      }
      throw error;
    }
  }

  async loadDetail(id: number): Promise<ApplicationDetail> {
    const detail = await firstValueFrom(this.api.get(id));
    this.detailState.set(detail);
    return detail;
  }

  clearDetail(): void {
    this.detailState.set(null);
  }

  async addNote(applicationId: number, body: string): Promise<Note> {
    const note = await firstValueFrom(this.api.addNote(applicationId, { body }));
    this.detailState.update((detail) =>
      detail && detail.application.id === applicationId
        ? { ...detail, notes: [note, ...detail.notes] } // newest first, like the server
        : detail,
    );
    return note;
  }

  async deleteNote(noteId: number): Promise<void> {
    await firstValueFrom(this.api.deleteNote(noteId));
    this.detailState.update((detail) =>
      detail ? { ...detail, notes: detail.notes.filter((note) => note.id !== noteId) } : detail,
    );
  }

  /** Back to an empty, not-loaded store. Called on logout. */
  reset(): void {
    this.session++;
    this.boardState.set(emptyBoard());
    this.loadingState.set(false);
    this.loadedState.set(false);
    this.errorState.set(null);
    this.detailState.set(null);
  }

  /** Keeps the open details drawer in step when its card is edited or moved. */
  private syncDetail(card: Application): void {
    this.detailState.update((detail) =>
      detail && detail.application.id === card.id ? { ...detail, application: card } : detail,
    );
  }
}
