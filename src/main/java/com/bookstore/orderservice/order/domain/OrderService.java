package com.bookstore.orderservice.order.domain;

import com.bookstore.orderservice.book.Book;
import com.bookstore.orderservice.book.BookClient;
import com.bookstore.orderservice.order.event.OrderAcceptedMessage;
import com.bookstore.orderservice.order.event.OrderDispatchedMessage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final BookClient bookClient;
    private final StreamBridge streamBridge;


    public Flux<Order> getAllOrder() {
        return orderRepository.findAll();
    }

    @Transactional
    public Mono<Order> submitOrder(String isbn, int quantity) {
        return  bookClient.getBookByIsbn(isbn)
                .map(book -> buildAcceptOrder(book, quantity))
                .defaultIfEmpty(buildRejectOrder(isbn, quantity))
                .flatMap(orderRepository::save)
                .doOnNext(this::publishOrderAcceptedEvent);

    }

    private void publishOrderAcceptedEvent(Order order) {
        if (!order.status().equals(OrderStatus.ACCEPTED)) {
            return;
        }
        var orderAcceptedMessage = new OrderAcceptedMessage(order.id());
        log.info("Sending order accepted event with id: {}", order.id());
        var result = streamBridge.send("acceptOrder-out-0", orderAcceptedMessage);
        log.info("Result of sending data for order with id {}: {}", order.id(), result);
    }

    public static Order buildRejectOrder(String isbn, int quantity) {
        return Order.of(isbn, null, null, quantity, OrderStatus.REJECTED);
    }

    public static Order buildAcceptOrder(Book book, int quantity) {
        return Order.of(book.isbn(), book.title() + " - " + book.author(), book.price(), quantity, OrderStatus.ACCEPTED);
    }

    public Flux<Order> consumeOrderDispatchedEvent(Flux<OrderDispatchedMessage> flux) {
        return flux
                .flatMap(message -> orderRepository.findById(message.orderId()))
                .map(this::buildDispatchedOrder)
                .flatMap(orderRepository::save);
    }

    private Order buildDispatchedOrder(Order existingOrder) {
        return new Order(
                existingOrder.id(),
                existingOrder.bookIsbn(),
                existingOrder.bookName(),
                existingOrder.bookPrice(),
                existingOrder.quantity(),
                OrderStatus.DISPATCHED,
                existingOrder.createdDate(),
                existingOrder.lastModifiedDate(),
                existingOrder.version()
        );
    }
}
