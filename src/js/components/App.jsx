import React from 'react';
import ReactDOM from 'react-dom';
import request from 'superagent';
import Home from './Home.jsx';
import {Lists} from '../enums.js';

export default React.createClass({

  getInitialState() {
    let initialState = {
      lists: {}
    }
    initialState[Lists.CARE] = []
    initialState[Lists.MAINTENANCE] = []
    initialState[Lists.FAQ] = []
    return initialState;
  },

  componentDidMount() {
    setTimeout(() => { this.getNotices('care.json', Lists.CARE); }, 500);
    setTimeout(() => { this.getNotices('maintenance.json', Lists.MAINTENANCE); }, 3000);
    setTimeout(() => { this.getNotices('faq.json', Lists.FAQ); }, 5000);
    /*
    this.getNotices('care.json', 'careNotices');
    this.getNotices('maintenance.json', 'maintenanceNotices');
    this.getNotices('faq.json', 'faqNotices');
    */
  },

  getNotices(file, type) {
    const url = '../data/' + file;
    request.get(url)
      .set('Accept', 'application/json')
      .end((err, response) => {
        if (err) return console.error(err);

        const latestId = this.state[type].length
        const notices = response.body.map((notice, index) => {
          notice.id = latestId + index;
          notice.type = type
          return notice;
        });

        this.setState({
          [type]: response.body,
        });
      });
  },

  render () {


    return (
      <Home {...this.state}/>
    )
  }
});
