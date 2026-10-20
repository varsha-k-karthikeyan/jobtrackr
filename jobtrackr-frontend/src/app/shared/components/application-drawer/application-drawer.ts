import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/api/problem-message';
import { APPLICATION_STATUS_LABELS } from '../../../core/models/application.model';
import { ApplicationStore } from '../../../core/state/application-store';
import { companyInitials, companyTilePalette } from '../../utils/company-tile';
import { followUpState } from '../../utils/dates';
import { formatSalary } from '../../utils/format';
import { ApplicationDialogs } from '../application-dialogs';

export interface ApplicationDrawerData {
  applicationId: number;
}

/** Opens the details drawer: a MatDialog pinned to the right edge, full height. */
export function openApplicationDrawer(
  dialog: MatDialog,
  applicationId: number,
): MatDialogRef<ApplicationDrawer, void> {
  return dialog.open<ApplicationDrawer, ApplicationDrawerData, void>(ApplicationDrawer, {
    data: { applicationId },
    position: { top: '0', right: '0' },
    height: '100dvh',
    // 640px on desktop, the full width on phones
    width: 'min(40rem, 100vw)',
    maxWidth: '100vw',
    maxHeight: '100dvh',
    panelClass: 'app-drawer-panel',
    autoFocus: 'dialog',
  });
}

// Side panel with every field of one application and its notes timeline. It renders store.detail(),
// so edits, moves and notes made anywhere show up here immediately.
@Component({
  selector: 'app-application-drawer',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './application-drawer.html',
  styleUrl: './application-drawer.scss',
})
export class ApplicationDrawer implements OnInit, OnDestroy {
  private readonly store = inject(ApplicationStore);
  private readonly dialogs = inject(ApplicationDialogs);
  private readonly dialogRef = inject<MatDialogRef<ApplicationDrawer, void>>(MatDialogRef);
  private readonly data = inject<ApplicationDrawerData>(MAT_DIALOG_DATA);

  protected readonly statusLabels = APPLICATION_STATUS_LABELS;
  protected readonly tileInitials = companyInitials;
  protected readonly tilePalette = companyTilePalette;
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly savingNote = signal(false);
  protected readonly noteControl = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  /** Only show the store's detail when it belongs to this drawer's card. */
  protected readonly detail = computed(() => {
    const detail = this.store.detail();
    return detail?.application.id === this.data.applicationId ? detail : null;
  });
  protected readonly salary = computed(() => {
    const detail = this.detail();
    return detail ? formatSalary(detail.application) : null;
  });
  protected readonly followUp = computed(() => {
    const date = this.detail()?.application.followUpOn;
    return date ? followUpState(date) : null;
  });

  ngOnInit(): void {
    void this.load();
  }

  ngOnDestroy(): void {
    this.store.clearDetail();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    try {
      await this.store.loadDetail(this.data.applicationId);
    } catch (error: unknown) {
      this.loadError.set(problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected edit(): void {
    const detail = this.detail();
    if (detail) {
      this.dialogs.openEdit(detail.application);
    }
  }

  protected async delete(): Promise<void> {
    const detail = this.detail();
    if (detail && (await this.dialogs.confirmAndDelete(detail.application))) {
      this.dialogRef.close();
    }
  }

  protected async addNote(): Promise<void> {
    const body = this.noteControl.value.trim();
    if (!body) {
      this.noteControl.markAsTouched();
      return;
    }
    this.savingNote.set(true);
    try {
      await this.store.addNote(this.data.applicationId, body);
      this.noteControl.reset();
    } catch {
      // Reported by the error interceptor; the typed text stays so nothing is lost
    } finally {
      this.savingNote.set(false);
    }
  }

  protected async deleteNote(noteId: number): Promise<void> {
    try {
      await this.store.deleteNote(noteId);
    } catch {
      // Reported by the error interceptor
    }
  }

  protected close(): void {
    this.dialogRef.close();
  }
}
