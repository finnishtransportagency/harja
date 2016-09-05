import React from 'react';
import Notice from './Notice.jsx';

export default React.createClass({
  getDefaultProps() {
    return {
      notices: []
    };
  },

  render() {
    let {notices} = this.props;
    return (
      <ul>
        {notices.map(notice =>
          <Notice notice={notice} key={notice.title} />
        )}
      </ul>
    );
  }
});
