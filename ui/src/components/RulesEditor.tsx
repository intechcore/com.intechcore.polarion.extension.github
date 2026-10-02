import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { ProjectField, ProjectOption, RuleMatch } from '../types';
import FieldValues, { type FieldRow } from './FieldValues';

/** The form state of one rule: ItemRule with the field values as an ordered list. */
export interface RuleForm {
  match: RuleMatch;
  value: string;
  skip: boolean;
  workItemType: string;
  fields: FieldRow[];
}

const MATCH_OPTIONS: Record<string, { id: RuleMatch; name: string }[]> = {
  issues: [
    { id: 'LABEL', name: 'Label' },
    { id: 'TYPE', name: 'Issue type' },
  ],
  discussions: [
    { id: 'LABEL', name: 'Label' },
    { id: 'CATEGORY', name: 'Category' },
  ],
};

interface RulesEditorProps {
  /** `issues` or `discussions`: what the rules apply to, and the prefix of the control labels. */
  kind: string;
  rules: RuleForm[];
  onChange: (rules: RuleForm[]) => void;
  workItemTypes: ProjectOption[];
  loadFields: (workItemType: string) => Promise<ProjectField[]>;
}

/**
 * The rules of one kind of GitHub item. A rule gives the items that carry a label, an issue type or
 * a category another work item type and field values, or leaves them out. The first matching rule
 * applies, so the rules can be moved up and down.
 */
export default function RulesEditor({ kind, rules, onChange, workItemTypes, loadFields }: Readonly<RulesEditorProps>) {
  const setRule = (index: number, change: Partial<RuleForm>) =>
    onChange(rules.map((rule, i) => (i === index ? { ...rule, ...change } : rule)));
  const move = (index: number, to: number) => {
    const next = [...rules];
    [next[index], next[to]] = [next[to], next[index]];
    onChange(next);
  };

  return (
    <div className="rules">
      {rules.map((rule, index) => {
        const owner = `rule ${index + 1} of ${kind}`;
        return (
          // A rule has no identity of its own. Moving one swaps two whole rows, which an index key renders right.
          <div className="rule" key={index}>
            <div className="rule-line">
              <span className="rule-number">{index + 1}.</span>
              <span>If</span>
              <SearchableSelect
                ariaLabel={`What ${owner} compares`}
                value={rule.match}
                onChange={(match) => setRule(index, { match: match as RuleMatch })}
                options={MATCH_OPTIONS[kind]}
                searchable={false}
              />
              <span>is</span>
              <input
                type="text"
                aria-label={`Value of ${owner}`}
                value={rule.value}
                onChange={(e) => setRule(index, { value: e.target.value })}
              />
              <button
                type="button"
                className="sbb-btn sbb-btn--control"
                aria-label={`Move ${owner} up`}
                disabled={index === 0}
                onClick={() => move(index, index - 1)}
              >
                Up
              </button>
              <button
                type="button"
                className="sbb-btn sbb-btn--control"
                aria-label={`Move ${owner} down`}
                disabled={index === rules.length - 1}
                onClick={() => move(index, index + 1)}
              >
                Down
              </button>
              <button
                type="button"
                className="sbb-btn sbb-btn--control"
                aria-label={`Remove ${owner}`}
                onClick={() => onChange(rules.filter((_, i) => i !== index))}
              >
                Remove rule
              </button>
            </div>
            <div className="rule-line">
              <input
                id={`${kind}-rule-${index + 1}-skip`}
                type="checkbox"
                checked={rule.skip}
                onChange={(e) => setRule(index, { skip: e.target.checked })}
              />
              <label htmlFor={`${kind}-rule-${index + 1}-skip`}>Do not import</label>
              {!rule.skip && (
                <>
                  <span>Work item type:</span>
                  <SearchableSelect
                    ariaLabel={`Work item type of ${owner}`}
                    value={rule.workItemType}
                    onChange={(workItemType) => setRule(index, { workItemType })}
                    options={workItemTypes}
                    allowEmpty
                  />
                </>
              )}
            </div>
            {!rule.skip && (
              <div className="rule-fields">
                <FieldValues
                  owner={owner}
                  workItemType={rule.workItemType}
                  rows={rule.fields}
                  onChange={(fields) => setRule(index, { fields })}
                  loadFields={loadFields}
                />
              </div>
            )}
          </div>
        );
      })}
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        onClick={() => onChange([...rules, { match: 'LABEL', value: '', skip: false, workItemType: '', fields: [] }])}
      >
        Add a rule
      </button>
    </div>
  );
}
