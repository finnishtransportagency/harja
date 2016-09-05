import Dispatcher from '../Dispatcher';
import Constants from '../Constants';
import BaseStore from './BaseStore';
import assign from 'object-assign';
import request from 'superagent';

// data storage
let _data = [];

// add private functions to modify data
function addItem(title, body) {
  _data = _data.concat({title, body});
}

getNotices: function() {
  request.get('../../data/carenotices.json')
    .set('Accept', 'application/json')
    .end(function(err, response) {
      if (err) return console.error(err);

      TodoServerActions.receiveRandom(response.body);
    });
}

// Facebook style store creation.
const CareNoticeStore = assign({}, BaseStore, {
  // public methods used by Controller-View to operate on data
  getAll() {
    return {
      notices: _data
    };
  },

  // register store with dispatcher, allowing actions to flow through
  dispatcherIndex: Dispatcher.register(function handleAction(payload) {
    const action = payload.action;

    switch (action.type) {
    case Constants.ActionTypes.CARENOTICE_ADDED:
      const data = action.data;
      // NOTE: if this action needs to wait on another store:
      // Dispatcher.waitFor([OtherStore.dispatchToken]);
      // For details, see: http://facebook.github.io/react/blog/2014/07/30/flux-actions-and-the-dispatcher.html#why-we-need-a-dispatcher
      if (data) {
        addItem(data.title, data.body);
        CareNoticeStore.emitChange();
      }
      break;

    // add more cases for other actionTypes...

    // no default
    }
  })
});

export default TodoStore;
