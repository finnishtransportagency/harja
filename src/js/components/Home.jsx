import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import SingleNoticeView from './SingleNoticeView.jsx';
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

    this.pubsub_notice_token = pubsub.subscribe('noticeSelected', (action, selection) => {
      this.setState({ selection: selection });
    });

    this.pubsub_nav_token = pubsub.subscribe('mainNavigation', (action, link) => {
      console.log(action + " / " + link);
      this.setState({ selection: null });
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_notice_token);
    pubsub.unsubscribe(this.pubsub_nav_token);
  },

  render() {
    let {careNotices, maintenanceNotices, faqNotices} = this.props;
    let {selection} = this.state;
    let mainEl;
    let singleNoticeEl;

    if (selection) {
      const list = this.props[selection.list];
      const notice =list.filter((item) => { return item.id === selection.id})[0];
      singleNoticeEl = (<SingleNoticeView notice={notice} list={list}/>);
    }
    else {
      mainEl = (
        <div>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList notices={careNotices} list='careNotices'/>
        <NoticeList notices={maintenanceNotices} list='maintenanceNotices'/>
        <NoticeList notices={faqNotices} list='faqNotices'/>
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
