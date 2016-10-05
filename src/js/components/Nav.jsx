import React, {PropTypes} from 'react';
import {Events, Category} from '../enums.js';
import NavButton from './NavButton.jsx'


export default React.createClass({
  render() {
    const links = [
      {title: 'HARJA-PROJEKTI', data: {action: Events.HOME}},
      {title: 'TEIDEN HOITO', data: {action: Events.CATEGORY, category: Category.CARE}},
      {title: 'TEIDEN YLLÄPITO', data: {action: Events.CATEGORY, category: Category.MAINTENANCE}},
      {title: 'UKK', data: {action: Events.CATEGORY, category: Category.FAQ}}
    ];

    return (
      <nav>
        <div className="harja-header title-bar show-for-large">
          <div className="top-bar-left">
            <img className="livi-header-logo" src="images/livi_logo_blue.png" alt="liikennevirasto logo" />
            <img className="harja-header-logo" src="images/harja_logo_text.png" alt="harja logo" srcSet="images/harja_logo_text.svg" />
          </div>
          <div className="top-bar-right">
            <div className="title-bar-title">Liikenneviraston Harja-projekti</div>
          </div>
        </div>

        <div className="harja-title-bar title-bar" data-responsive-toggle="top-menu" data-hide-for="large">
          <div className="top-bar-left">
            <img className="harja-menu-logo" src="images/harja_logo.png" alt="harja logo" />
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
                    <NavButton item={link} key={link.title}/>
                  </li>)
              }
              <li className="show-for-medium">
                <a className="button" href="apidoc/api.html" target="_blank">Katso API</a>
              </li>
              <li className="show-for-medium">
                <a className="button" href="https://github.com/finnishtransportagency/harja" target="_blank">Projekti GitHubissa</a>
              </li>
            </ul>
          </div>
        </div>
      </nav>
    );
  }
});
