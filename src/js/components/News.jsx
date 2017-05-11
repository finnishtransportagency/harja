import React from 'react';

import ScrollArea from 'react-scrollbar';
import Notice from './Notice.jsx';
import NavButton from './NavButton.jsx';
import request from 'superagent';
import pubsub from 'pubsub-js';
import {Events, Category} from '../enums.js';

let ListItem = React.createClass({
  onclick: function() {
    pubsub.publish(Events.NAV, {action: Events.NOTICE, id: this.props.notice.id, category: this.props.notice.type});
  },
  render: function() {
    return (
      <div className="harja-newslist-item">
        <div className="row">
          <div className="column large-1 large-offset-2 medium-3 medium-offset-1 small-3">
            <div className="harja-date">{this.props.notice.displayDate}</div>
          </div>
          <div className="column large-9 medium-6 small-9">
            <a className="harja-news-link" href="#" onClick={this.onclick}>{this.props.notice.title}</a>
          </div>
        </div>
      </div>
    );
  }
});


export default React.createClass({
  getDefaultProps() {
    return {
      news: [],
    }
  },

  render() {
    let loadingEl, newsEl;
    let {news} = this.props;

    if (!news.length > 0) {
      loadingEl = (
        <img className="harja-loading" src="images/ajax-loader.gif" alt="ladataan..." />
      );
    }
    else {
      newsEl = (
          <ScrollArea
              speed={0.8}
              className="area"
              horizontal={false}>
              <ScrollContent news={news}/>
          </ScrollArea>
      );
    }

    return (
      <div className="harja-newslist">
        <h3 className="show-for-large">Uutiset</h3>
        {loadingEl}
        {newsEl}
      </div>
    );
  }
});


class ScrollContent extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
        feedLength: 5,
    };
  }

  componentDidUpdate() {
    //TODO bug in react-scrollbar prevents us to automatically scrolling
    // this.context.scrollArea.scrollBottom();
  }

  render() {
    const {feedLength} = this.state;
    let {news} = this.props;
    let moreEl = null;

    if (news.length > feedLength) {
      moreEl = (
          <div className="harja-newslist-item">
            <div className="row column small-4 small-offset-4">
              <button onClick={()=>this.handleMoreNews()}>Lisää uutisia</button>
            </div>
          </div>
      );
    }

    return (
      <ul>
          {news.slice(0, feedLength).map(notice =>
            <ListItem notice={notice} key={notice.id + notice.type}/>
          )}
          {moreEl}
      </ul>
    );
  }

  handleMoreNews(){
    this.setState({
      feedLength: this.state.feedLength + 5,
    });
  }
}

ScrollContent.contextTypes = {
    scrollArea: React.PropTypes.object
};
