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
  getInitialState() {
    return {
    };
  },

  getDefaultProps() {
    return {
      news: [],
      shorten: 0
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
              horizontal={false}
              >
              <ul>
                {news.map(notice =>
                  <ListItem notice={notice} key={notice.id + notice.type}/>
                )}
              </ul>
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
