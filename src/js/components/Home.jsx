import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import SingleNoticeView from './SingleNoticeView.jsx';
import NoticeTypeView from './NoticeTypeView.jsx';
import Nav from './Nav.jsx';
import {Button, Colors} from 'react-foundation';
import {Category, Events} from '../enums.js';
import pubsub from 'pubsub-js';

export default React.createClass({
  getInitialState() {
    return {
      selection: null
    };
  },

  componentWillMount() {
    this.pubsub_nav_token = pubsub.subscribe(Events.NAV, (navEvent, data) => {
      switch (data.action) {
        case Events.HOME:
          this.setState({ selection: null });
          break;
        case Events.NOTICE:
          this.setState({ selection: {id: data.id, category: data.category} });
          break;
        case Events.CATEGORY:
          this.setState({ selection: {category: data.category} });
          break;
      }
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_nav_token);
  },

  render() {
    let {care, maintenance, faq} = this.props;
    let {selection} = this.state;
    let mainEl, singleNoticeEl, noticeTypeEl;

    if (selection && selection.id != null) {
      const category = this.props[selection.category];
      const notice =category.filter((item) => { return item.id === selection.id})[0];
      singleNoticeEl = (<SingleNoticeView notice={notice} category={category}/>);
    }
    else if (selection && selection.category) {
      const props = {category: selection.category, notices: this.props[selection.category]}
      noticeTypeEl = (<NoticeTypeView {...props}/>);
    }
    else {
      mainEl = (
        <div>
        <NoticeList notices={care} category={Category.CARE}/>
        <NoticeList notices={maintenance} category={Category.MAINTENANCE}/>
        <NoticeList notices={faq} category={Category.FAQ}/>
        </div>
      );
    }

    return (
      <div>
        <h1>Harja Info</h1>
        <Nav />
          {mainEl}
          {singleNoticeEl}
          {noticeTypeEl}
      </div>
    );
  }
});
