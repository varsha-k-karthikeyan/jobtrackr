import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth-service';
import { flushMicrotasks, makeBoard, makeCard } from '../testing/test-data';
import { ApplicationStore } from './application-store';

const API = environment.apiBaseUrl;
const BOARD_URL = `${API}/applications/board`;

describe('ApplicationStore', () => {
  let store: ApplicationStore;
  let http: HttpTestingController;

  const razorpay = makeCard(1, 'APPLIED', 0, { company: 'Razorpay', version: 3 });
  const postman = makeCard(2, 'APPLIED', 1, { company: 'Postman' });
  const zoho = makeCard(3, 'INTERVIEW', 0, { company: 'Zoho' });
  const columns = () => ({
    APPLIED: store.board().APPLIED.map((card) => `${card.company}:${card.position}`),
    INTERVIEW: store.board().INTERVIEW.map((card) => `${card.company}:${card.position}`),
  });

  async function loadBoard(): Promise<void> {
    const loading = store.loadBoard();
    http.expectOne(BOARD_URL).flush(makeBoard(razorpay, postman, zoho));
    await loading;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ApplicationStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('bumps revision after a successful change, but not after a plain load', async () => {
    await loadBoard();
    expect(store.revision()).toBe(0);

    const deleting = store.delete(razorpay.id);
    http.expectOne(`${API}/applications/${razorpay.id}`).flush(null, { status: 204, statusText: 'No Content' });
    await deleting;

    expect(store.revision()).toBe(1);
  });

  it('loads the board and derives counts and total', async () => {
    expect(store.loaded()).toBe(false);
    await loadBoard();

    expect(store.loaded()).toBe(true);
    expect(store.loading()).toBe(false);
    expect(store.counts()).toEqual({ APPLIED: 2, INTERVIEW: 1, OFFER: 0, REJECTED: 0 });
    expect(store.total()).toBe(3);
  });

  it('records an error when the board fails to load', async () => {
    const loading = store.loadBoard();
    http.expectOne(BOARD_URL).flush({ status: 500, detail: 'Database down' }, { status: 500, statusText: 'Server Error' });
    await loading;

    expect(store.loaded()).toBe(false);
    expect(store.error()).toBe('Database down');
  });

  it('moves a card optimistically, then takes the server copy', async () => {
    await loadBoard();

    const moving = store.move(1, 'INTERVIEW', 1);
    // Before the server answers, the board already shows the move
    expect(columns()).toEqual({ APPLIED: ['Postman:0'], INTERVIEW: ['Zoho:0', 'Razorpay:1'] });

    const request = http.expectOne(`${API}/applications/1/move`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INTERVIEW', position: 1 });
    request.flush({ ...razorpay, status: 'INTERVIEW', position: 1, version: 4 });

    expect(await moving).toBe('moved');
    expect(store.board().INTERVIEW[1].version).toBe(4);
  });

  it('rolls back to the previous board when the move fails', async () => {
    await loadBoard();
    const before = store.board();

    const moving = store.move(1, 'INTERVIEW', 0);
    expect(store.board()).not.toBe(before);
    http.expectOne(`${API}/applications/1/move`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(await moving).toBe('rolled-back');
    expect(store.board()).toBe(before);
  });

  it('reloads the board instead of rolling back when the card changed elsewhere (409)', async () => {
    await loadBoard();

    const moving = store.move(2, 'APPLIED', 0);
    http.expectOne(`${API}/applications/2/move`).flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    await flushMicrotasks();
    http.expectOne(BOARD_URL).flush(makeBoard({ ...postman, position: 0 }, { ...razorpay, position: 1 }, zoho));

    expect(await moving).toBe('reloaded');
    expect(columns().APPLIED).toEqual(['Postman:0', 'Razorpay:1']);
  });

  it('does not send a request when a card is dropped where it already is', async () => {
    await loadBoard();
    expect(await store.move(1, 'APPLIED', 0)).toBe('unchanged');
    http.expectNone(`${API}/applications/1/move`);
  });

  it('clears everything on logout', async () => {
    await loadBoard();

    TestBed.inject(AuthService).clearSession();

    expect(store.loaded()).toBe(false);
    expect(store.total()).toBe(0);
    expect(store.detail()).toBeNull();
  });

  it('drops a board response that arrives after logout (it belongs to the previous user)', async () => {
    const loading = store.loadBoard();
    TestBed.inject(AuthService).clearSession();
    http.expectOne(BOARD_URL).flush(makeBoard(razorpay));
    await loading;

    expect(store.total()).toBe(0);
    expect(store.loaded()).toBe(false);
  });

  it('adds a created card to the bottom of its column', async () => {
    await loadBoard();
    const creating = store.create({
      company: 'Groww', role: 'Frontend Engineer', status: 'APPLIED', jdUrl: null, jdText: null, location: null,
      salaryMinLpa: null, salaryMaxLpa: null, source: null, appliedOn: null, followUpOn: null,
      interviewAt: null,
    });
    http.expectOne(`${API}/applications`).flush(makeCard(7, 'APPLIED', 2, { company: 'Groww' }));
    await creating;

    expect(columns().APPLIED).toEqual(['Razorpay:0', 'Postman:1', 'Groww:2']);
  });

  it('moves an edited card whose status changed to the bottom of its new column', async () => {
    await loadBoard();
    const updating = store.update(1, {
      company: 'Razorpay', role: 'Engineer', status: 'INTERVIEW', jdUrl: null, jdText: null, location: null,
      salaryMinLpa: null, salaryMaxLpa: null, source: null, appliedOn: null, followUpOn: null, interviewAt: null, version: 3,
    });
    http.expectOne(`${API}/applications/1`).flush({ ...razorpay, status: 'INTERVIEW', position: 1, version: 4 });
    await updating;

    expect(columns()).toEqual({ APPLIED: ['Postman:0'], INTERVIEW: ['Zoho:0', 'Razorpay:1'] });
  });

  it('keeps an edited card in place when its status did not change', async () => {
    await loadBoard();
    const updating = store.update(2, {
      company: 'Postman Inc', role: 'Engineer', status: 'APPLIED', jdUrl: null, jdText: null, location: null,
      salaryMinLpa: null, salaryMaxLpa: null, source: null, appliedOn: null, followUpOn: null, interviewAt: null, version: 0,
    });
    http.expectOne(`${API}/applications/2`).flush({ ...postman, company: 'Postman Inc', version: 1 });
    await updating;

    expect(columns().APPLIED).toEqual(['Razorpay:0', 'Postman Inc:1']);
  });

  it('removes a deleted card and closes the gap', async () => {
    await loadBoard();
    const deleting = store.delete(1);
    http.expectOne(`${API}/applications/1`).flush(null, { status: 204, statusText: 'No Content' });
    await deleting;

    expect(columns().APPLIED).toEqual(['Postman:0']);
  });
});
