import React from 'react';
import { Router, Route, Link, IndexRoute, hashHistory, browserHistory } from 'react-router'
import ReactDOM from 'react-dom';
import AppContainer from './components/AppContainer.jsx';
import Home from './components/Home.jsx';


ReactDOM.render(
  <Router history={hashHistory}>
    <Route path="/" component={AppContainer}>
      <IndexRoute component={Home} />

    </Route>
  </Router>,
  document.getElementById('main'));
//ReactDOM.render(<AppContainer />, document.getElementById('main'));
