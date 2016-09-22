import React from 'react';
import Notice from './Notice.jsx';
import request from 'superagent';
import pubsub from 'pubsub-js';
import {Events} from '../enums.js';

var ListItem = React.createClass({
  onclick: function() {
    pubsub.publish(Events.NOTICE, {id: this.props.notice.id, list: this.props.list});
  },
  render: function() {
    return <div onClick={this.onclick}>{this.props.notice.title}</div>;
  }
});

export default React.createClass({
  getInitialState() {
    return {
    };
  },

  getDefaultProps() {
    return {
      notices: []
    }
  },

  render() {
    let loadingEl;
    let noticesEl;

    let {notices, list} = this.props;

    if (!notices.length > 0) {
      loadingEl = (<p>Loading...</p>);
    }
    else {
      noticesEl = (
        <ul>
          {notices.map(notice =>
            <ListItem notice={notice} key={notice.id} list={list}/>//<Notice notice={notice} key={notice.id} />
          )}
        </ul>
      );
    }

    return (
      <div>
        {loadingEl}
        {noticesEl}
      </div>
    );
  }
});
