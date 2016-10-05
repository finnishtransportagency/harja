import React from 'react';

export default React.createClass({
  render() {
    return (
      <footer id="harja-footer">
        <div className="row align-middle">
          <div className="columns medium-4">
            <img className="livi-header-logo" src="images/livi_logo_white.png" alt="liikennevirasto logo" />
          </div>
          <div className="columns medium-8 small-12">
            <p>Tätä projektia ylläpitää <span><a href="http://www.liikennevirasto.fi/" target="_blank">Liikennevirasto</a></span></p>
          </div>
        </div>
      </footer>
    );
  }
});
