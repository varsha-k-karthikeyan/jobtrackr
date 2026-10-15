import { inject, Injectable } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { ApplicationStore } from '../../core/state/application-store';
import { ApplicationDialog, ApplicationDialogData } from './application-dialog/application-dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog/confirm-dialog';

// One place that opens the create/edit and delete-confirmation dialogs with consistent settings,
// shared by the board, the list and the details drawer.
@Injectable({ providedIn: 'root' })
export class ApplicationDialogs {
  private readonly dialog = inject(MatDialog);
  private readonly store = inject(ApplicationStore);
  private readonly snackBar = inject(MatSnackBar);

  openCreate(status: ApplicationStatus = 'APPLIED'): MatDialogRef<ApplicationDialog, Application> {
    return this.openForm({ mode: 'create', status });
  }

  openEdit(application: Application): MatDialogRef<ApplicationDialog, Application> {
    return this.openForm({ mode: 'edit', application });
  }

  /** Asks first; resolves to true when the card was deleted. */
  async confirmAndDelete(application: Application): Promise<boolean> {
    const confirmed = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
          data: {
            title: 'Delete application?',
            message: `${application.company} (${application.role}) and all its notes will be deleted. This can't be undone.`,
            confirmLabel: 'Delete',
          },
          width: 'min(28rem, calc(100vw - 2rem))',
        })
        .afterClosed(),
    );
    if (!confirmed) {
      return false;
    }
    await this.store.delete(application.id); // a failure is reported by the error interceptor
    this.snackBar.open(`Deleted ${application.company}`, 'Dismiss', { duration: 4000 });
    return true;
  }

  private openForm(data: ApplicationDialogData): MatDialogRef<ApplicationDialog, Application> {
    return this.dialog.open<ApplicationDialog, ApplicationDialogData, Application>(ApplicationDialog, {
      data,
      width: 'min(44rem, calc(100vw - 2rem))',
      maxWidth: '100vw',
      maxHeight: 'calc(100dvh - 2rem)',
      // A half-filled form is easy to lose with one stray click outside, so only Cancel/Esc close it
      disableClose: true,
      autoFocus: 'first-tabbable',
    });
  }
}
