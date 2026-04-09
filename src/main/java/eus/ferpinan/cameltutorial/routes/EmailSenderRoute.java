package eus.ferpinan.cameltutorial.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class EmailSenderRoute extends RouteBuilder {

    @Value("${email.host}")
    private String host;

    @Value("${email.port}")
    private String port;

    @Value("${email.from}")
    private String from;

    @Override
    public void configure() {

        from("direct:sendEmail")
                .routeId("email-sender")
                .log("Sending email to: ${header.To}")
                .setHeader("From", constant(from))
                .setHeader("Subject", simple("System Notification - ${header.Subject}"))
                // the body comes from previous route
                .toD("smtp://" + host + ":" + port + "?to=${header.To}")
                .log("Email sent successfully to ${header.To}");
    }
}