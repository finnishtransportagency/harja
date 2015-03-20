module.exports = function(config) {
  config.set({
    files: [
      'node_modules/es5-shim/es5-shim.js', // for PhantomJS and React with IE8
      'node_modules/es5-shim/es5-sham.js', // React with IE8
      'node_modules/console-polyfill/index.js', // React with IE8
      'test/js/react-0.12.0.js',
      'target/cljs/test/goog/base.js',
      'target/cljs/test/test.js',
      {pattern: 'target/cljs/test/**/*.js', watched: true, included: false, served: true},
      'test/js/bootstrap.js',
    ],
    browsers: ['IE8 - WinXP']
  });
};
