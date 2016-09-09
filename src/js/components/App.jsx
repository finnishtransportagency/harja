import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import Notice from './Notice.jsx';
import {Button, Colors} from 'react-foundation';
import request from 'superagent';
import pubsub from 'pubsub-js';

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
      faqNotices: [],
      selection: null
    };
  },

  componentDidMount() {
      this.getNotices('care.json', 'careNotices');
      this.getNotices('maintenance.json', 'maintenanceNotices');
      this.getNotices('faq.json', 'faqNotices');
  },

  componentWillMount() {
    this.pubsub_token = pubsub.subscribe('noticeSelected', (action, notice) => {
      this.setState({ selection: notice });
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_token);
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
    let {careNotices, maintenanceNotices, faqNotices, selection} = this.state;
    let mainEl;
    let singleNoticeEl;

    if (selection) {
      singleNoticeEl = (<Notice notice={selection} />);
    }
    else {
      mainEl = (
        <div>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList notices={careNotices} />
        <NoticeList notices={maintenanceNotices} />
        <NoticeList notices={faqNotices} />
        </div>
      );
    }

    return (
      <div>
        <h1>Harja Info</h1>
          {mainEl}
          {singleNoticeEl}
      </div>
    );
  }
});
