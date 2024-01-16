package org.apache.camel.demo;

import javax.sql.DataSource;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.demo.behavior.VerifyBookingCompletedMail;
import org.apache.camel.demo.behavior.VerifyBookingStatus;
import org.apache.camel.demo.behavior.WaitForEntityPersisted;
import org.apache.camel.demo.model.Booking;
import org.apache.camel.demo.model.Product;
import org.apache.camel.demo.model.Supply;
import org.apache.camel.demo.model.event.BookingCompletedEvent;
import org.apache.camel.demo.model.event.ShippingEvent;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusConfiguration;
import org.citrusframework.annotations.CitrusEndpoint;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.kafka.endpoint.KafkaEndpoint;
import org.citrusframework.mail.server.MailServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.citrusframework.actions.ReceiveMessageAction.Builder.receive;
import static org.citrusframework.actions.SendMessageAction.Builder.send;
import static org.citrusframework.dsl.JsonSupport.json;
import static org.citrusframework.dsl.JsonSupport.marshal;
import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@CitrusConfiguration(classes = { CitrusEndpointConfig.class })
public class FoodMarketDemoTest {

    @CitrusResource
    TestCaseRunner t;

    @CitrusEndpoint
    HttpClient foodMarketApiClient;

    @CitrusEndpoint
    KafkaEndpoint supplies;

    @CitrusEndpoint
    KafkaEndpoint completed;

    @CitrusEndpoint
    KafkaEndpoint shipping;

    @CitrusEndpoint
    MailServer mailServer;

    @Inject
    DataSource dataSource;

    @Test
    void shouldMatchBookingAndSupply() {
        Product product = new Product("Kiwi");
        Booking booking = new Booking("citrus", product, 250, 0.99D,
                TestHelper.createShippingAddress().getFullAddress());

        createBooking(booking);

        t.then(t.applyBehavior(new WaitForEntityPersisted(booking, dataSource).withStatus(Booking.Status.APPROVAL_REQUIRED.name())));

        Supply supply = new Supply("citrus", product, 250, 0.99D);
        createSupply(supply);

        verifyBookingStatus(Booking.Status.APPROVAL_REQUIRED);

        approveBooking();

        BookingCompletedEvent bookingCompletedEvent = BookingCompletedEvent.from(booking);
        verifyBookingCompletedEvent(bookingCompletedEvent);

        ShippingEvent shippingEvent = new ShippingEvent(booking.getClient(), product.getName(),
                booking.getAmount(), booking.getShippingAddress());
        verifyShippingEvent(shippingEvent);

        verifyBookingCompletedMail(booking);

        verifyBookingStatus(Booking.Status.COMPLETED);

    }

    private void approveBooking() {
        t.then(http()
                .client(foodMarketApiClient)
                .send()
                .put("/api/bookings/approval/${bookingId}")
        );

        t.then(http()
                .client(foodMarketApiClient)
                .receive()
                .response(HttpStatus.ACCEPTED)
        );
    }

    private void verifyBookingStatus(Booking.Status status) {
        t.then(t.applyBehavior(new VerifyBookingStatus(status, dataSource)));
    }

    private void verifyBookingCompletedMail(Booking booking) {
        t.then(t.applyBehavior(new VerifyBookingCompletedMail(booking, mailServer)));
    }

    private void createBooking(Booking booking) {
        t.when(http()
                .client(foodMarketApiClient)
                .send()
                .post("/api/bookings")
                .message()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(marshal(booking))
        );

        t.then(http()
                .client(foodMarketApiClient)
                .receive()
                .response(HttpStatus.CREATED)
                .message()
                .extract(json().expression("$.id", "bookingId"))
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
