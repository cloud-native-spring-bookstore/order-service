package com.bookstore.orderservice.order.event;

public record OrderDispatchedMessage (
		Long orderId
){}
