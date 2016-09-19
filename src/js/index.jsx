import React from 'react';
import { Router, Route, Link, IndexRoute, hashHistory, browserHistory } from 'react-router'
import ReactDOM from 'react-dom';
import Nav from './components/Nav.jsx';
import Home from './components/Home.jsx';


ReactDOM.render(
  <Router history={hashHistory}>
    <Route path="/" component={Nav}>
      <IndexRoute component={Home} />

    </Route>
  </Router>,
  document.getElementById('main'));
//ReactDOM.render(<AppContainer />, document.getElementById('main'));
