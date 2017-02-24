#!/bin/sh

echo "Tuhotaan harjadb docker kontti"
docker stop harjadb 1> /dev/null
docker rm harjadb 1> /dev/null
