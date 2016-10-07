import React from 'react';

export default React.createClass({
  render() {
    return (
      <footer id="harja-footer">
        <div className="row align-middle">
          <div className="columns show-for-medium medium-4 large-4">
            <img className="livi-footer-logo" src="images/livi_logo_white.png" alt="liikennevirasto logo" />
          </div>
          <div className="columns small-12 medium-8 large-8">
            <p>Tätä projektia ylläpitää <span><a href="http://www.liikennevirasto.fi/" target="_blank">Liikennevirasto</a></span></p>
          </div>
        </div>
      </footer>
    );
  }
});
