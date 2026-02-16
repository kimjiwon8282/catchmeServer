//package com.example.catchme.config.messagingQueue;
//
//import org.springframework.amqp.core.Binding;
//import org.springframework.amqp.core.BindingBuilder;
//import org.springframework.amqp.core.DirectExchange;
//import org.springframework.amqp.core.Queue;
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.amqp.support.converter.MessageConverter;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RabbitMqConfig {
//
//    // 큐, 익스체인지, 라우팅 키 이름 정의
//    public static final String QUEUE_NAME = "notification.queue";
//    public static final String EXCHANGE_NAME = "notification.exchange";
//    public static final String ROUTING_KEY = "notification.routing.key";
//
//    /**
//     * 1. Queue (우체통) 생성
//     * 두 번째 파라미터 true(durable) = RabbitMQ 서버가 재시작되어도 큐를 디스크에 저장하여 유지함 (데이터 유실 방지!)
//     */
//    @Bean
//    public Queue queue() {
//        return new Queue(QUEUE_NAME, true);
//    }
//
//    /**
//     * 2. Exchange (우체국 분류소) 생성
//     * Direct 방식: 라우팅 키가 정확히 일치하는 큐로만 메시지를 보냄
//     */
//    @Bean
//    public DirectExchange exchange() {
//        return new DirectExchange(EXCHANGE_NAME);
//    }
//
//    /**
//     * 3. Binding (분류소와 우체통 연결)
//     * "이 라우팅 키를 가진 메시지는 저 큐로 보내주세요" 라는 규칙
//     */
//    @Bean
//    public Binding binding(Queue queue, DirectExchange exchange) {
//        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
//    }
//
//    /**
//     * 4. Message Converter (직렬화)
//     * 자바 객체(NotificationEvent)를 JSON으로 안전하게 변환하여 큐에 넣기 위함
//     */
//    @Bean
//    public MessageConverter messageConverter() {
//        return new Jackson2JsonMessageConverter();
//    }
//    @Bean
//    public RabbitTemplate rabbitTemplate(
//            ConnectionFactory connectionFactory,
//            MessageConverter messageConverter) {
//
//        RabbitTemplate rabbitTemplate
//                = new RabbitTemplate(connectionFactory);
//        rabbitTemplate.setMessageConverter(messageConverter);
//        return rabbitTemplate;
//    }
//}