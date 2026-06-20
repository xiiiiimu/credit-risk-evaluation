#!/bin/sh
set -e
NAMESRV_ADDR="${NAMESRV_ADDR:-credit-rmqnamesrv:9876}"
TOPIC="${TOPIC:-credit-approval-task-topic}"
CLUSTER="${CLUSTER:-DefaultCluster}"
ROCKETMQ_BIN="/home/rocketmq/rocketmq-4.9.7/bin"

echo "waiting for RocketMQ NameServer: ${NAMESRV_ADDR}"
sleep 25

echo "creating topic: ${TOPIC}"
sh "${ROCKETMQ_BIN}/mqadmin" updateTopic \
  -n "${NAMESRV_ADDR}" \
  -t "${TOPIC}" \
  -c "${CLUSTER}" \
  -r 8 -w 8 || true

echo "topic ${TOPIC} init done (autoCreateTopicEnable=true 也会在首次发送时自动创建)"
