import React from 'react';
import Notice from './Notice.jsx';
import NavButton from './NavButton.jsx';
import request from 'superagent';
import pubsub from 'pubsub-js';
import {Events, Category} from '../enums.js';

let ListItem = React.createClass({
  onclick: function() {
    pubsub.publish(Events.NAV, {action: Events.NOTICE, id: this.props.notice.id, category: this.props.category});
  },
  render: function() {
    return (
      <div className="harja-noticelist-item">
        <div className="row">
          <img className="harja-icon medium-2 hide-for-small columns" src="images/clock.png" alt="harja logo" srcSet="images/clock.svg" />
          <div className="harja-date medium-10 small-12 columns">{this.props.notice.date}</div>
        </div>
        <div className="row">
          <a className="harja-notice-link columns" href="#" onClick={this.onclick}>{this.props.notice.title}</a>
        </div>
      </div>
    );
  }
});


export default React.createClass({
  getInitialState() {
    return {
    };
  },

  getDefaultProps() {
    return {
      notices: [],
      shorten: 0
    }
  },

  render() {
    let loadingEl, noticesEl, moreEl;
    let titleText, buttonText = '';

    let {notices, category} = this.props;

    let className = 'harja-noticelist harja-' + category + '-noticelist';

    switch (category) {
      case Category.CARE:
        titleText = 'Ajankohtaista teiden hoidossa';
        buttonText = 'Kaikki teiden hoito-tiedotteet'
        break;
      case Category.MAINTENANCE:
        titleText = 'Ajankohtaista teiden ylläpidossa';
        buttonText = 'Kaikki teiden ylläpito-tiedotteet'
        break;
      case Category.FAQ:
        titleText = 'Usein Kysytyt Kysymykset';
        buttonText = 'Kaikki kysymykset'
        break;
    }

    if (!notices.length > 0) {
      loadingEl = (
        <img className="harja-loading" src="images/ajax-loader.gif" alt="ladataan..." />
      );
    }
    else {
      let show = notices;
      if (this.props.shorten > 0) {
        show = notices.slice(0, this.props.shorten);
        const link = {title: buttonText, data: {action: Events.CATEGORY, category: category}};
        moreEl = (
          <div className="harja-more">
            <NavButton item={link} />
          </div>
        );
      }

      noticesEl = (
        <ul>
          {show.map(notice =>
            <ListItem notice={notice} key={notice.id} category={category}/>
          )}
        </ul>
      );
    }

    return (
      <div className={className}>
        <h5>{titleText}</h5>
        {loadingEl}
        {noticesEl}
        {moreEl}
      </div>
    );
  }
});
