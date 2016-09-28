import React from 'react';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: {
        title: '',
        body: ''
      }
    };
  },

  handleToggle(notice) {
  },

  render() {
    let {notice} = this.props;
    const images = notice.images;
    let imagesEl = (
        images.map(url =>
          <div className="row">
            <img className="harja-notice-image" src={url} alt="kuvitusta" />
          </div>
        )
    );
    return (
      <div className="notice">
        <div className="row">
          <h4>{notice.title}</h4>
        </div>
        <div className="row">
          <img className="harja-icon medium-2 hide-for-small left columns" src="images/clock.png" alt="harja logo" srcSet="images/clock.svg" />
          <div className="harja-date medium-10 small-12 columns">{notice.date}</div>
        </div>
        <div className="row">
          <h5>{notice.short}</h5>
        </div>
        <div className="row">
          <p>{notice.body}</p>
        </div>
      </div>
    );
  }
});
