import { FormControl, FormGroup } from '@angular/forms';
import { matchesControl } from './matches-control';

describe('matchesControl', () => {
  function group(password: string, confirmation: string) {
    const form = new FormGroup({
      newPassword: new FormControl(password),
      confirmPassword: new FormControl(confirmation, matchesControl('newPassword')),
    });
    // A control is first validated before it has a parent, so the sibling was not visible yet
    form.controls.confirmPassword.updateValueAndValidity();
    return form;
  }

  it('is valid when both values are equal', () => {
    expect(group('N3w@Secret', 'N3w@Secret').controls.confirmPassword.errors).toBeNull();
  });

  it('reports a mismatch', () => {
    expect(group('N3w@Secret', 'Other@1').controls.confirmPassword.errors).toEqual({ mismatch: true });
  });

  it('leaves an empty confirmation to the required validator', () => {
    expect(group('N3w@Secret', '').controls.confirmPassword.errors).toBeNull();
  });
});
