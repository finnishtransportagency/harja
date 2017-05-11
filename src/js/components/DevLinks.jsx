import React from 'react';

export default React.createClass({
  render() {
    return (
      <div className="harja-devlinks show-for-large">
        <div className="row align-middle text-center">
          <div className="harja-more harja-icon-link column medium-3">
            <a className="button" href="apidoc/api.html" target="_blank">Katso API</a>
          </div>
          <div className="harja-more harja-icon-link column medium-3">
            <a className="button" href="https://github.com/finnishtransportagency/harja" target="_blank">Projekti GitHubissa</a>
          </div>
        </div>
      </div>
    );
  }
});
