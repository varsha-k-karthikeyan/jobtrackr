import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatSortModule, Sort, SortDirection } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ApplicationApiService, ApplicationListQuery } from '../../../core/api/application-api-service';
import { problemMessage } from '../../../core/api/problem-message';
import {
  Application,
  APPLICATION_STATUS_LABELS,
  APPLICATION_STATUSES,
  ApplicationStatus,
} from '../../../core/models/application.model';
import { Page } from '../../../core/models/page.model';
import { openApplicationDrawer } from '../../../shared/components/application-drawer/application-drawer';
import { ApplicationDialogs } from '../../../shared/components/application-dialogs';
import { formatLpaAmount } from '../../../shared/utils/format';

/** Columns the backend allows in ?sort= (ApplicationService.SORTABLE_FIELDS) that this table offers. */
const SORTABLE_COLUMNS = ['company', 'role', 'status', 'location', 'salaryMinLpa', 'salaryMaxLpa', 'appliedOn'] as const;
type SortableColumn = (typeof SORTABLE_COLUMNS)[number];
const DEFAULT_SORT = { active: 'appliedOn' as SortableColumn, direction: 'desc' as const };
const PAGE_SIZES = [10, 25, 50];
/** Salary slider scale in LPA. The top of the scale means "this much or more" (no upper limit). */
const SALARY_SLIDER_MAX = 100;

type ListState =
  | { kind: 'loading' }
  | { kind: 'loaded'; page: Page<Application> }
  | { kind: 'error'; message: string };

// Server-side paginated table. The URL query string is the single source of truth for search, filter,
// sort and page: every control writes to the URL, and the data is (re)fetched from the URL. A refresh,
// the back button or a shared link therefore restores exactly the same view.
@Component({
  selector: 'app-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSliderModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './list-page.html',
  styleUrl: './list-page.scss',
})
export class ListPage {
  private readonly api = inject(ApplicationApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(ApplicationDialogs);

  // Query params, bound by withComponentInputBinding(). Always strings (or undefined) from the URL.
  readonly searchText = input<string>();
  readonly status = input<string>();
  readonly page = input<string>();
  readonly size = input<string>();
  readonly sort = input<string>();
  readonly salaryFrom = input<string>();
  readonly salaryTo = input<string>();

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly statusLabels = APPLICATION_STATUS_LABELS;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly formatLpa = formatLpaAmount;
  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly salarySliderMax = SALARY_SLIDER_MAX;
  /** Thumb positions while dragging (the URL only changes when a thumb is released). */
  protected readonly sliderFrom = signal(0);
  protected readonly sliderTo = signal(SALARY_SLIDER_MAX);

  /** URL -> validated query. Garbage in the URL falls back to defaults instead of erroring. */
  protected readonly query = computed(() => {
    const status = this.status();
    const [sortField, sortDirection] = (this.sort() ?? '').split(',');
    const sort = isSortable(sortField)
      ? { active: sortField, direction: sortDirection === 'asc' ? ('asc' as const) : ('desc' as const) }
      : DEFAULT_SORT;
    const size = Number(this.size());
    return {
      searchText: (this.searchText() ?? '').trim(),
      status: isStatus(status) ? status : null,
      page: Math.max(0, Math.floor(Number(this.page())) || 0),
      size: PAGE_SIZES.includes(size) ? size : PAGE_SIZES[0],
      sort,
      ...salaryRange(this.salaryFrom(), this.salaryTo()),
    };
  });

  /** Bumped to refetch the same query (e.g. after the details drawer closes). */
  private readonly refresh = signal(0);

  private readonly state = toSignal(
    combineLatest([toObservable(this.query), toObservable(this.refresh)]).pipe(
      // switchMap cancels a still-running request when the query changes: no out-of-order results
      switchMap(([query]) => {
        const request: ApplicationListQuery = {
          searchText: query.searchText,
          status: query.status,
          page: query.page,
          size: query.size,
          sort: `${query.sort.active},${query.sort.direction}`,
          salaryFrom: query.salaryFrom,
          salaryTo: query.salaryTo,
        };
        return this.api.list(request).pipe(
          map((page): ListState => ({ kind: 'loaded', page })),
          catchError((error: unknown) => of<ListState>({ kind: 'error', message: problemMessage(error) })),
          startWith<ListState>({ kind: 'loading' }),
        );
      }),
    ),
    { initialValue: { kind: 'loading' } as ListState },
  );

  /** The last successful page stays on screen while the next one loads (no flicker). */
  private readonly lastPage = signal<Page<Application> | null>(null);
  protected readonly loading = computed(() => this.state().kind === 'loading');
  protected readonly error = computed(() => {
    const state = this.state();
    return state.kind === 'error' ? state.message : null;
  });
  protected readonly rows = computed(() => this.lastPage()?.content ?? []);
  protected readonly totalElements = computed(() => this.lastPage()?.totalElements ?? 0);

  protected readonly salaryFilterActive = computed(() => this.query().salaryFrom !== null || this.query().salaryTo !== null);
  /** Live summary next to the slider: follows the thumbs while dragging. */
  protected readonly salarySummary = computed(() => {
    const from = this.sliderFrom();
    const to = this.sliderTo();
    const openEnded = to >= SALARY_SLIDER_MAX;
    if (from <= 0 && openEnded) {
      return 'Any salary';
    }
    if (openEnded) {
      return `₹${from}+ LPA`;
    }
    return from <= 0 ? `Up to ₹${to} LPA` : `₹${from} – ₹${to} LPA`;
  });

  // Fewer columns on phones; the details drawer shows the rest
  protected readonly isWide = toSignal(
    inject(BreakpointObserver).observe('(min-width: 768px)').pipe(map((result) => result.matches)),
    { initialValue: true },
  );
  protected readonly displayedColumns = computed(() =>
    this.isWide()
      ? ['company', 'role', 'status', 'location', 'salaryMinLpa', 'salaryMaxLpa', 'appliedOn']
      : ['company', 'status', 'appliedOn'],
  );

  constructor() {
    effect(() => {
      const state = this.state();
      if (state.kind === 'loaded') {
        this.lastPage.set(state.page);
      }
    });

    // Keep the slider in step with the URL (first load, back/forward navigation, Clear)
    effect(() => {
      const { salaryFrom, salaryTo } = this.query();
      this.sliderFrom.set(salaryFrom ?? 0);
      this.sliderTo.set(salaryTo ?? SALARY_SLIDER_MAX);
    });

    // Keep the search box in step with the URL (first load, back/forward navigation)
    effect(() => {
      const text = this.query().searchText;
      if (this.searchControl.value.trim() !== text) {
        this.searchControl.setValue(text, { emitEvent: false });
      }
    });

    // Typing: wait for a 300ms pause, then put the text in the URL (which triggers the fetch)
    this.searchControl.valueChanges
      .pipe(
        map((text) => text.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((text) => this.updateUrl({ searchText: text || null, page: null }, true));
  }

  protected onStatusChange(status: ApplicationStatus | null): void {
    this.updateUrl({ status, page: null });
  }

  /** A thumb was released: write the range to the URL (defaults are left out to keep it clean). */
  protected onSalaryChange(): void {
    const from = this.sliderFrom();
    const to = this.sliderTo();
    this.updateUrl({
      salaryFrom: from > 0 ? from : null,
      salaryTo: to < SALARY_SLIDER_MAX ? to : null,
      page: null,
    });
  }

  protected clearSalary(): void {
    this.updateUrl({ salaryFrom: null, salaryTo: null, page: null });
  }

  /** Label on a thumb while it is dragged. */
  protected formatSliderValue(value: number): string {
    return value >= SALARY_SLIDER_MAX ? `${SALARY_SLIDER_MAX}+` : String(value);
  }

  /** Native input event of a thumb while dragging: update the live summary. */
  protected onThumbInput(thumb: 'from' | 'to', event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    (thumb === 'from' ? this.sliderFrom : this.sliderTo).set(value);
  }

  protected onSortChange(sort: Sort): void {
    // Clearing the sort (third click) returns to the default instead of an unsorted list
    const value = sort.direction && isSortable(sort.active) ? `${sort.active},${sort.direction}` : null;
    this.updateUrl({ sort: value, page: null });
  }

  protected onPage(event: PageEvent): void {
    this.updateUrl({ page: event.pageIndex || null, size: event.pageSize === PAGE_SIZES[0] ? null : event.pageSize });
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.updateUrl({ searchText: null, status: null, salaryFrom: null, salaryTo: null, page: null });
  }

  protected openDetails(row: Application): void {
    openApplicationDrawer(this.dialog, row.id)
      .afterClosed()
      .subscribe(() => this.refresh.update((value) => value + 1));
  }

  protected addApplication(): void {
    this.dialogs
      .openCreate()
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.refresh.update((value) => value + 1);
        }
      });
  }

  /** MatTable's `let row` is untyped; this typed accessor keeps the template strict-mode safe. */
  protected statusLabel(status: ApplicationStatus): string {
    return APPLICATION_STATUS_LABELS[status];
  }

  protected sortDirection(): SortDirection {
    return this.query().sort.direction;
  }

  /** Merges into the current query string. null removes a parameter, so defaults keep the URL clean. */
  private updateUrl(params: Params, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
      // Typing a search should not add one history entry per pause
      replaceUrl,
    });
  }
}

function isStatus(value: string | undefined): value is ApplicationStatus {
  return (APPLICATION_STATUSES as readonly string[]).includes(value ?? '');
}

/**
 * Salary bounds from the URL. null = no bound: 0 as the lower bound and the top of the scale as the upper
 * bound mean "no limit". A hand-edited URL with the bounds the wrong way round is swapped, not rejected.
 */
function salaryRange(rawFrom: string | undefined, rawTo: string | undefined): { salaryFrom: number | null; salaryTo: number | null } {
  const from = salaryBound(rawFrom, (value) => value > 0);
  const to = salaryBound(rawTo, (value) => value < SALARY_SLIDER_MAX);
  return from !== null && to !== null && from > to ? { salaryFrom: to, salaryTo: from } : { salaryFrom: from, salaryTo: to };
}

/** Parses one salary bound from the URL; out-of-range or "no limit" values become null. */
function salaryBound(raw: string | undefined, isLimit: (value: number) => boolean): number | null {
  const value = Number(raw);
  return raw !== undefined && Number.isFinite(value) && value >= 0 && value <= SALARY_SLIDER_MAX && isLimit(value)
    ? Math.round(value)
    : null;
}

function isSortable(value: string | undefined): value is SortableColumn {
  return (SORTABLE_COLUMNS as readonly string[]).includes(value ?? '');
}
