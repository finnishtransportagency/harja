import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import SingleNoticeView from './SingleNoticeView.jsx';
import Nav from './Nav.jsx';
import {Button, Colors} from 'react-foundation';
import {Lists, Events} from '../enums.js';
import pubsub from 'pubsub-js';

export default React.createClass({
  getInitialState() {
    return {
      selection: null
    };
  },

  componentWillMount() {

    this.pubsub_notice_token = pubsub.subscribe(Events.NOTICE, (action, selection) => {
      this.setState({ selection: selection });
    });

    this.pubsub_nav_token = pubsub.subscribe(Events.NAV, (navEvent, data) => {
      console.log(navEvent + " / " + data);
      if (data.action === Events.HOME) {
        this.setState({ selection: null });
      }
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_notice_token);
    pubsub.unsubscribe(this.pubsub_nav_token);
  },

  render() {
    let {care, maintenance, faq} = this.props;
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
        <NoticeList notices={care} list={Lists.CARE}/>
        <NoticeList notices={maintenance} list={Lists.MAINTENANCE}/>
        <NoticeList notices={faq} list={Lists.FAQ}/>
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
