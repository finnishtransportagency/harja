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
    let {notice, category} = this.props;
    let title, body;
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
      <div className="row">
        <div className="medium-8 columns">
          <h1>{title}</h1>
          <p>{body}</p>
        </div>
        <div className="medium-4 columns">
          <NoticeList notices={this.props.notices} category={this.props.category}/>
        </div>
      </div>
    );
  }
});
