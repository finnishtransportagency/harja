var gulp = require('gulp');

var buildTask = require('./build');
var watchTask = require('./watch');
var serverTask = require('./server');

gulp.task('default', gulp.series(buildTask, watchTask, serverTask));