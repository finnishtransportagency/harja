module.exports = function(config) {
  config.set({
    basePath: '',
    files: [
      'node_modules/es5-shim/es5-shim.js', // for PhantomJS
      'test/js/react-0.12.0.js',
      'target/cljs/test/goog/base.js',
      'target/cljs/test/test.js',
      {pattern: 'target/cljs/test/**/*.js', watched: true, included: false, served: true},
      'test/js/bootstrap.js',
    ],
    reporters: ['progress'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    browsers: ['PhantomJS', 'Chrome'],
    singleRun: true
  });
};
