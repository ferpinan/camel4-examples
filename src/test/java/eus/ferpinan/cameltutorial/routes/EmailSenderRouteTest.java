package eus.ferpinan.cameltutorial.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;


@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
class EmailSenderRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    void shouldSendEmailWithCorrectHeaders() throws Exception {
        AdviceWith.adviceWith(camelContext, "email-sender", route -> route.weaveByToUri("smtp*").replace().to("mock:email-out"));

        camelContext.start();
        MockEndpoint mock = camelContext.getEndpoint("mock:email-out", MockEndpoint.class);

        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("To", "user@test.com");
        mock.expectedHeaderReceived("Subject", "System Notification - Test"); // route prepends string
        mock.expectedBodiesReceived("Expected Body");

        Map<String, Object> headers = new HashMap<>();
        headers.put("To", "user@test.com");
        headers.put("Subject", "Test");

        producerTemplate.sendBodyAndHeaders("direct:sendEmail", "Expected Body", headers);

        mock.assertIsSatisfied();
    }
}