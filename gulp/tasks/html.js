var gulp = require('gulp');
var config = require('../config').html;

async function htmlTask() {
  return gulp.src(config.src)
    .pipe(gulp.dest(config.dest));
}

module.exports = htmlTask;