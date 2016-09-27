var dest = './dist';
var src = './src';
var server_root = './';
var gutil = require('gulp-util');

module.exports = {
  server: {
    settings: {
      root: server_root,
      host: 'localhost',
      port: 8080,
      livereload: {
        port: 35929
      }
    }
  },
  sass: {
    src: src + '/styles/**/*.{sass,scss,css}',
    dest: dest + '/styles',
    settings: {
      indentedSyntax: false // Enable .sass syntax?
    }
  },
  browserify: {
    settings: {
      transform: ['babelify']
    },
    src: src + '/js/main.jsx',
    dest: dest + '/js',
    outputName: 'main.js',
    debug: gutil.env.type === 'dev'
  },
  html: {
    src: src + '/index.html',
    dest: server_root
  },
  watch: {
    src: src + '/**/*.*',
    tasks: ['build']
  }
};
