import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import Notice from './Notice.jsx';
import Nav from './Nav.jsx';
import {Button, Colors} from 'react-foundation';
import pubsub from 'pubsub-js';

export default React.createClass({
  getInitialState() {
    return {
      selection: null
    };
  },

  componentWillMount() {
    /*
    this.pubsub_token = pubsub.subscribe('noticeSelected', (action, notice) => {
      this.setState({ selection: notice });
    });
    */
    this.pubsub_token = pubsub.subscribe('mainNavigation', (action, link) => {
      console.log(action + " / " + link);
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_token);
  },

  render() {
    let {careNotices, maintenanceNotices, faqNotices} = this.props;
    let {selection} = this.state;
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
        <Nav />
        <h1>Harja Info</h1>
          {mainEl}
          {singleNoticeEl}
      </div>
    );
  }
});
