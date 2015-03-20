module.exports = function(config) {
    config.set({
	basePath: '',
	files: [
	    'test/js/es5-shim.js', // for PhantomJS
	    'test/js/react-with-addons.inc.js',
	    'target/cljs/test/goog/base.js',
	    'target/cljs/test/test.js',
	    
	    {pattern: 'target/cljs/test/**/*.js', watched: true, included: false, served: true},
	    'test/js/start.js'
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
