import React, {PropTypes} from 'react';
import {Button, ButtonGroup, Link, Colors} from 'react-foundation';
import pubsub from 'pubsub-js';

var NavItem = React.createClass({
  onclick: function() {
    pubsub.publish('mainNavigation', this.props.item.action);
  },
  render: function() {
    return <Link onClick={this.onclick}>{this.props.item.title}</Link>;
  }
});

export default React.createClass({
  render() {
    const links = [
      {title: 'HARJA-PROJEKTI', action: 'home', id: 1},
      {title: 'TEIDEN HOITO', action: 'care', id: 2},
      {title: 'TEIDEN YLLÄPITO', action: 'maintenance', id: 3},
      {title: 'UKK', action: 'faq', id: 4}
    ];

    return (
      <div className="main-nav">
        <ButtonGroup>
          {links.map(link =>
            <NavItem item={link} key={link.id}/>
          )}
        </ButtonGroup>
      </div>
    );
  }
});
