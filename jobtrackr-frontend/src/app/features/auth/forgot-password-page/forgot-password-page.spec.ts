import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ForgotPasswordPage } from './forgot-password-page';

const URL = `${environment.apiBaseUrl}/auth/forgot-password`;

describe('ForgotPasswordPage', () => {
  let http: HttpTestingController;

  function create() {
    TestBed.configureTestingModule({
      imports: [ForgotPasswordPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    return { fixture, component, form: component['form'] };
  }

  afterEach(() => http.verify());

  it('requires a valid email and sends nothing otherwise', () => {
    const { component, form } = create();
    form.controls.email.setValue('not-an-email');

    component['submit']();

    expect(form.controls.email.hasError('email')).toBe(true);
    http.expectNone(URL);
  });

  it('sends the email and shows the same neutral confirmation for any address', () => {
    const { component, form, fixture } = create();
    form.controls.email.setValue('varsha@example.com');

    component['submit']();
    const request = http.expectOne(URL);
    expect(request.request.body).toEqual({ email: 'varsha@example.com' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('If an account exists for varsha@example.com');
  });
});
