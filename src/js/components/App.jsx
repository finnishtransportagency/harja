import React, {PropTypes} from 'react';
import NoticeList from './NoticeList.jsx';
import {Button, Colors} from 'react-foundation';
import request from 'superagent';

export default React.createClass({
  propTypes: {
  },

  getDefaultProps() {
    return {

    }
  },

  getInitialState() {
    return {
      careNotices: [],
    };
  },

  componentDidMount() {
      this.getNotices('carenotices.json', this.state.careNotices)
  },

  getNotices(file, result) {
    const url = '../data/' + file;
    request.get(url)
      .set('Accept', 'application/json')
      .end((err, response) => {
        if (err) return console.error(err);
        debugger;
        this.setState({
          careNotices: response.body,
        });
      });
  },

  render() {
    let {careNotices} = this.state;
    return (
      <div>
        <h1>Learn Flux</h1>
        <Button color={Colors.SUCCESS}>TESTSAVE</Button>
        <NoticeList notices={careNotices} />
      </div>
    );
  }
});
