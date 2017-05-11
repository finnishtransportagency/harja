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
        <div className="row column">
          <div className="harja-date harja-icon-clock">{this.props.notice.displayDate}</div>
        </div>
        <div className="row column">
          <a className="harja-notice-link" href="#" onClick={this.onclick}>{this.props.notice.title}</a>
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
        titleText = 'Koulutusvideot';
        buttonText = 'Kaikki koulutusvideot'
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
        const className = "harja-more harja-"+category
        const link = {title: buttonText, data: {action: Events.CATEGORY, category: category}};
        moreEl = (
          <div className={className}>
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
      <div>
        <h5>{titleText}</h5>
        {loadingEl}
        {noticesEl}
        {moreEl}
      </div>
    );
  }
});
