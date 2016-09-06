import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import {Button, Colors} from 'react-foundation';

export default React.createClass({
  propTypes: {
    careNoticesUrl: PropTypes.string.isRequired
  },

  getDefaultProps() {
    return {
      careNoticesUrl: 'carenotices.json'
    }
  },

  render() {
    let {careNoticesUrl} = this.props;
    return (
      <div>
        <h1>Learn Flux</h1>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList url={careNoticesUrl} />
      </div>
    );
  }
});
