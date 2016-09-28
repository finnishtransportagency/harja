import React from 'react';
import Notice from './Notice.jsx';
import NoticeList from './NoticeList.jsx';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: null,
      category: null,
      notices: []
    };
  },

  render() {
    let {notice, category, notices} = this.props;
    return (
      <div className="row">
        <div className="medium-8 columns">
          <Notice notice={notice}/>
        </div>
        <div className="medium-4 columns">
          <NoticeList notices={notices} category={category}/>
        </div>
      </div>
    );
  }
});
