import { About as RspAbout } from '@sbb-polarion/react-sbb-polarion';
import appIcon from '../assets/app-icon.svg';
import useRemote from '../services/useRemote';

/**
 * The standard extension About page, from react-sbb-polarion: the manifest table, the configuration
 * properties, the REST-token test and the README help article.
 */
export default function About() {
  const { sendRequest } = useRemote();
  return <RspAbout sendRequest={sendRequest} appIcon={appIcon} restApiUrl="/polarion/github/rest/api/version" />;
}
