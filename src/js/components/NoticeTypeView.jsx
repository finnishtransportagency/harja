import React from 'react';
import NoticeList from './NoticeList.jsx';
import {Category} from '../enums.js';

export default React.createClass({
  getDefaultProps() {
    return {
      category: null
    };
  },

  render() {
    let {notice, category, notices} = this.props;
    let title, body;
    let className = 'harja-noticelist harja-' + category + '-noticelist';

    switch (category) {
      case Category.CARE:
        title = 'Teiden hoito';
        body = 'Teitä hoidetaan lorem ipsum......'
        break;
      case Category.MAINTENANCE:
        title = 'Teiden huolto';
        body = 'Teitä huolletaan lorem ipsum......'
        break;
      case Category.FAQ:
        title = 'UKK';
        body = 'Meiltä kysytään usein lorem ipsum....'
        break;
    }

    return (
      <div>
        <div className="row">
          <div className="medium-8 columns">
            <h1>{title}</h1>
            <p>{body}</p>
          </div>
        </div>
        <div className="row">
          <div className="medium-12 small-12 columns">
            <div className={className}>
              <NoticeList notices={notices} category={category}/>
            </div>
          </div>
        </div>
      </div>
    );
  }
});
