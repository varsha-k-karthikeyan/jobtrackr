import { firstName, personInitials, timeOfDayGreeting } from './person';

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

  it('greets by the local time of day', () => {
    expect(timeOfDayGreeting(new Date(2026, 9, 20, 0, 30))).toBe('Good morning');
    expect(timeOfDayGreeting(new Date(2026, 9, 20, 11, 59))).toBe('Good morning');
    expect(timeOfDayGreeting(new Date(2026, 9, 20, 12, 0))).toBe('Good afternoon');
    expect(timeOfDayGreeting(new Date(2026, 9, 20, 16, 59))).toBe('Good afternoon');
    expect(timeOfDayGreeting(new Date(2026, 9, 20, 17, 0))).toBe('Good evening');
  });
});
