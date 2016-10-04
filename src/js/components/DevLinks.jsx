import React from 'react';

export default React.createClass({
  render() {
    return (
      <div className="harja-devlinks row align-middle text-center">
        <div className="harja-more column medium-6">
          <a className="button" href="http://finnishtransportagency.github.io/harja/apidoc/api.html">Katso API-kuvaus</a>
        </div>
        <div className="harja-more column medium-6">
          <a className="button" href="https://github.com/finnishtransportagency/harja">Projekti GitHubissa</a>
        </div>
      </div>
    );
  }
});
