import { makeBoard, makeCard } from '../testing/test-data';
import { appendCard, moveCard, normalizeBoard, removeCard, replaceCard } from './board-state';

const positions = (cards: { id: number; position: number }[]) => cards.map((card) => `${card.id}:${card.position}`);

describe('board-state', () => {
  const a = makeCard(1, 'APPLIED', 0);
  const b = makeCard(2, 'APPLIED', 1);
  const c = makeCard(3, 'APPLIED', 2);
  const x = makeCard(4, 'INTERVIEW', 0);
  const board = makeBoard(a, b, c, x);

  describe('moveCard', () => {
    it('moves down within a column and renumbers', () => {
      const result = moveCard(board, 1, 'APPLIED', 2);
      expect(positions(result.APPLIED)).toEqual(['2:0', '3:1', '1:2']);
    });

    it('moves up within a column and renumbers', () => {
      const result = moveCard(board, 3, 'APPLIED', 0);
      expect(positions(result.APPLIED)).toEqual(['3:0', '1:1', '2:2']);
    });

    it('moves across columns, closing the gap and updating the status', () => {
      const result = moveCard(board, 2, 'INTERVIEW', 0);
      expect(positions(result.APPLIED)).toEqual(['1:0', '3:1']);
      expect(positions(result.INTERVIEW)).toEqual(['2:0', '4:1']);
      expect(result.INTERVIEW[0].status).toBe('INTERVIEW');
    });

    it('clamps an out-of-range index like the backend does', () => {
      expect(positions(moveCard(board, 1, 'APPLIED', 99).APPLIED)).toEqual(['2:0', '3:1', '1:2']);
      expect(positions(moveCard(board, 1, 'INTERVIEW', 99).INTERVIEW)).toEqual(['4:0', '1:1']);
    });

    it('returns the same board object for a no-op, so callers can skip the request', () => {
      expect(moveCard(board, 2, 'APPLIED', 1)).toBe(board);
      expect(moveCard(board, 999, 'OFFER', 0)).toBe(board);
    });

    it('keeps untouched columns and cards by reference (cheap for OnPush)', () => {
      const result = moveCard(board, 1, 'APPLIED', 2);
      expect(result.INTERVIEW).toBe(board.INTERVIEW);
      expect(result.OFFER).toBe(board.OFFER);
      // A card whose position did not change keeps its object identity; a renumbered one is a new object
      expect(result.INTERVIEW[0]).toBe(x);
      expect(result.APPLIED[0]).not.toBe(b);
    });

    it('never mutates the input', () => {
      const snapshot = JSON.stringify(board);
      moveCard(board, 2, 'INTERVIEW', 0);
      expect(JSON.stringify(board)).toBe(snapshot);
    });
  });

  it('appendCard adds to the bottom of its column', () => {
    const created = makeCard(9, 'INTERVIEW', 1);
    expect(positions(appendCard(board, created).INTERVIEW)).toEqual(['4:0', '9:1']);
  });

  it('replaceCard swaps in the server copy in place', () => {
    const saved = { ...b, version: 5, company: 'Renamed' };
    const result = replaceCard(board, saved);
    expect(result.APPLIED[1]).toBe(saved);
    expect(positions(result.APPLIED)).toEqual(['1:0', '2:1', '3:2']);
  });

  it('removeCard closes the gap', () => {
    expect(positions(removeCard(board, 1).APPLIED)).toEqual(['2:0', '3:1']);
  });

  it('normalizeBoard fills in missing columns', () => {
    const normalized = normalizeBoard({ OFFER: [makeCard(5, 'OFFER', 0)] });
    expect(Object.keys(normalized)).toEqual(['APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED']);
    expect(normalized.APPLIED).toEqual([]);
  });
});
