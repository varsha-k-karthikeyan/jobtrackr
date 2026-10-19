import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../../environments/environment';
import { ResetPasswordPage } from './reset-password-page';

const URL = `${environment.apiBaseUrl}/auth/reset-password`;

describe('ResetPasswordPage', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function create(token: string | null) {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ResetPasswordPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();
    // `form`, `submit` and `linkInvalid` are protected (template-only); element access is fine in a test
    const component = fixture.componentInstance;
    return { fixture, component, form: component['form'] };
  }

  afterEach(() => http.verify());

  it('removes the token from the address bar straight away, without a history entry', () => {
    create('abc123');

    expect(navigate).toHaveBeenCalledWith([], { queryParams: {}, replaceUrl: true });
  });

  it('shows the "link does not work" state when opened without a token', () => {
    const { fixture } = create(null);

    expect(fixture.nativeElement.textContent).toContain("This link doesn't work");
    expect(navigate).not.toHaveBeenCalled();
  });

  it('sends the token in the body with the new password, then goes to login with a confirmation', () => {
    const { component, form } = create('abc123');
    form.setValue({ newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });

    component['submit']();
    const request = http.expectOne(URL);
    expect(request.request.body).toEqual({ token: 'abc123', newPassword: 'N3w@Secret' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigate).toHaveBeenLastCalledWith(['/login'], { queryParams: { reset: 'done' } });
  });

  it('switches to "request a new link" when the link is used or expired', () => {
    const { component, form, fixture } = create('old-token');
    form.setValue({ newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });

    component['submit']();
    http.expectOne(URL).flush(
      { status: 400, detail: 'This reset link is invalid or has expired. Request a new one.' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(component['linkInvalid']()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Request a new link');
  });

  it('sends nothing while the passwords are invalid or do not match', () => {
    const { component, form } = create('abc123');
    form.setValue({ newPassword: 'N3w@Secret', confirmPassword: 'Different@1' });

    component['submit']();

    expect(form.controls.confirmPassword.hasError('mismatch')).toBe(true);
    http.expectNone(URL);
  });
});
