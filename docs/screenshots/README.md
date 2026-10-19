# Screenshots

Images used by the main [README](../../README.md). Once a file below exists, uncomment its line in the README's "Screenshots" section (it is commented out so GitHub never shows a broken image).

## How to capture

- Run the full stack (`docker compose --profile full up --build`), open http://localhost:4200 and use **Try the demo**, so every image shows the same public sample data and nothing personal.
- Browser window about 1440 x 900, zoom 100%, light theme. Hide bookmarks bars and extensions.
- PNG for screenshots. Crop to the app (no browser chrome) except where noted.
- Keep each file under about 1 MB (a PNG optimiser such as `oxipng` or squoosh.app helps), and the GIF under about 8 MB so it loads quickly on GitHub.

## The list

| File | What to show |
|---|---|
| `board.png` | The board with all four columns filled, the stats strip, and at least one red "Overdue" follow-up chip |
| `drag.png` | A card mid-drag between two columns, with the drop placeholder visible in the target column |
| `drawer.png` | The details drawer open on a card that has two or three notes |
| `list-filters.png` | The List view with a search term, a status filter, the salary slider moved, and a sorted column |
| `bell.png` | The header bell with its red count, menu open, showing due items (one in red) and the "Upcoming" section |
| `login.png` | The login page with the "Try the demo" button and "Forgot password?" link |
| `swagger.png` | Swagger UI at http://localhost:8080/swagger-ui.html with the endpoint groups expanded (browser chrome may stay, it shows the URL) |
| `demo.gif` | The 30 to 60 second tour below |

## GIF script (30 to 60 seconds)

Record at 1280 x 800 with a screen recorder that exports GIF (for example ScreenToGif on Windows), 10 to 15 frames per second.

1. **0-5 s** Login page. Pause a moment on it, then click **Try the demo**.
2. **5-15 s** The board appears. Slowly drag an Applied card into the Interview column and drop it; let the card settle.
3. **15-25 s** Click a card. The details drawer slides in; scroll its notes, then close it.
4. **25-40 s** Click the bell. The menu shows due and overdue follow-ups; click the X on one of them and let the count drop by one.
5. **40-50 s** Switch to **List**, type a company name into the search box and watch the table filter.
6. **50-60 s** Reload the page to show you are still signed in. End on the board.

Keep the mouse movements slow and deliberate; trim dead time at the start and end.
