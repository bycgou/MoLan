package com.novel.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 交换机名称
    public static final String NOVEL_EXCHANGE = "novel.exchange";

    // 队列名称
    public static final String QUEUE_CHAPTER_GENERATE = "novel.chapter.generate.queue";
    public static final String QUEUE_CHAPTER_VALIDATE = "novel.chapter.validate.queue";
    public static final String QUEUE_CHAPTER_SUMMARY = "novel.chapter.summary.queue";

    // 路由键
    public static final String ROUTING_KEY_GENERATE = "novel.chapter.generate";
    public static final String ROUTING_KEY_VALIDATE = "novel.chapter.validate";
    public static final String ROUTING_KEY_SUMMARY = "novel.chapter.summary";

    /**
     * 配置消息转换器为JSON格式
     * 
     * 【为什么需要这个？】
     * Spring AMQP 3.x 默认不允许反序列化任意类，会抛出 SecurityException。
     * 使用JSON格式可以避免这个问题，同时提高可读性和兼容性。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 禁用将日期写为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 配置RabbitTemplate使用JSON消息转换器
     * 
     * 【为什么需要这个？】
     * 确保发送消息时也使用JSON格式，而不是Java序列化格式。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public DirectExchange novelExchange() {
        return new DirectExchange(NOVEL_EXCHANGE);
    }

    @Bean
    public Queue chapterGenerateQueue() {
        return new Queue(QUEUE_CHAPTER_GENERATE, true);
    }

    @Bean
    public Queue chapterValidateQueue() {
        return new Queue(QUEUE_CHAPTER_VALIDATE, true);
    }

    @Bean
    public Queue chapterSummaryQueue() {
        return new Queue(QUEUE_CHAPTER_SUMMARY, true);
    }

    @Bean
    public Binding generateBinding() {
        return BindingBuilder.bind(chapterGenerateQueue()).to(novelExchange()).with(ROUTING_KEY_GENERATE);
    }

    @Bean
    public Binding validateBinding() {
        return BindingBuilder.bind(chapterValidateQueue()).to(novelExchange()).with(ROUTING_KEY_VALIDATE);
    }

    @Bean
    public Binding summaryBinding() {
        return BindingBuilder.bind(chapterSummaryQueue()).to(novelExchange()).with(ROUTING_KEY_SUMMARY);
    }
}
