#!/bin/bash

PORT=8080
if [ $# -eq 1 ]; then
  PORT=$1
fi
netstat -anp |grep ${PORT} |sed 's/^tcp.\+LISTEN \+//' |sed -r 's/(.+)\/java/kill -KILL \1/' |sh
