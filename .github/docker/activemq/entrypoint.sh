#!/bin/ash
set -euo pipefail

sed -i "s/61616/${TCP_PORT}/g" "${ACTIVEMQ_HOME}/conf/activemq.xml";
sed -i "s/8161/${UI_PORT}/g" "${ACTIVEMQ_HOME}/conf/jetty.xml";
"${ACTIVEMQ_HOME}/bin/activemq" console
