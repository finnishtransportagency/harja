import React from 'react';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: {
        date: 'Ei päiväystä',
        title: '',
        short: '',
        body: '',
        images: []
      }
    };
  },

  handleToggle(notice) {
  },

  render() {
    let {notice} = this.props;
    const images = notice.images;
    let imagesEl = (
        images.map( (url, index) =>
          <div className="harja-notice-image column row" key={index}>
            <img src={url} />
          </div>
        )
    );
    return (
      <div className="column row notice">
        <div className="column row">
          <h4>{notice.title}</h4>
        </div>
        <div className="column row">
          <div className="harja-date harja-icon-clock">{notice.displayDate}</div>
        </div>
        <div className="column row">
          <h5>{notice.short}</h5>
        </div>
        <div className="column row">
          <p dangerouslySetInnerHTML={{__html: notice.body}}></p>
        </div>
        {imagesEl}
      </div>
    );
  }
});
