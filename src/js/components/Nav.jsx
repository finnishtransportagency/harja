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
        <div className="harja-header title-bar show-for-medium">
          <div className="top-bar-left">
            <img className="livi-header-logo" src="styles/images/livi_logo_blue.png" alt="liikennevirasto logo" />
            <img className="harja-header-logo" src="styles/images/harja_logo_text.png" alt="harja logo" srcSet="styles/images/harja_logo_text.svg" />
          </div>
          <div className="top-bar-right">
            <div className="title-bar-title">Liikenneviraston Harja-projekti</div>
          </div>
        </div>

        <div className="harja-title-bar title-bar" data-responsive-toggle="top-menu" data-hide-for="medium">
          <div className="top-bar-left">
            <img className="harja-menu-logo" src="styles/images/harja_logo.png" alt="harja logo" />
          </div>
          <div className="top-bar-right">
            <button className="menu-icon" type="button" data-toggle></button>
          </div>
        </div>

        <div className="harja-menu top-bar" id="top-menu">
          <div className="top-bar-left">
            <ul className="vertical medium-horizontal menu" data-responsive-menu="medium-dropdown">
              {
                links.map((link, index) =>
                  <li key={index}>
                    <NavItem item={link} key={link.title}/>
                  </li>)
              }
            </ul>
          </div>
        </div>
      </div>
    );
  }
});
