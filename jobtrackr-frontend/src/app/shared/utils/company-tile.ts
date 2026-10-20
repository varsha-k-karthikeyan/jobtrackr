// The two-letter company tile used in the list, the bell menu and the details panel. Pure functions, so the
// same company gets the same letters and colour on every screen and in every session.

/** Pastel pairs defined as tokens in tailwind.css (--color-tile-<name>-soft / -ink). */
export const TILE_PALETTES = ['indigo', 'pink', 'violet', 'green', 'sky', 'amber'] as const;
export type TilePalette = (typeof TILE_PALETTES)[number];

/** First two letters or digits of the name, upper case: "Systech" -> "SY", "3M India" -> "3M". */
export function companyInitials(company: string): string {
  const characters = company.replace(/[^\p{L}\p{N}]/gu, '');
  return (characters.slice(0, 2) || '?').toUpperCase();
}

/**
 * A palette picked from the name with a small string hash (djb2), so the choice is stable and spread out.
 * Case and surrounding spaces are ignored: "Zoho" and " zoho " get the same colour.
 */
export function companyTilePalette(company: string): TilePalette {
  let hash = 5381;
  for (const character of company.trim().toLowerCase()) {
    hash = ((hash << 5) + hash + character.charCodeAt(0)) | 0;
  }
  return TILE_PALETTES[Math.abs(hash) % TILE_PALETTES.length];
}
