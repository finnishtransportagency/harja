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
    const className = 'harja-noticelist harja-' + category + '-noticelist';

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
        categoryLink = {title: 'Koulutusvideot',
          data: {action: Events.CATEGORY, category: Category.FAQ},
          buttonStyle: 'harja-breadcrumb'}
        break;
      case Category.WATERWAYS:
        categoryLink = {title: 'Vesiväylät',
          data: {action: Events.CATEGORY, category: Category.WATERWAYS},
          buttonStyle: 'harja-breadcrumb'}
        break;
    }

    return (
      <div>
        <div className="harja-breadcrumbs show-for-medium">
          <div className="row column">
            <p><NavButton item={homeLink} /><span> &gt; </span><NavButton item={categoryLink} /><span> &gt; </span><a className="harja-breadcrumb" href="#">{title}</a></p>
          </div>
      </div>
        <div className="harja-singlenotice-content row">
          <div className="small-12 medium-8 large-8 columns">
            <Notice notice={notice}/>
          </div>
          <div className="small-12 medium-4 large-4 columns">
            <div className={className}>
              <NoticeList notices={notices} category={category}/>
            </div>
          </div>
        </div>
      </div>
    );
  }
});
