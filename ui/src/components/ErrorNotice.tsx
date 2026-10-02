import type { ReactNode } from 'react';

/**
 * A persistent error message on a page. RSP styles `.alert` only inside `.notifications`, the markup
 * of generic's notifications: without that wrapper the message renders as plain text.
 */
export default function ErrorNotice({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="notifications">
      <div className="alert alert-error">{children}</div>
    </div>
  );
}
