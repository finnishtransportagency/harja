import React from 'react';
import ReactDOM from 'react-dom';
import request from 'superagent';
import Home from './Home.jsx';
import {Category} from '../enums.js';

export default React.createClass({

  getInitialState() {
    let initialState = {
      Category: {}
    }
    initialState[Category.CARE] = []
    initialState[Category.MAINTENANCE] = []
    initialState[Category.FAQ] = []
    return initialState;
  },

  componentDidMount() {
    // Slow down fetching for development
    setTimeout(() => { this.getNotices('care.json', Category.CARE); }, 500);
    setTimeout(() => { this.getNotices('maintenance.json', Category.MAINTENANCE); }, 3000);
    setTimeout(() => { this.getNotices('faq.json', Category.FAQ); }, 5000);

    /*
    this.getNotices('care.json', 'careNotices');
    this.getNotices('maintenance.json', 'maintenanceNotices');
    this.getNotices('faq.json', 'faqNotices');
    */
  },

  getNotices(file, type) {
    const url = '../../data/' + file;
    request.get(url)
      .set('Accept', 'application/json')
      .end((err, response) => {
        if (err) return console.error(err);

        // 1. Create date from string
        // 2. Sort notices by date. Those with no date to bottom
        // 3. Add running index number and stringify date
        const notices = response.body.map((notice) => {
            let d = new Date(notice.date+'Z');
            if (isNaN( d.getTime() )) {
              d = null;
            }
            notice.date = d;
            return notice;
          })
          .sort((a,b) => {
              if (a.date === null && b.date === null) return 0;
              if (a.date === null) return 1;
              if (b.date === null) return -1;
              return b.date.getTime() - a.date.getTime()
          })
          .map((notice, index) => {
            notice.id = index;
            notice.type = type
            notice.date = notice.date === null ? '' : notice.date.toLocaleDateString('fi-FI');
            return notice;
          });

        this.setState({
          [type]: notices,
        });
      });
  },

  render () {


    return (
      <Home {...this.state}/>
    )
  }
});
