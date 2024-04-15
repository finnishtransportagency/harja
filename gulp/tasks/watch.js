var gulp = require('gulp');
var config = require('../config').watch;

var browserifyTask = require('./browserify');
var stylesTask = require('./styles');
var styleImagesTask = require('./style_images');
var htmlTask = require('./html');
var buildTask = require('./build');

function watchTask() {
  gulp.watch(config.src, buildTask);
}

gulp.task('watch', gulp.series(browserifyTask, stylesTask, styleImagesTask, htmlTask, buildTask, watchTask));

module.exports = watchTask; 
