package eus.ferpinan.cameltutorial.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.atomic.AtomicInteger;

import static eus.ferpinan.cameltutorial.constants.RouteConstants.DIRECT_SEND_TO_API;
import static org.junit.jupiter.api.Assertions.assertEquals;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HttpApiRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @EndpointInject("mock:deadLetter")
    private MockEndpoint mockDeadLetter;

    @EndpointInject("mock:sendEmail")
    private MockEndpoint mockSendEmail;

    @Test
    void shouldSendJsonToApiSuccessfully() throws Exception {
        AdviceWith.adviceWith(camelContext, "http-api-sender", route -> route.weaveByToUri("http://*").replace().to("mock:result"));
        camelContext.start();

        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived("Content-Type", "application/json");
        mockResult.expectedBodiesReceived("{\"name\":\"Test User\",\"email\":\"test@example.com\"}");

        String jsonBody = "{\"name\":\"Test User\",\"email\":\"test@example.com\"}";
        producerTemplate.sendBodyAndHeader(
                DIRECT_SEND_TO_API,
                jsonBody,
                "Content-Type",
                "application/json"
        );

        mockResult.assertIsSatisfied();
    }

    @Test
    void shouldRetryOnFailureAndEventuallySucceed() throws Exception {
        final AtomicInteger attemptCounter = new AtomicInteger(0);

        AdviceWith.adviceWith(camelContext, "http-api-sender", route -> {
            route.weaveByToUri("http://*").replace()
                    .process(exchange -> {
                        int currentAttempt = attemptCounter.incrementAndGet();

                        // Falla las primeras 2 veces, luego éxito
                        if (currentAttempt <= 2) {
                            throw new RuntimeException("Simulated HTTP error - attempt " + currentAttempt);
                        }

                        // Tercer intento: éxito
                        exchange.getMessage().setHeader("CamelHttpResponseCode", 200);
                        exchange.getMessage().setBody("Success");
                    })
                    .to("mock:result");
        });
        camelContext.start();

        mockResult.expectedMessageCount(1);
        mockResult.expectedBodiesReceived("Success");

        String jsonBody = "{\"test\":\"data\"}";
        producerTemplate.sendBody(DIRECT_SEND_TO_API, jsonBody);

        mockResult.assertIsSatisfied();

        assertEquals(3, attemptCounter.get(), "Should have retried 2 times before success");
    }

    @Test
    void shouldSendToDeadLetterAfterMaxRetries() throws Exception {
        AdviceWith.adviceWith(camelContext, "http-api-sender", route -> {
            route.weaveByToUri("http://*").replace()
                    .throwException(new RuntimeException("Permanent failure"));

            route.interceptSendToEndpoint("direct:handle-api-error")
                    .skipSendToOriginalEndpoint() // Evita que salga por consola si quieres
                    .to("mock:deadLetter");
        });

        camelContext.start();

        mockDeadLetter.expectedMessageCount(1);

        producerTemplate.sendBody(DIRECT_SEND_TO_API, "{\"test\":\"data\"}");

        mockDeadLetter.assertIsSatisfied();
    }

    @Test
    void shouldSetCorrectResponseCodeHeader() throws Exception {
        AdviceWith.adviceWith(camelContext, "http-api-sender", route -> {
            route.weaveByToUri("http://*").replace()
                    .process(exchange -> {
                        exchange.getMessage().setHeader("CamelHttpResponseCode", 201);
                        exchange.getMessage().setBody("{\"id\":\"12345\"}");
                    })
                    .to("mock:result");
        });
        camelContext.start();

        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived("CamelHttpResponseCode", 201);

        producerTemplate.sendBody(DIRECT_SEND_TO_API, "{\"data\":\"test\"}");

        mockResult.assertIsSatisfied();

        String responseBody = mockResult.getExchanges().getFirst().getIn().getBody(String.class);
        assertEquals("{\"id\":\"12345\"}", responseBody);
    }

    @Test
    void shouldProcessErrorAndSendFormattedEmail() throws Exception {
        AdviceWith.adviceWith(camelContext, "api-error-handler", route -> {
            route.weaveByToUri("direct:sendEmail").replace().to("mock:sendEmail");
        });

        camelContext.start();

        mockSendEmail.expectedMessageCount(1);
        mockSendEmail.expectedHeaderReceived("To", "admin@ferpinan.eus");
        mockSendEmail.expectedHeaderReceived("Content-Type", "text/plain; charset=UTF-8");
        mockSendEmail.expectedHeaderReceived("Subject", "Fallo total envío API: api-error-handler");
        mockSendEmail.expectedBodiesReceived("""
                The message delivery failed after all retry attempts.
                
                Response Code: 500
                Technical Cause: HTTP operation failed invoking http://api with statusCode: 500
                
                Original Payload:
                {"id":99}""");

        String originalJson = "{\"id\":99}";

        producerTemplate.send("direct:handle-api-error", exchange -> {
            exchange.getIn().setBody(originalJson);
            exchange.setProperty("payloadOriginal", originalJson);
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
                    new HttpOperationFailedException("http://api", 500, "Internal Error", null, null, null));
        });

        mockSendEmail.assertIsSatisfied();
    }

    @Test
    void shouldHandleNetworkErrorInErrorHandler() throws Exception {
        AdviceWith.adviceWith(camelContext, "api-error-handler", route -> {
            route.weaveByToUri("direct:sendEmail").replace().to("mock:sendEmail");
        });
        camelContext.start();

        mockSendEmail.expectedMessageCount(1);
        mockSendEmail.expectedHeaderReceived("To", "admin@ferpinan.eus");
        mockSendEmail.expectedHeaderReceived("Content-Type", "text/plain; charset=UTF-8");
        mockSendEmail.expectedHeaderReceived("Subject", "Fallo total envío API: api-error-handler");
        mockSendEmail.expectedBodiesReceived("""
                The message delivery failed after all retry attempts.
                
                Response Code: Network error: ConnectException
                Technical Cause: Connection refused
                
                Original Payload:
                {}""");

        producerTemplate.send("direct:handle-api-error", exchange -> {
            exchange.setProperty("payloadOriginal", "{}");
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new java.net.ConnectException("Connection refused"));
        });

        mockSendEmail.assertIsSatisfied();
    }
}