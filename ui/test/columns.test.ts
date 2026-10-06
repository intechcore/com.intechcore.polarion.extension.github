import { afterEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_LAYOUT, loadLayout, moveColumn, saveLayout, toggleColumn } from '../src/services/columns';

// The column layout of the items table, as the browser keeps it.

afterEach(() => {
  window.localStorage.clear();
  vi.restoreAllMocks();
});

describe('column layout', () => {
  it('starts with every column shown in its order', () => {
    expect(loadLayout()).toEqual(DEFAULT_LAYOUT);
  });

  it('drops unknown columns and adds the ones missing at the end', () => {
    window.localStorage.setItem(
      'github-items-columns',
      JSON.stringify({ order: ['status', 'gone', 'status', 'item'], hidden: ['labels', 'gone'] }),
    );

    const layout = loadLayout();

    expect(layout.order.slice(0, 3)).toEqual(['status', 'item', 'repository']);
    expect(layout.order).toHaveLength(DEFAULT_LAYOUT.order.length);
    expect(layout.hidden).toEqual(['labels']);
  });

  it('falls back to the default for a broken or foreign value', () => {
    window.localStorage.setItem('github-items-columns', '{broken');
    expect(loadLayout()).toEqual(DEFAULT_LAYOUT);
    window.localStorage.setItem('github-items-columns', JSON.stringify({ order: 'item' }));
    expect(loadLayout()).toEqual(DEFAULT_LAYOUT);
    window.localStorage.setItem('github-items-columns', JSON.stringify({ order: ['item'] }));
    expect(loadLayout().hidden).toEqual([]);
  });

  it('works without storage', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => saveLayout(DEFAULT_LAYOUT)).not.toThrow();
  });

  it('does not move a column past either end', () => {
    expect(moveColumn(DEFAULT_LAYOUT, 'repository', -1)).toBe(DEFAULT_LAYOUT);
    expect(moveColumn(DEFAULT_LAYOUT, 'workItemAssignees', 1)).toBe(DEFAULT_LAYOUT);
  });

  it('shows a hidden column again', () => {
    const hidden = toggleColumn(DEFAULT_LAYOUT, 'labels');
    expect(toggleColumn(hidden, 'labels').hidden).toEqual([]);
  });
});
