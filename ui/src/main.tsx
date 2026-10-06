import React from 'react';
import ReactDOM from 'react-dom/client';
import { config } from '@fortawesome/fontawesome-svg-core';
import '@fortawesome/fontawesome-svg-core/styles.css';
import '@sbb-polarion/react-sbb-polarion/style.css';
import App from './App';
import './App.css';

// The icon styles come from the bundled stylesheet above. Font Awesome would otherwise add a <style>
// element at run time, which a Content Security Policy without 'unsafe-inline' refuses.
config.autoAddCss = false;

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
