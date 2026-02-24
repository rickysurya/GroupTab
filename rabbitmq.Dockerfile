FROM rabbitmq:3-management
RUN rabbitmq-plugins enable --offline rabbitmq_stomp
COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf
