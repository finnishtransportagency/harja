import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import SingleNoticeView from './SingleNoticeView.jsx';
import NoticeTypeView from './NoticeTypeView.jsx';
import Nav from './Nav.jsx';
import Footer from './Footer.jsx';
import Hero from './Hero.jsx';
import DevLinks from './DevLinks.jsx';
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
    let heroEl, mainEl, singleNoticeEl, noticeTypeEl;

    if (selection && selection.id != null) {
      const notices = this.props[selection.category];
      const notice = notices.filter((item) => { return item.id === selection.id})[0];
      const props = {notice: notice, category: selection.category, notices: notices}
      singleNoticeEl = (<SingleNoticeView  {...props}/>);
    }
    else if (selection && selection.category) {
      const props = {category: selection.category, notices: this.props[selection.category]}
      noticeTypeEl = (<NoticeTypeView {...props}/>);
    }
    else {
      heroEl = <Hero />;
      mainEl = (
        <div className="row">
          <div className="medium-4 small-12 columns">
            <div className="harja-noticelist harja-care-noticelist">
              <NoticeList notices={care} category={Category.CARE} shorten={3}/>
            </div>
          </div>
          <div className="medium-4 small-12 columns">
            <div className="harja-noticelist harja-maintenance-noticelist">
              <NoticeList notices={maintenance} category={Category.MAINTENANCE} shorten={3}/>
            </div>
          </div>
          <div className="medium-4 small-12 columns">
            <div className="harja-noticelist harja-faq-noticelist">
              <NoticeList notices={faq} category={Category.FAQ} shorten={3}/>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div id="harja-home">
        <Nav />
        {heroEl}
        <DevLinks />
        <div id="harja-content">
          {mainEl}
          {singleNoticeEl}
          {noticeTypeEl}
        </div>
        <Footer />
      </div>
    );
  }
});
