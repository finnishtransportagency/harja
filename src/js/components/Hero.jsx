import React from 'react';
import NavButton from './NavButton.jsx'
import {Events, Category} from '../enums.js';

export default React.createClass({
  render() {

    const linkCare = {title: "Teiden hoito", data: {action: Events.CATEGORY, category: Category.CARE}};
    const moreCareEl = (
      <div className="harja-more harja-care">
        <NavButton item={linkCare} />
      </div>
    );

    const linkMaintenance = {title: "Teiden ylläpito", data: {action: Events.CATEGORY, category: Category.MAINTENANCE}};
    const moreMaintenanceEl = (
      <div className="harja-more harja-maintenance">
        <NavButton item={linkMaintenance} />
      </div>
    );

    const linkFaq = {title: "Koulutusvideot", data: {action: Events.CATEGORY, category: Category.FAQ}};
    const moreFaqEl = (
      <div className="harja-more harja-faq">
        <NavButton item={linkFaq} />
      </div>
    );

    return (
      <section className="harja-hero show-for-large">
        <div className="row column align-middle text-center">
          <h4>Liikenneviraston Harja-projekti</h4>
          <p>Liikenneviraston väylien kunnossapidon seurannan ja raportoinnin järjestelmä Harja</p>
          <div className="row">
            <div className="large-4 column">
              {moreCareEl}
            </div>
            <div className="large-4 column">
              {moreMaintenanceEl}
            </div>
            <div className="large-4 column">
              {moreFaqEl}
            </div>
          </div>
        </div>
      </section>
    );
  }
});
