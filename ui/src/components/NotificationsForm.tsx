import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { NotificationSettings, ProjectOption } from '../types';

interface NotificationsFormProps {
  value: NotificationSettings;
  onChange: (value: NotificationSettings) => void;
  users: ProjectOption[];
}

/** Who hears of new items of the repository, and of which kinds. The watch job of the scheduler sends the mails. */
export default function NotificationsForm({ value, onChange, users }: Readonly<NotificationsFormProps>) {
  const set = (change: Partial<NotificationSettings>) => onChange({ ...value, ...change });
  const kinds: { key: 'issues' | 'discussions' | 'pullRequests' | 'advisories'; label: string }[] = [
    { key: 'issues', label: 'New issues' },
    { key: 'discussions', label: 'New discussions' },
    { key: 'pullRequests', label: 'Pull requests with failed checks' },
    { key: 'advisories', label: 'Security advisories' },
  ];

  return (
    <table className="settings-table">
      <tbody>
        <tr>
          <td>Mail about:</td>
          <td className="notification-kinds">
            {kinds.map(({ key, label }) => (
              <label key={key}>
                <input
                  type="checkbox"
                  id={`notify-${key}`}
                  checked={value[key]}
                  onChange={(e) => set({ [key]: e.target.checked })}
                />
                {label}
              </label>
            ))}
          </td>
        </tr>
        <tr>
          <td>
            <label htmlFor="notify-users">Recipients:</label>
          </td>
          <td>
            <SearchableSelect
              id="notify-users"
              multiple
              ariaLabel="Recipients"
              value={value.users}
              onChange={(users) => set({ users })}
              options={users.map((user) => ({ id: user.id, name: `${user.name} (${user.id})` }))}
              placeholder="Polarion users"
            />
          </td>
        </tr>
      </tbody>
    </table>
  );
}
