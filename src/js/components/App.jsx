import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import {Button, Colors} from 'react-foundation';
import request from 'superagent';

export default React.createClass({
  propTypes: {
  },

  getDefaultProps() {
    return {

    }
  },

  getInitialState() {
    return {
      careNotices: [],
      maintenanceNotices: [],
      faqNotices: []
    };
  },

  componentDidMount() {
      this.getNotices('care.json', 'careNotices');
      this.getNotices('maintenance.json', 'maintenanceNotices');
      this.getNotices('faq.json', 'faqNotices');
  },

  getNotices(file, result) {
    const url = '../data/' + file;
    request.get(url)
      .set('Accept', 'application/json')
      .end((err, response) => {
        if (err) return console.error(err);
        this.setState({
          [result]: response.body,
        });
      });
  },

  render() {
    let {careNotices, maintenanceNotices, faqNotices} = this.state;
    return (
      <div>
        <h1>Learn Flux</h1>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList notices={careNotices} />
        <NoticeList notices={maintenanceNotices} />
        <NoticeList notices={faqNotices} />
      </div>
    );
  }
});
