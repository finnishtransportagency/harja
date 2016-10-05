import React from 'react';
import Notice from './Notice.jsx';
import NoticeList from './NoticeList.jsx';
import {Category, Events} from '../enums.js';
import NavButton from './NavButton.jsx';

export default React.createClass({
  getDefaultProps() {
    return {
      notice: null,
      category: null,
      notices: []
    };
  },

  onclick: function() {
    pubsub.publish(Events.NAV, this.props.navLink);
  },

  render() {
    let {notice, category, notices} = this.props;
    const title = notice.title;
    const homeLink = {title: 'Harja-projekti',
      data: {action: Events.HOME},
      buttonStyle: 'harja-breadcrumb'};
    let categoryLink = '';
    switch (category) {
      case Category.CARE:
        categoryLink = {title: 'Teiden hoito',
          data: {action: Events.CATEGORY, category: Category.CARE},
          buttonStyle: 'harja-breadcrumb'};
        break;
      case Category.MAINTENANCE:
        categoryLink = {title: 'Teiden ylläpito',
          data: {action: Events.CATEGORY, category: Category.MAINTENANCE},
          buttonStyle: 'harja-breadcrumb'};
        break;
      case Category.FAQ:
        categoryLink = {title: 'Usein Kysytyt Kysymykset',
          data: {action: Events.CATEGORY, category: Category.FAQ},
          buttonStyle: 'harja-breadcrumb'}
        break;
    }
    return (
      <div>
        <div className="harja-breadcrumbs">
          <div className="row column show-for-medium">
            <p><NavButton item={homeLink} /><span> &gt; </span><NavButton item={categoryLink} /><span> &gt; </span><a className="harja-breadcrumb" href="#">{title}</a></p>
          </div>
        </div>
        <div className="row">
          <div className="medium-8 columns">
            <Notice notice={notice}/>
          </div>
          <div className="medium-4 columns">
            <NoticeList notices={notices} category={category}/>
          </div>
        </div>
      </div>
    );
  }
});
