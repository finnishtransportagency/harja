#!/bin/sh

cp -R ../../target .
cp -R ../../tietokanta .

docker build -t harjadev .
