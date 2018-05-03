#!/usr/bin/env bash
time docker run -v /home/ernoku/src/harja:/harja-src --link harjadb:postgres -p 127.0.0.1:3000:3000 -it solita/harja-testit back develop
