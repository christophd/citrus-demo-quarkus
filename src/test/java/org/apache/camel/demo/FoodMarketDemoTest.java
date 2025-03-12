package org.apache.camel.demo;

import javax.sql.DataSource;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.demo.behavior.VerifyBookingCompletedMail;
import org.apache.camel.demo.behavior.VerifyBookingStatus;
import org.apache.camel.demo.behavior.WaitForEntityPersisted;
import org.apache.camel.demo.model.Booking;
import org.apache.camel.demo.model.Product;
import org.apache.camel.demo.model.ShippingAddress;
import org.apache.camel.demo.model.Supply;
import org.apache.camel.demo.model.event.BookingCompletedEvent;
import org.apache.camel.demo.model.event.ShippingEvent;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusConfiguration;
import org.citrusframework.annotations.CitrusEndpoint;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.kafka.endpoint.KafkaEndpoint;
import org.citrusframework.mail.server.MailServer;
import org.citrusframework.openapi.OpenApiSpecification;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.selenium.endpoint.SeleniumBrowser;
import org.citrusframework.spi.Resources;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.citrusframework.actions.ReceiveMessageAction.Builder.receive;
import static org.citrusframework.actions.SendMessageAction.Builder.send;
import static org.citrusframework.actions.SleepAction.Builder.delay;
import static org.citrusframework.container.FinallySequence.Builder.doFinally;
import static org.citrusframework.dsl.JsonSupport.json;
import static org.citrusframework.dsl.JsonSupport.marshal;
import static org.citrusframework.http.actions.HttpActionBuilder.http;
import static org.citrusframework.openapi.actions.OpenApiActionBuilder.openapi;
import static org.citrusframework.selenium.actions.SeleniumActionBuilder.selenium;

@QuarkusTest
@CitrusSupport
@CitrusConfiguration(classes = { CitrusEndpointConfig.class })
public class FoodMarketDemoTest {

    private final OpenApiSpecification foodMarketSpec =
            OpenApiSpecification.from(Resources.fromClasspath("openapi.yaml"));

    @CitrusResource
    TestCaseRunner t;

    @CitrusEndpoint
    HttpClient foodMarketApiClient;

    @CitrusEndpoint
    HttpServer shippingDetailsService;

    @CitrusEndpoint
    SeleniumBrowser browser;

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
        Booking booking = new Booking("citrus", product, 250, 0.99D);

        createBooking(booking);

        t.then(t.applyBehavior(new WaitForEntityPersisted(booking, dataSource).withStatus(Booking.Status.APPROVAL_REQUIRED.name())));

        Supply supply = new Supply("citrus", product, 250, 0.99D);
        createSupply(supply);

        t.then(t.applyBehavior(new WaitForEntityPersisted(supply, dataSource)));

        verifyBookingStatus(Booking.Status.APPROVAL_REQUIRED);

        approveBooking();

        BookingCompletedEvent bookingCompletedEvent = BookingCompletedEvent.from(booking);
        verifyBookingCompletedEvent(bookingCompletedEvent);

        ShippingAddress shippingAddress = TestHelper.createShippingAddress();
        ShippingEvent shippingEvent = new ShippingEvent(booking.getClient(), product.getName(),
                booking.getAmount(), shippingAddress.getFullAddress());
        verifyShippingEvent(shippingEvent, shippingAddress);

        verifyBookingCompletedMail(booking);

        verifyBookingStatus(Booking.Status.COMPLETED);

    }

    private void approveBooking() {
        t.given(selenium()
                .browser(browser)
                .start());

        t.given(doFinally().actions(
                selenium()
                        .browser(browser)
                        .stop()));

        t.when(selenium()
                .browser(browser)
                .navigate("http://localhost:8081"));

        t.then(delay().seconds(3));

        t.then(selenium()
                .browser(browser)
                .click()
                .element("id", "${bookingId}"));
    }

    private void verifyBookingStatus(Booking.Status status) {
        t.then(t.applyBehavior(new VerifyBookingStatus(status, dataSource)));
    }

    private void verifyBookingCompletedMail(Booking booking) {
        t.then(t.applyBehavior(new VerifyBookingCompletedMail(booking, mailServer)));
    }

    private void createBooking(Booking booking) {
        t.variable("booking", booking);

        t.when(openapi()
                .specification(foodMarketSpec)
                .client(foodMarketApiClient)
                .send("addBooking")
                .message()
                .body(marshal(booking))
        );

        t.then(openapi()
                .specification(foodMarketSpec)
                .client(foodMarketApiClient)
                .receive("addBooking", HttpStatus.CREATED)
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

    private void verifyShippingEvent(ShippingEvent shippingEvent, ShippingAddress shippingAddress) {
        t.then(http()
                .server(shippingDetailsService)
                .receive()
                .get("/shipping/address/${booking.client}"));

        t.then(http()
                .server(shippingDetailsService)
                .send()
                .response(HttpStatus.OK)
                .message()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(marshal(shippingAddress))
        );

        t.then(receive()
                .endpoint(shipping)
                .message()
                .body(marshal(shippingEvent))
        );
    }
}
