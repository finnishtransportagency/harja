var gulp = require('gulp');
var config = require('../config').style_images;

gulp.task('style_images', function() {
  return gulp.src(config.src)
    .pipe(gulp.dest(config.dest));
});
