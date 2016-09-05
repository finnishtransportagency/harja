import React, {PropTypes} from 'react';
import TaskList from './TaskList.jsx';
import NoticeList from './NoticeList.jsx';
import {Button, Colors} from 'react-foundation';

var carenotices = require('../../data/carenotices.json');

export default React.createClass({
  propTypes: {
    tasks: PropTypes.array.isRequired,
    onAddTask: PropTypes.func.isRequired,
    onClear: PropTypes.func.isRequired
  },

  getDefaultProps() {
    return {
      tasks: [],
      carenotices: carenotices
    }
  },

  render() {
    let {onAddTask, onClear, tasks, carenotices} = this.props;
    return (
      <div>
        <h1>Learn Flux</h1>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList notices={carenotices} />
        <TaskList tasks={tasks} />
        <button onClick={onAddTask}>Add New</button>
        <button onClick={onClear}>Clear List</button>
      </div>
    );
  }
});
