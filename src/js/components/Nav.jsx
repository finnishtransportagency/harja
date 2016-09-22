import React, {PropTypes} from 'react';
import {Button, ButtonGroup, Link, Colors} from 'react-foundation';
import {Events, Lists} from '../enums.js';
import pubsub from 'pubsub-js';

var NavItem = React.createClass({
  onclick: function() {
    pubsub.publish(Events.NAV, this.props.item.data);
  },
  render: function() {
    return <Link onClick={this.onclick}>{this.props.item.title}</Link>;
  }
});

export default React.createClass({
  render() {
    const links = [
      {title: 'HARJA-PROJEKTI', data: {action: Events.HOME}},
      {title: 'TEIDEN HOITO', data: {action: Events.LIST, list: Lists.CARE}},
      {title: 'TEIDEN YLLÄPITO', data: {action: Events.LIST, list: Lists.MAINTENANCE}},
      {title: 'UKK', data: {action: Events.LIST, list: Lists.FAQ}}
    ];

    return (
      <div className="main-nav">
        <ButtonGroup>
          {links.map(link =>
            <NavItem item={link} key={link.title}/>
          )}
        </ButtonGroup>
      </div>
    );
  }
});
