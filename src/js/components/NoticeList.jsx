import React from 'react';
import Notice from './Notice.jsx';
import request from 'superagent';
import pubsub from 'pubsub-js';

var ListItem = React.createClass({
  onclick: function() {
    pubsub.publish('noticeSelected', this.props.item);
  },
  render: function() {
    return <div onClick={this.onclick}>{this.props.item.title}</div>;
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

    let {notices} = this.props;

    if (!notices.length > 0) {
      loadingEl = (<p>Loading...</p>);
    }
    else {
      noticesEl = (
        <ul>
          {notices.map(notice =>
            <ListItem item={notice} key={notice.id}/>//<Notice notice={notice} key={notice.id} />
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
