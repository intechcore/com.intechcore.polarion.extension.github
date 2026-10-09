import { describe, expect, it } from 'vitest';
import { NO_FILTERS } from '../src/services/itemFilters';
import { readPageOptions } from '../src/services/pageOptions';

// What the URL asks of the page of the GitHub items: nothing from the topic, the settings of the
// Live Report widget otherwise.

describe('page options', () => {
  it('lets the topic do everything, with no preset', () => {
    expect(readPageOptions('?feature=items&scope=project%2Felibrary%2F')).toEqual({
      widget: false,
      filters: NO_FILTERS,
      layout: null,
      hideFilters: false,
      allowCreate: true,
      showTime: false,
    });
    // Clear filters compares with NO_FILTERS itself.
    expect(readPageOptions('').filters).toBe(NO_FILTERS);
  });

  it('reads the settings of the widget', () => {
    const options = readPageOptions(
      '?widget=true&settings=tool%2C%20docs&kinds=ISSUE&states=NEW%2COUTDATED&columns=state%2Citem%2Cnone&hideFilters=true&allowCreate=true',
    );

    expect(options.filters).toEqual({
      ...NO_FILTERS,
      settings: ['tool', 'docs'],
      kinds: ['ISSUE'],
      states: ['NEW', 'OUTDATED'],
    });
    // An unknown column drops out; the chosen ones lead in their order, and the others are hidden.
    expect(options.layout!.order.slice(0, 2)).toEqual(['state', 'item']);
    expect(options.layout!.hidden).toHaveLength(7);
    expect(options.layout!.hidden).not.toContain('item');
    expect([options.widget, options.hideFilters, options.allowCreate]).toEqual([true, true, true]);
  });

  it('keeps a widget a report unless it allows creating, and the topic always shows its filters', () => {
    expect(readPageOptions('?widget=true').allowCreate).toBe(false);
    // Only a widget shows how long the report took.
    expect(readPageOptions('?widget=true&showTime=true').showTime).toBe(true);
    expect(readPageOptions('?showTime=true').showTime).toBe(false);
    expect(readPageOptions('?hideFilters=true').hideFilters).toBe(false);
  });
});
