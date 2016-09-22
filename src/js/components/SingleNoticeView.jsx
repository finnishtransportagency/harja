import React from 'react';
import Notice from './Notice.jsx';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: null,
      category: null
    };
  },

  render() {
    let {notice, category} = this.props;
    return (
      <Notice notice={notice}/>
    );
  }
});
