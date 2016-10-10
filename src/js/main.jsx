import 'babel-polyfill';
import React from 'react';
import ReactDOM from 'react-dom';
import App from './components/App.jsx';

global.$ = global.jQuery = require('jquery');
require('foundation-sites');

ReactDOM.render(<App />, document.getElementById('main'));

$(document).foundation();
