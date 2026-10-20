import { PercentPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { Stats } from '../../../core/models/stats.model';
import { ApplicationStore } from '../../../core/state/application-store';

// Summary tiles above the board. Derived from the store's board instead of calling GET /stats: the
// board is already loaded, so the numbers update instantly (and without a request) after every create,
// delete and drag. The formula matches the backend's StatsService exactly.
@Component({
  selector: 'app-stats-strip',
  imports: [PercentPipe],
  templateUrl: './stats-strip.html',
  styleUrl: './stats-strip.scss',
})
export class StatsStrip {
  private readonly store = inject(ApplicationStore);

  protected readonly stats = computed<Stats>(() => {
    const counts = this.store.counts();
    const total = this.store.total();
    const responded = counts.INTERVIEW + counts.OFFER + counts.REJECTED;
    return {
      total,
      applied: counts.APPLIED,
      interview: counts.INTERVIEW,
      offer: counts.OFFER,
      rejected: counts.REJECTED,
      // Any answer counts as a response, including a rejection; 0 when there is nothing yet
      responseRate: total === 0 ? 0 : responded / total,
    };
  });
}
