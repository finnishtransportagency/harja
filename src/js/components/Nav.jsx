import React, {PropTypes} from 'react';
import {Button, ButtonGroup, Link, Colors} from 'react-foundation';
import {Events, Category} from '../enums.js';
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
      {title: 'TEIDEN HOITO', data: {action: Events.CATEGORY, category: Category.CARE}},
      {title: 'TEIDEN YLLÄPITO', data: {action: Events.CATEGORY, category: Category.MAINTENANCE}},
      {title: 'UKK', data: {action: Events.CATEGORY, category: Category.FAQ}}
    ];

    return (
      <div>
        <div className="title-bar" data-responsive-toggle="example-menu" data-hide-for="medium">
          <button className="menu-icon" type="button" data-toggle></button>
          <div className="title-bar-title">Menu</div>
        </div>

        <div className="top-bar" id="example-menu">
          <div className="top-bar-left">
            <ul className="vertical medium-horizontal menu" data-responsive-menu="medium-dropdown">
              <li className="menu-text">Site Title</li>
              {links.map(link => <li><NavItem item={link} key={link.title}/></li>)}
            </ul>
          </div>
        </div>
      </div>
    );
  }
});
