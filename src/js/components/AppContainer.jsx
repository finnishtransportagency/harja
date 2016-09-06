import React from 'react';
import App from './App.jsx';

export default React.createClass({
  _onChange() {
    //this.setState(TodoStore.getAll());
  },

  getInitialState() {
    //return TodoStore.getAll();
    return null;
  },

  componentDidMount() {
    //TodoStore.addChangeListener(this._onChange);
  },

  componentWillUnmount() {
    //TodoStore.removeChangeListener(this._onChange);
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

    return (
      <App
        onAddTask={this.handleAddTask}
        onClear={this.handleClear}
        />
    );
  }
});
