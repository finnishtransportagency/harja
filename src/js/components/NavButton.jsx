import React, {PropTypes} from 'react';
import {Events, Category} from '../enums.js';
import pubsub from 'pubsub-js';

export default React.createClass({
  onclick: function() {
    pubsub.publish(Events.NAV, this.props.item.data);
  },

  render: function() {
    return <a className="button" onClick={this.onclick}>{this.props.item.title}</a>;
  }
});
