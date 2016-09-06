import React from 'react';
import Notice from './Notice.jsx';
import request from 'superagent';

export default React.createClass({
  getInitialState() {
    return {
      notices: [],
      loading: true
    };
  },

  componentDidMount() {
      const url = '../data/' + this.props.url;
      request.get(url)
        .set('Accept', 'application/json')
        .end((err, response) => {
          if (err) return console.error(err);
          this.setState({
            notices: response.body,
            loading: false
          });
        });
  },

  render() {
    var loadingEl;
    var noticesEl;

    if (this.state.loading) {
      loadingEl = (<p>Loading...</p>);
    }
    else {
      let {notices} = this.state;
      noticesEl = (
        <ul>
          {notices.map(notice =>
            <Notice notice={notice} key={notice.title} />
          )}
        </ul>
      );
    }

    return (
      <div>
        {loadingEl}
        {noticesEl}
      </div>
    );
  }
});
