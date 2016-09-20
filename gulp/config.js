var dest = './dist';
var src = './src';
var gutil = require('gulp-util');

module.exports = {
  server: {
    settings: {
      root: dest,
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
      indentedSyntax: false, // Enable .sass syntax?
      imagePath: '/images' // Used by the image-url helper
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
    dest: dest
  },
  images: {
    src: src + '/styles/images/*',
    dest: dest + '/styles/images',
  },
  watch: {
    src: src + '/**/*.*',
    tasks: ['build']
  }
};
