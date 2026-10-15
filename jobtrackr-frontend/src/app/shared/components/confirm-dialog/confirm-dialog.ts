import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
}

// Generic yes/no dialog. Closes with true when confirmed, false or undefined otherwise.
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <!-- Cancel comes first, so it gets the initial focus: pressing Enter never deletes by accident -->
      <button mat-flat-button class="confirm-danger" [mat-dialog-close]="true">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '@angular/material' as mat;

    // Destructive action in the theme's error colour, through Material's override API
    .confirm-danger {
      @include mat.button-overrides((
        filled-container-color: var(--mat-sys-error),
        filled-label-text-color: var(--mat-sys-on-error),
      ));
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
