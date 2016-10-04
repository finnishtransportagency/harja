import React, {PropTypes} from 'react';
import {Events, Category} from '../enums.js';
import pubsub from 'pubsub-js';

export default React.createClass({

  getDefaultProps() {
    return {
      buttonStyle: null
    };
  },

  onclick: function() {
    pubsub.publish(Events.NAV, this.props.item.data);
  },

  render: function() {
    let className = 'button';
    if (this.props.item.buttonStyle) {
      className = this.props.item.buttonStyle;
    }
    return <a className={className} onClick={this.onclick}>{this.props.item.title}</a>;
  }
});
