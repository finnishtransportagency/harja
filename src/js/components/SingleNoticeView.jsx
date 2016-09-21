import React from 'react';
import Notice from './Notice.jsx';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: null,
      list: null
    };
  },

  render() {
    let {notice, list} = this.props;
    return (
      <Notice notice={notice}/>
    );
  }
});
