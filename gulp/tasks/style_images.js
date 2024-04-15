var gulp = require('gulp');
var config = require('../config').style_images;

async function styleImagesTask() {
  return gulp.src(config.src)
    .pipe(gulp.dest(config.dest));
};

module.exports = styleImagesTask;