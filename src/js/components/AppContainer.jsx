import React from 'react';
import TodoStore from '../stores/TodoStore';
import ActionCreator from '../actions/TodoActionCreators';
import App from './App.jsx';

export default React.createClass({
  _onChange() {
    this.setState(TodoStore.getAll());
  },

  getInitialState() {
    return TodoStore.getAll();
  },

  componentDidMount() {
    TodoStore.addChangeListener(this._onChange);
  },

  componentWillUnmount() {
    TodoStore.removeChangeListener(this._onChange);
  },

  handleAddTask(e) {
    let title = prompt('Enter task title:');
    if (title) {
      ActionCreator.addItem(title);
    }
  },

  handleClear(e) {
    ActionCreator.clearList();
  },

  render() {
    let {tasks} = this.state;
    return (
      <App
        onAddTask={this.handleAddTask}
        onClear={this.handleClear}
        tasks={tasks} />
    );
  }
});
