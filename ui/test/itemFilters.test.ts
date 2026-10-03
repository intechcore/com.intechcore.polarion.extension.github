import { describe, expect, it } from 'vitest';
import { NONE, NO_FILTERS, applyFilters, optionsOf } from '../src/services/itemFilters';
import { ENTRIES } from './fixtures/items';

const numbers = (filters: Partial<typeof NO_FILTERS>) =>
  applyFilters(ENTRIES, { ...NO_FILTERS, ...filters }).map((entry) => entry.number);

describe('item filters', () => {
  it('shows everything without a filter', () => {
    expect(numbers({})).toEqual([7, 5, 9, 10, 30, 3]);
  });

  it('matches an item by any of the selected values of a filter', () => {
    expect(numbers({ settings: ['docs'] })).toEqual([3]);
    expect(numbers({ kinds: ['DISCUSSION'] })).toEqual([30]);
    expect(numbers({ githubTypes: ['Bug', 'Q&A'] })).toEqual([7, 5, 30]);
    expect(numbers({ workItemTypes: ['Defect'] })).toEqual([7, 5]);
    expect(numbers({ assignees: ['bob', 'carol'] })).toEqual([7, 3]);
    expect(numbers({ workItemAssignees: ['Rob Project'] })).toEqual([5]);
  });

  it('matches an item without a value by the empty choice', () => {
    expect(numbers({ assignees: [NONE] })).toEqual([9, 10, 30]);
    expect(numbers({ githubTypes: [NONE] })).toEqual([9, 10, 3]);
    expect(numbers({ workItemTypes: [NONE] })).toEqual([9]);
  });

  it('combines filters', () => {
    expect(numbers({ settings: ['tool'], states: ['NEW'], kinds: ['ISSUE'] })).toEqual([7, 10]);
  });

  it('counts a work item created on the page as one that exists', () => {
    const created = { ...ENTRIES[0], status: 'CREATED' as const };
    expect(applyFilters([created], { ...NO_FILTERS, states: ['EXISTS'] })).toEqual([created]);
  });

  it('searches the title, the number, the work item and the labels', () => {
    expect(numbers({ text: ' CSV ' })).toEqual([10]);
    expect(numbers({ text: '#30' })).toEqual([30]);
    expect(numbers({ text: 'el-12' })).toEqual([5]);
    expect(numbers({ text: 'wontfix' })).toEqual([9]);
    const untitled = { ...ENTRIES[0], title: null, labels: null };
    expect(applyFilters([untitled], { ...NO_FILTERS, text: '#7' })).toEqual([untitled]);
  });

  it('offers every value once, sorted, with the empty choice last', () => {
    expect(optionsOf(ENTRIES, 'assignees', undefined, 'Unassigned')).toEqual([
      { id: 'alice', name: 'alice' },
      { id: 'bob', name: 'bob' },
      { id: 'carol', name: 'carol' },
      { id: NONE, name: 'Unassigned' },
    ]);
    expect(optionsOf(ENTRIES, 'states', (state) => state.toLowerCase())).toEqual([
      { id: 'EXISTS', name: 'exists' },
      { id: 'NEW', name: 'new' },
      { id: 'SKIPPED', name: 'skipped' },
    ]);
    expect(optionsOf([{ ...ENTRIES[0], setting: null }], 'settings')).toEqual([{ id: NONE, name: '(none)' }]);
  });
});
