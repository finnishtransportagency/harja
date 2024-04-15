var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config').watch;

var browserifyTask = require('./browserify');
var stylesTask = require('./styles');
var styleImagesTask = require('./style_images');
var htmlTask = require('./html');

async function buildTask() {
  gulp.src(config.src).pipe(connect.reload());
}

gulp.task('build', gulp.series(browserifyTask,stylesTask, styleImagesTask, htmlTask, buildTask));

module.exports = buildTask;