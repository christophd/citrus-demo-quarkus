package org.apache.camel.demo;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.demo.model.Booking;
import org.apache.camel.demo.model.Product;
import org.apache.camel.demo.model.Supply;
import org.apache.camel.demo.model.event.BookingCompletedEvent;
import org.apache.camel.demo.model.event.ShippingEvent;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusConfiguration;
import org.citrusframework.annotations.CitrusEndpoint;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.kafka.endpoint.KafkaEndpoint;
import org.citrusframework.mail.message.MailMessage;
import org.citrusframework.mail.server.MailServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.junit.jupiter.api.Test;

import static org.citrusframework.actions.ReceiveMessageAction.Builder.receive;
import static org.citrusframework.actions.SendMessageAction.Builder.send;
import static org.citrusframework.dsl.JsonSupport.marshal;

@QuarkusTest
@CitrusSupport
@CitrusConfiguration(classes = { CitrusEndpointConfig.class })
public class FoodMarketDemoTest {

    @CitrusResource
    TestCaseRunner t;

    @CitrusEndpoint
    KafkaEndpoint bookings;

    @CitrusEndpoint
    KafkaEndpoint supplies;

    @CitrusEndpoint
    KafkaEndpoint completed;

    @CitrusEndpoint
    KafkaEndpoint shipping;

    @CitrusEndpoint
    MailServer mailServer;

    @Test
    void shouldMatchBookingAndSupply() {
        Product product = new Product("Kiwi");
        Booking booking = new Booking("citrus", product, 100, 0.99D,
                TestHelper.createShippingAddress().getFullAddress());

        createBooking(booking);

        Supply supply = new Supply("citrus", product, 100, 0.99D);
        createSupply(supply);

        BookingCompletedEvent bookingCompletedEvent = BookingCompletedEvent.from(booking);
        verifyBookingCompletedEvent(bookingCompletedEvent);

        ShippingEvent shippingEvent = new ShippingEvent(booking.getClient(), product.getName(),
                booking.getAmount(), booking.getShippingAddress());
        verifyShippingEvent(shippingEvent);

        verifyBookingCompletedMail(booking);
    }

    private void verifyBookingCompletedMail(Booking booking) {
        t.then(receive()
                .endpoint(mailServer)
                .message(MailMessage.request()
                        .from("foodmarket@quarkus.io")
                        .to("%s@quarkus.io".formatted(booking.getClient()))
                        .subject("Booking completed!")
                        .body("Hey %s, your booking %s has been completed."
                                .formatted(booking.getClient(), booking.getProduct().getName()), "text/plain")));

        t.then(send()
                .endpoint(mailServer)
                .message(MailMessage.response(250)));
    }

    private void createBooking(Booking booking) {
        t.when(send()
                .endpoint(bookings)
                .message()
                .body(marshal(booking))
        );
    }

    private void createSupply(Supply supply) {
        t.when(send()
                .endpoint(supplies)
                .message()
                .body(marshal(supply))
        );
    }

    private void verifyBookingCompletedEvent(BookingCompletedEvent bookingCompletedEvent) {
        t.then(receive()
                .endpoint(completed)
                .message()
                .body(marshal(bookingCompletedEvent))
        );
    }

    private void verifyShippingEvent(ShippingEvent shippingEvent) {
        t.then(receive()
                .endpoint(shipping)
                .message()
                .body(marshal(shippingEvent))
        );
    }
}
