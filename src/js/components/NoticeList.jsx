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

    if (!this.props.notices.length > 0) {
      loadingEl = (<p>Loading...</p>);
    }
    else {
      let {notices} = this.props;
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
