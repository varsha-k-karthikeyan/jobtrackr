// Display helpers for the signed-in user. The full name is optional at registration, so every helper falls
// back to the email address.

/** "Varsha Karthikeyan" -> "Varsha"; no name -> the part of the email before the @ ("varsha.k.karthikeyan"). */
export function firstName(fullName: string | null, email: string): string {
  const first = fullName?.trim().split(/\s+/)[0];
  return first || email.split('@')[0];
}

/**
 * Two letters for the avatar: first and last name initials ("Varsha Karthikeyan" -> "VK"), the first two
 * letters of a single name ("Varsha" -> "VA"), else of the email ("demo@..." -> "DE"). Always upper case.
 */
export function personInitials(fullName: string | null, email: string): string {
  const words = (fullName ?? '').trim().split(/\s+/).filter((word) => word.length > 0);
  const letters =
    words.length >= 2 ? words[0][0] + words[words.length - 1][0] : (words[0] ?? email.split('@')[0]).slice(0, 2);
  return letters.toUpperCase();
}
