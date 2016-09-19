import React from 'react';
import App from './App.jsx';
import request from 'superagent';

export default React.createClass({
  getInitialState() {
    return {
      careNotices: [],
      maintenanceNotices: [],
      faqNotices: []
    };
  },

  componentDidMount() {
    setTimeout(() => { this.getNotices('care.json', 'careNotices'); }, 500);
    setTimeout(() => { this.getNotices('maintenance.json', 'maintenanceNotices'); }, 3000);
    setTimeout(() => { this.getNotices('faq.json', 'faqNotices'); }, 5000);
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

  render() {
    return (
      <div>
        <App
            careNotices={this.state.careNotices}
            maintenanceNotices={this.state.maintenanceNotices}
            faqNotices={this.state.faqNotices}/>
          {this.props.children}
      </div>
    );
  }
});
