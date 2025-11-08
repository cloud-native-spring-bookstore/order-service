package com.bookstore.orderservice.order.event;

public record OrderAcceptedMessage (
		Long orderId
){}
