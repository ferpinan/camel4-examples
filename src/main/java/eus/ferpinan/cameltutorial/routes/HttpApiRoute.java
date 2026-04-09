package eus.ferpinan.cameltutorial.routes;

import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static eus.ferpinan.cameltutorial.constants.RouteConstants.DIRECT_SEND_TO_API;

@Component
@RequiredArgsConstructor
public class HttpApiRoute extends RouteBuilder {

    @Value("${api.endpoint:http://localhost:7070/post}")
    private String apiEndpoint;

    @Override
    public void configure() {

        from(DIRECT_SEND_TO_API)
                .routeId("http-api-sender")
                .errorHandler(
                        deadLetterChannel("direct:handle-api-error")
                                .maximumRedeliveries(3)
                                .redeliveryDelay(2000)
                                .retryAttemptedLogLevel(LoggingLevel.WARN)

                )
                .setProperty("originalPayload", body())
                .to(apiEndpoint)
                .log("Response [${header.CamelHttpResponseCode}]: ${body}");

        from("direct:handle-api-error")
                .routeId("api-error-handler")
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String statusCode = "Server not available";

                    if (cause instanceof HttpOperationFailedException httpException) {
                        statusCode = String.valueOf(httpException.getStatusCode());
                    } else if (cause != null) {
                        statusCode = "Network error: " + cause.getClass().getSimpleName();
                    }
                    exchange.getIn().setHeader("ErrorStatusCode", statusCode);
                })
                .log(LoggingLevel.ERROR, "Message failed permanently: ${body}")
                .setHeader("Content-Type", constant("text/plain; charset=UTF-8"))
                .setHeader("To", constant("admin@ferpinan.eus"))
                .setHeader("Subject", simple("Fallo total envío API: ${routeId}"))
                .setBody(simple("""
                    The message delivery failed after all retry attempts.
                    
                    Response Code: ${header.ErrorStatusCode}
                    Technical Cause: ${exception.message}
                    
                    Original Payload:
                    ${exchangeProperty.payloadOriginal}
                    """))
                .to("direct:sendEmail");
    }


}