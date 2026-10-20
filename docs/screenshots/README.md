# Screenshots

Images used by the main [README](../../README.md). All are taken from the public demo account (**Try the demo**), so they show the same sample data and nothing personal. The approved designs these screens follow are in [`docs/design/`](../design).

## How they were captured

- The app running locally (`docker compose up -d`, the backend, `npm start`), signed in with **Try the demo**.
- A headless Edge window of 1440 x 900 at 100% zoom, light colour scheme, captured as PNG straight from the page, so there is no browser chrome. Each file is under 200 kB.
- For `bell.png`, the Postman card was given a temporary interview tomorrow at 11:15 AM so both sections of the menu show; it was removed again afterwards, so the demo data is unchanged.

To retake them after a visual change, use the same window size and the demo account, and keep the same file names.

## The list

| File | What it shows |
|---|---|
| `board.png` | The board: welcome band with the greeting, "N follow-ups due" and the stats; four status columns with job cards and overdue / interview pills |
| `drag.png` | A card mid-drag between two columns, with the dashed drop placeholder in the target column |
| `drawer.png` | The details panel on a card with a job description and three notes |
| `list-filters.png` | The List view with a search term, the salary filter moved (20+ LPA) and the table sorted by max salary |
| `bell.png` | The bell menu open: an interview tomorrow, and the due follow-ups with their initials tiles and overdue pills |
| `login.png` | The login page with "Forgot password?" and the "Try the demo" button |
| `swagger.png` | Swagger UI at http://localhost:8080/swagger-ui.html with the endpoint groups |
| `demo.gif` | Not recorded yet: the 30 to 60 second tour below (its line in the README is commented out until it exists) |

## GIF script (30 to 60 seconds)

Record at 1280 x 800 with a screen recorder that exports GIF (for example ScreenToGif on Windows), 10 to 15 frames per second, and keep it under about 8 MB.

1. **0-5 s** Login page. Pause a moment on it, then click **Try the demo**.
2. **5-15 s** The board appears. Slowly drag an Applied card into the Interview column and drop it; let the card settle.
3. **15-25 s** Click a card. The details panel slides in; scroll to its notes, then close it.
4. **25-40 s** Click the bell. The menu shows interviews and due follow-ups; click the X on one follow-up and let the count drop by one.
5. **40-50 s** Switch to **List**, type a company name into the search box and watch the table filter.
6. **50-60 s** Reload the page to show you are still signed in. End on the board.

Keep the mouse movements slow and deliberate; trim dead time at the start and end.
