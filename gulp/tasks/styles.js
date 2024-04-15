var gulp = require('gulp');
const sass = require('gulp-sass')(require('sass'));
var connect = require('gulp-connect');
var config = require('../config.js').sass;

async function stylesTask() {
  gulp.src(config.src)
    .pipe(sass(config.settings))
    .pipe(gulp.dest(config.dest))
    .pipe(connect.reload());
};

module.exports = stylesTask;