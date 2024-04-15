var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config').server;

async function serverTask() {
  connect.server(config.settings);
}

gulp.task('server', serverTask);

module.exports = serverTask;
