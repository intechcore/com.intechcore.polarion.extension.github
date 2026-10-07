import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

/** The icon in front of the label of a button. */
export default function ButtonIcon({ icon }: Readonly<{ icon: IconDefinition }>) {
  return <FontAwesomeIcon icon={icon} className="button-icon" />;
}
