package com.erp.aierpbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Removed TransactionalOperator related imports

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "erp.direct.exchange";

    // Job Ledger Queue/Key
    public static final String BC_JOB_LEDGER_QUEUE_NAME = "bc.jobledger.ingestion.queue";
    public static final String BC_JOB_LEDGER_ROUTING_KEY = "bc.jobledger.ingestion.key";

    // OCR Invoice Data Queue/Key
    public static final String OCR_INVOICE_DATA_QUEUE_NAME = "ocr.invoice.data.queue";
    public static final String OCR_INVOICE_DATA_ROUTING_KEY = "ocr.invoice.data.key"; // Use a distinct key

    @Bean
    DirectExchange exchange() {
        // Durable direct exchange
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue jobLedgerQueue() {
        // Durable queue
        return new Queue(BC_JOB_LEDGER_QUEUE_NAME, true);
    }

    @Bean
    Binding jobLedgerBinding(Queue jobLedgerQueue, DirectExchange exchange) {
        return BindingBuilder.bind(jobLedgerQueue).to(exchange).with(BC_JOB_LEDGER_ROUTING_KEY);
    }

    // --- OCR Invoice Data Queue ---
    @Bean
    public Queue ocrInvoiceDataQueue() {
        return new Queue(OCR_INVOICE_DATA_QUEUE_NAME, true); // Durable queue
    }

    @Bean
    public Binding ocrInvoiceDataBinding(Queue ocrInvoiceDataQueue, DirectExchange exchange) {
        // Bind to the same direct exchange but with a different routing key
        return BindingBuilder.bind(ocrInvoiceDataQueue).to(exchange).with(OCR_INVOICE_DATA_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Use Jackson for JSON serialization/deserialization of messages
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // Set the message converter for the template
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // Configure the listener container factory to use the JSON message converter
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter); // Set the converter here!
        return factory;
    }

    // Optional: Define Dead Letter Queue (DLQ) configuration for error handling
    // public static final String BC_JOB_LEDGER_DLQ_NAME = "bc.jobledger.ingestion.dlq";
    // public static final String BC_JOB_LEDGER_DLX_NAME = "erp.deadletter.exchange";
    // public static final String BC_JOB_LEDGER_DLQ_ROUTING_KEY = "bc.jobledger.ingestion.dlq.key";

    // @Bean
    // DirectExchange deadLetterExchange() {
    //     return new DirectExchange(BC_JOB_LEDGER_DLX_NAME, true, false);
    // }

    // @Bean
    // Queue deadLetterQueue() {
    //     return new Queue(BC_JOB_LEDGER_DLQ_NAME, true);
    // }

    // @Bean
    // Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
    //     return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(BC_JOB_LEDGER_DLQ_ROUTING_KEY);
    // }

    // // Modify jobLedgerQueue bean to route failed messages to DLX
    // @Bean
    // Queue jobLedgerQueueWithDLQ() {
    //     return QueueBuilder.durable(BC_JOB_LEDGER_QUEUE_NAME)
    //             .withArgument("x-dead-letter-exchange", BC_JOB_LEDGER_DLX_NAME)
    //             .withArgument("x-dead-letter-routing-key", BC_JOB_LEDGER_DLQ_ROUTING_KEY)
    //             .build();
    // }
}
