import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Application } from '../../../core/models/application.model';
import { dateToIsoDate, followUpState } from '../../../shared/utils/dates';
import { formatSalary } from '../../../shared/utils/format';

// One Kanban card. Presentational: it renders an Application and reports user intent through outputs;
// the board page decides what open/edit/delete mean. Dragging is handled by the board's cdkDrag wrapper.
@Component({
  selector: 'app-application-card',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatChipsModule, MatIconModule, MatMenuModule],
  templateUrl: './application-card.html',
  styleUrl: './application-card.scss',
})
export class ApplicationCard {
  readonly application = input.required<Application>();

  readonly open = output<Application>();
  readonly edit = output<Application>();
  readonly delete = output<Application>();

  protected readonly salary = computed(() => formatSalary(this.application()));

  /** Null when there is no follow-up date. */
  protected readonly followUp = computed(() => {
    const date = this.application().followUpOn;
    return date ? { date, state: followUpState(date) } : null;
  });

  /** Today's or a future interview; null when none is scheduled or it is already in the past. */
  protected readonly interview = computed(() => {
    const at = this.application().interviewAt;
    if (!at) {
      return null;
    }
    const start = new Date(at);
    const state = followUpState(dateToIsoDate(start));
    return state === 'overdue' ? null : { start, isToday: state === 'today' };
  });
}
