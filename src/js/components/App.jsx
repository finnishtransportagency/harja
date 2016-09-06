import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import {Button, Colors} from 'react-foundation';

export default React.createClass({
  propTypes: {
    onAddTask: PropTypes.func.isRequired,
    onClear: PropTypes.func.isRequired
  },

  getDefaultProps() {
    return {
      carenotices: []
    }
  },

  render() {
    let {onAddTask, onClear} = this.props;
    return (
      <div>
        <h1>Learn Flux</h1>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList/>
      </div>
    );
  }
});
