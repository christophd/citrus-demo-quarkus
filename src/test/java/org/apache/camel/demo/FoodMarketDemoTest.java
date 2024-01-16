package org.apache.camel.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.demo.model.Booking;
import org.apache.camel.demo.model.Product;
import org.apache.camel.demo.model.Supply;
import org.apache.camel.demo.model.event.BookingCompletedEvent;
import org.apache.camel.demo.model.event.ShippingEvent;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.kafka.endpoint.KafkaEndpoint;
import org.citrusframework.quarkus.CitrusSupport;
import org.junit.jupiter.api.Test;

import static org.citrusframework.actions.ReceiveMessageAction.Builder.receive;
import static org.citrusframework.actions.SendMessageAction.Builder.send;
import static org.citrusframework.dsl.JsonSupport.marshal;
import static org.citrusframework.kafka.endpoint.builder.KafkaEndpoints.kafka;

@QuarkusTest
@CitrusSupport
public class FoodMarketDemoTest {

    @CitrusResource
    TestCaseRunner t;

    @Inject
    ObjectMapper mapper;

    private final KafkaEndpoint supplies = kafka()
            .asynchronous()
            .topic("supplies")
            .build();

    private final KafkaEndpoint completed = kafka()
            .asynchronous()
            .topic("completed")
            .timeout(10000L)
            .consumerGroup("citrus-completed")
            .build();

    private final KafkaEndpoint shipping = kafka()
            .asynchronous()
            .topic("shipping")
            .timeout(10000L)
            .consumerGroup("citrus-shipping")
            .build();

    @Test
    void shouldMatchBookingAndSupply() {
        Product product = new Product("Kiwi");
        Booking booking = new Booking("citrus", product, 10, 0.99D,
                TestHelper.createShippingAddress().getFullAddress());

        createBooking(booking);

        Supply supply = new Supply("citrus", product, 10, 0.99D);
        createSupply(supply);

        BookingCompletedEvent bookingCompletedEvent = BookingCompletedEvent.from(booking);
        verifyBookingCompletedEvent(bookingCompletedEvent);

        ShippingEvent shippingEvent = new ShippingEvent(booking.getClient(), product.getName(),
                booking.getAmount(), booking.getShippingAddress());
        verifyShippingEvent(shippingEvent);
    }

    private void createBooking(Booking booking) {
        t.when(send()
                .endpoint(kafka()
                        .asynchronous()
                        .topic("bookings")
                        .build())
                .message()
                .body(marshal(booking, mapper))
        );
    }

    private void createSupply(Supply supply) {
        t.when(send()
                .endpoint(supplies)
                .message()
                .body(marshal(supply, mapper))
        );
    }

    private void verifyBookingCompletedEvent(BookingCompletedEvent bookingCompletedEvent) {
        t.then(receive()
                .endpoint(completed)
                .message()
                .body(marshal(bookingCompletedEvent, mapper))
        );
    }

    private void verifyShippingEvent(ShippingEvent shippingEvent) {
        t.then(receive()
                .endpoint(shipping)
                .message()
                .body(marshal(shippingEvent, mapper))
        );
    }
}
