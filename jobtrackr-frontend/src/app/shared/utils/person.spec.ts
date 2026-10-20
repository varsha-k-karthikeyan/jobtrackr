import { firstName, personInitials } from './person';

describe('person', () => {
  it('uses the first word of the full name, else the email before the @', () => {
    expect(firstName('Varsha Karthikeyan', 'varsha@test.dev')).toBe('Varsha');
    expect(firstName('  Varsha  ', 'varsha@test.dev')).toBe('Varsha');
    expect(firstName(null, 'varsha.k.karthikeyan@gmail.com')).toBe('varsha.k.karthikeyan');
    expect(firstName('   ', 'demo@jobtrackr.dev')).toBe('demo');
  });

  it('makes two upper-case initials from the name, else from the email', () => {
    expect(personInitials('Varsha Karthikeyan', 'x@test.dev')).toBe('VK');
    expect(personInitials('Varsha K Karthikeyan', 'x@test.dev')).toBe('VK');
    expect(personInitials('Varsha', 'x@test.dev')).toBe('VA');
    expect(personInitials(null, 'demo@jobtrackr.dev')).toBe('DE');
  });
});
