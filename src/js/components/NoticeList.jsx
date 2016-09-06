import React from 'react';
import Notice from './Notice.jsx';
import request from 'superagent';

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

  componentDidMount() {
  },

  render() {
    var loadingEl;
    var noticesEl;

    let {notices} = this.props;

    if (!notices.length > 0) {
      loadingEl = (<p>Loading...</p>);
    }
    else {
      noticesEl = (
        <ul>
          {notices.map(notice =>
            <Notice notice={notice} key={notice.title} />
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
