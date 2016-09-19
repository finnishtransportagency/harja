import React, {PropTypes} from 'react';
import {Button, Colors} from 'react-foundation';
import pubsub from 'pubsub-js';

export default React.createClass({
  getInitialState() {
    return {
      selection: null
    };
  },

  componentWillMount() {
    this.pubsub_token = pubsub.subscribe('noticeSelected', (action, notice) => {
      this.setState({ selection: notice });
    });
  },

  componentWillUnmount: function() {
    pubsub.unsubscribe(this.pubsub_token);
  },

  render() {
    return (
      <div>
        <h1>HOME</h1>
      </div>
    );
  }
});
