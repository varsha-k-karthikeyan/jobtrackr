import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  Application,
  APPLICATION_STATUS_LABELS,
  APPLICATION_STATUSES,
  ApplicationStatus,
} from '../../../core/models/application.model';
import { ApplicationStore } from '../../../core/state/application-store';
import { openApplicationDrawer } from '../../../shared/components/application-drawer/application-drawer';
import { ApplicationDialogs } from '../../../shared/components/application-dialogs';
import { StatsStrip } from '../../stats/stats-strip/stats-strip';
import { ApplicationCard } from '../application-card/application-card';

// The Kanban board: four connected CDK drop lists fed by ApplicationStore signals.
@Component({
  selector: 'app-board-page',
  imports: [
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    CdkScrollable,
    MatButtonModule,
    MatIconModule,
    ApplicationCard,
    StatsStrip,
  ],
  templateUrl: './board-page.html',
  styleUrl: './board-page.scss',
})
export class BoardPage implements OnInit {
  protected readonly store = inject(ApplicationStore);
  private readonly dialogs = inject(ApplicationDialogs);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly labels = APPLICATION_STATUS_LABELS;
  /** Three grey placeholder cards per column while the first load runs. */
  protected readonly skeletonCards = [1, 2, 3];
  /** Touch: hold briefly before a drag starts, so a normal swipe still scrolls the board sideways. */
  protected readonly dragStartDelay = { touch: 250, mouse: 0 };

  /** Releasing a drag can also fire a click on the card; that click must not open the drawer. */
  private lastDragEndedAt = 0;

  ngOnInit(): void {
    // Always refetch on entry: the board may have changed in another tab or on another device
    void this.store.loadBoard();
  }

  protected retry(): void {
    void this.store.loadBoard();
  }

  protected addApplication(status: ApplicationStatus = 'APPLIED'): void {
    this.dialogs.openCreate(status);
  }

  protected openDetails(card: Application): void {
    if (Date.now() - this.lastDragEndedAt > 300) {
      openApplicationDrawer(this.dialog, card.id);
    }
  }

  protected edit(card: Application): void {
    this.dialogs.openEdit(card);
  }

  protected delete(card: Application): void {
    void this.dialogs.confirmAndDelete(card);
  }

  protected dragEnded(): void {
    this.lastDragEndedAt = Date.now();
  }

  /**
   * CDK's moveItemInArray/transferArrayItem mutate arrays in place, which would bypass the store's
   * immutable signal state. The store's move() does the same splice immutably and calls the API.
   * `currentIndex` is the card's index in the target column after the drop: exactly the backend's
   * zero-based `position`, for same-column and cross-column moves alike.
   */
  protected async drop(event: CdkDragDrop<ApplicationStatus, ApplicationStatus, Application>): Promise<void> {
    const card = event.item.data;
    const result = await this.store.move(card.id, event.container.data, event.currentIndex);
    if (result === 'rolled-back') {
      this.snackBar.open(`Couldn't move ${card.company}. It's back where it was.`, 'Dismiss', { duration: 6000 });
    } else if (result === 'reloaded') {
      this.snackBar.open('The board changed elsewhere, so it was reloaded.', 'Dismiss', { duration: 6000 });
    }
  }
}
