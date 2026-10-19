import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../auth/auth-service';
import { User } from '../../models/auth.model';
import { Shell } from './shell';

const API = environment.apiBaseUrl;
const user: User = { id: 7, email: 'varsha@test.dev', fullName: 'Varsha', createdAt: '2026-10-13T16:20:35Z', emailRemindersEnabled: true };

describe('Shell', () => {
  let component: Shell;
  let fixture: ComponentFixture<Shell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      // routerLink needs a Router; AuthService needs HttpClient (a test double, no real calls)
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Shell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('switches reminder emails off from the account menu', () => {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).loadMe().subscribe();
    http.expectOne(`${API}/me`).flush(user);

    // toggleEmailReminders is protected (template-only); element access is fine in a test
    component['toggleEmailReminders']();
    const request = http.expectOne(`${API}/me/email-reminders`);
    expect(request.request.body).toEqual({ enabled: false });
    request.flush({ ...user, emailRemindersEnabled: false });

    expect(component['user']()?.emailRemindersEnabled).toBe(false);
  });
});
