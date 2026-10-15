import { FormControl } from '@angular/forms';
import { passwordStrengthValidator } from './password-strength';

describe('passwordStrengthValidator', () => {
  const validate = (password: string) => passwordStrengthValidator(new FormControl(password));

  it('accepts a password with uppercase, lowercase, a special character and 8+ characters', () => {
    expect(validate('Demo@1234')).toBeNull();
    expect(validate('Pass_word')).toBeNull();
  });

  it('leaves an empty value to the required validator', () => {
    expect(validate('')).toBeNull();
  });

  it('lists every rule that is not met', () => {
    expect(validate('abc')).toEqual({ passwordStrength: { missing: ['length', 'uppercase', 'special'] } });
    expect(validate('Abcdefghij@klmnopqrst')).toEqual({ passwordStrength: { missing: ['length'] } }); // 21 chars
    expect(validate('NoSpecial123')).toEqual({ passwordStrength: { missing: ['special'] } });
    expect(validate('lowercase@1')).toEqual({ passwordStrength: { missing: ['uppercase'] } });
    expect(validate('UPPERCASE@1')).toEqual({ passwordStrength: { missing: ['lowercase'] } });
  });

  it('does not count whitespace as a special character (same as the backend)', () => {
    expect(validate('Has Space1')).toEqual({ passwordStrength: { missing: ['special'] } });
  });
});
