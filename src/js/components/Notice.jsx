import React from 'react';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: {
        title: '',
        body: ''
      }
    };
  },

  handleToggle(notice) {
  },

  render() {
    let {notice} = this.props;
    return (
      <li className="notice">
        <label>{notice.title}</label>
        <label>{notice.body}</label>
      </li>
    );
  }
});
