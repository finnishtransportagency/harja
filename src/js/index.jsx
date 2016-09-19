import React from 'react';
import { Router, Route, Link, IndexRoute, hashHistory, browserHistory } from 'react-router'
import ReactDOM from 'react-dom';
import Container from './components/Container.jsx';
import Home from './components/Home.jsx';


ReactDOM.render(
  <Router history={hashHistory}>
    <Route path="/" component={Container}>
      <IndexRoute component={Home} />
    </Route>
  </Router>,
  document.getElementById('main'));
//ReactDOM.render(<AppContainer />, document.getElementById('main'));
