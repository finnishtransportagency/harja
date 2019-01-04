import React from 'react';

export default React.createClass({
  render() {
    return (
      <footer id="harja-footer">
        <div className="row align-middle">
          <div className="columns show-for-medium medium-4 large-4">
            <img className="livi-footer-logo" src="images/VAYLAwhite.png" alt="väylä logo" />
          </div>
          <div className="columns small-12 medium-8 large-8">
            <p>Tätä projektia ylläpitää <span><a href="http://www.vayla.fi/" target="_blank">Väylä</a></span></p>
          </div>
        </div>
      </footer>
    );
  }
});
