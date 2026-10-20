import { companyInitials, companyTilePalette, TILE_PALETTES } from './company-tile';

describe('company tile', () => {
  it('takes the first two letters or digits, upper case', () => {
    expect(companyInitials('Systech')).toBe('SY');
    expect(companyInitials('ABCD')).toBe('AB');
    expect(companyInitials('3M India')).toBe('3M');
    expect(companyInitials('  .io Labs')).toBe('IO');
    expect(companyInitials('Z')).toBe('Z');
    expect(companyInitials('!!')).toBe('?');
  });

  it('always picks the same palette for the same company, ignoring case and spaces', () => {
    expect(companyTilePalette('Zoho')).toBe(companyTilePalette('Zoho'));
    expect(companyTilePalette(' zoho ')).toBe(companyTilePalette('Zoho'));
    expect(TILE_PALETTES).toContain(companyTilePalette('Freshworks'));
  });

  it('spreads different companies over the palettes', () => {
    const companies = ['Razorpay', 'Postman', 'Darwinbox', 'Chargebee', 'Freshworks', 'PhonePe', 'Zoho', 'Thoughtworks', 'Meesho', 'Zerodha', 'Groww', 'Keka', 'Swiggy', 'Flipkart', 'TCS'];
    expect(new Set(companies.map(companyTilePalette)).size).toBeGreaterThanOrEqual(4);
  });
});
