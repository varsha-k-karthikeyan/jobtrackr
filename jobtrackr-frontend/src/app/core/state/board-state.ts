import { Application, APPLICATION_STATUSES, ApplicationStatus, Board } from '../models/application.model';

// Pure, immutable board transforms used by ApplicationStore. Each returns a NEW Board and replaces only
// the columns it touches, so unchanged columns keep their array identity (cheap for OnPush and @for).
// Positions are kept zero-based and gap-free, exactly like the backend's ApplicationService.

export function emptyBoard(): Board {
  return { APPLIED: [], INTERVIEW: [], OFFER: [], REJECTED: [] };
}

/** Guarantees all four columns exist, in enum order, whatever the server sent. */
export function normalizeBoard(board: Partial<Board>): Board {
  const normalized = emptyBoard();
  for (const status of APPLICATION_STATUSES) {
    normalized[status] = board[status] ?? [];
  }
  return normalized;
}

export function findCard(board: Board, id: number): Application | undefined {
  for (const status of APPLICATION_STATUSES) {
    const card = board[status].find((candidate) => candidate.id === id);
    if (card) {
      return card;
    }
  }
  return undefined;
}

/** Rewrites `position` to match the array index; untouched cards keep their object identity. */
function renumber(cards: Application[]): Application[] {
  return cards.map((card, index) => (card.position === index ? card : { ...card, position: index }));
}

/**
 * Moves a card to `targetIndex` in `targetStatus`, clamping the index the same way the backend does
 * (last slot in the same column, or one past the end of another column). Returns the SAME board object
 * when nothing changes, so callers can detect a no-op with `===`.
 */
export function moveCard(board: Board, id: number, targetStatus: ApplicationStatus, targetIndex: number): Board {
  const card = findCard(board, id);
  if (!card) {
    return board;
  }
  const sourceStatus = card.status;
  const sourceCards = board[sourceStatus].filter((candidate) => candidate.id !== id);

  if (sourceStatus === targetStatus) {
    const index = clamp(targetIndex, 0, sourceCards.length);
    if (index === card.position) {
      return board;
    }
    const reordered = [...sourceCards];
    reordered.splice(index, 0, card);
    return { ...board, [sourceStatus]: renumber(reordered) };
  }

  const targetCards = [...board[targetStatus]];
  const index = clamp(targetIndex, 0, targetCards.length);
  targetCards.splice(index, 0, { ...card, status: targetStatus });
  return { ...board, [sourceStatus]: renumber(sourceCards), [targetStatus]: renumber(targetCards) };
}

/** Adds a newly created card at the bottom of its column (where the backend put it). */
export function appendCard(board: Board, card: Application): Board {
  return { ...board, [card.status]: renumber([...board[card.status], card]) };
}

/** Swaps in the server's copy of a card (fresh version, timestamps), keeping its place on the board. */
export function replaceCard(board: Board, updated: Application): Board {
  const column = board[updated.status];
  const index = column.findIndex((candidate) => candidate.id === updated.id);
  if (index === -1) {
    return board;
  }
  const replaced = [...column];
  replaced[index] = updated;
  return { ...board, [updated.status]: renumber(replaced) };
}

/** Removes a card and closes the gap it leaves, as the backend's delete does. */
export function removeCard(board: Board, id: number): Board {
  const card = findCard(board, id);
  if (!card) {
    return board;
  }
  return { ...board, [card.status]: renumber(board[card.status].filter((candidate) => candidate.id !== id)) };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
