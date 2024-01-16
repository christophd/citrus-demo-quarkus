package org.apache.camel.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.quarkus.CitrusSupport;
import org.junit.jupiter.api.Test;

import static org.citrusframework.actions.ReceiveMessageAction.Builder.receive;
import static org.citrusframework.actions.SendMessageAction.Builder.send;

@QuarkusTest
@CitrusSupport
public class FoodMarketDemoTest {

    @CitrusResource
    TestCaseRunner t;

    @Test
    void shouldMatchBookingAndSupply() {
        createBooking();

        createSupply();

        verifyBookingCompletedEvent();

        verifyShippingEvent();
    }

    private void createBooking() {
        t.when(send()
                .endpoint("kafka:bookings")
                .message()
                .body("""
                    {
                        "client": "citrus",
                        "product": {
                            "name": "Kiwi"
                        },
                        "amount": 10,
                        "price": 0.99,
                        "shippingAddress": "FooTown"
                    }
                """)
        );
    }

    private void createSupply() {
        t.when(send()
                .endpoint("kafka:supplies")
                .message()
                .body("""
                    {
                        "client": "citrus",
                        "product": {
                            "name": "Kiwi"
                        },
                        "amount": 10,
                        "price": 0.99
                    }
                """)
        );
    }

    private void verifyBookingCompletedEvent() {
        t.then(receive()
                .endpoint("kafka:completed?timeout=10000&consumerGroup=citrus-booking")
                .message()
                .body("""
                    {
                        "client": "citrus",
                        "product": "Kiwi",
                        "amount": 10,
                        "status": "COMPLETED"
                    }
                """)
        );
    }

    private void verifyShippingEvent() {
        t.then(receive()
                .endpoint("kafka:shipping?timeout=10000&consumerGroup=citrus-shipping")
                .message()
                .body("""
                    {
                        "client": "citrus",
                        "product": "Kiwi",
                        "amount": 10,
                        "address": "FooTown"
                    }
                """)
        );
    }
}
