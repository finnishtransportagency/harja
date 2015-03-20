var baseConfig = require('./karma.conf.js');
module.exports = function(config) {
  baseConfig(config);
  config.set({
    reporters: ['progress', 'junit'],
    junitReporter: {
      outputFile: 'karma-test-results.xml',
      suite: ''
    },
    browsers: ['PhantomJS']
  });
};
