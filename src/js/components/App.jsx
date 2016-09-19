import React, { Component }  from 'react';
import { Router, Route, Link, IndexRoute, hashHistory, browserHistory } from 'react-router'
import ReactDOM from 'react-dom';
import Container from './Container.jsx';
import Home from './Home.jsx';

export default class App extends Component {
  render () {
    return (
      <Router history={hashHistory}>
        <Route path="/" component={Container}>
          <IndexRoute component={Home} />
        </Route>
      </Router>
    )
  }
}
