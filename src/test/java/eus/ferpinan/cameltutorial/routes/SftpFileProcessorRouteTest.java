package eus.ferpinan.cameltutorial.routes;

import eus.ferpinan.cameltutorial.mapper.UserMapper;
import eus.ferpinan.cameltutorial.model.UserContact;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static eus.ferpinan.cameltutorial.constants.RouteConstants.DIRECT_SEND_TO_API;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SftpFileProcessorRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockitoBean
    private UserMapper userMapper;

    @EndpointInject("mock:api-endpoint")
    private MockEndpoint mockApiEndpoint;

    @EndpointInject("mock:sendEmail")
    private MockEndpoint mockSendEmail;

    @Test
    void shouldProcessSftpFileCorrectly() throws Exception {
        UserContact user = new UserContact("a", "b", "1");
        when(userMapper.csvToUserContact(anyString())).thenReturn(Optional.of(user));

        AdviceWith.adviceWith(camelContext, "sftp-file-processor", route -> {
            route.replaceFromWith("direct:sftp-start");
            route.weaveByToUri(DIRECT_SEND_TO_API).replace().to("mock:api-endpoint");
            route.weaveByToUri("direct:sendEmail").replace().to("mock:sendEmail");
        });

        camelContext.start();

        mockApiEndpoint.expectedMessageCount(2);
        mockApiEndpoint.expectedHeaderReceived("Content-Type", "application/json");
        mockApiEndpoint.expectedBodiesReceived(
                "{\"fullName\":\"a\",\"fullAddress\":\"b\",\"phone\":\"1\"}",
                "{\"fullName\":\"a\",\"fullAddress\":\"b\",\"phone\":\"1\"}"
        );


        mockSendEmail.expectedMessageCount(0);

        String csvContent = "a;b;1\na;b;1";
        producerTemplate.sendBodyAndHeader("direct:sftp-start", csvContent, "CamelFileName", "test.csv");

        mockApiEndpoint.assertIsSatisfied();
        mockSendEmail.assertIsSatisfied();
    }

    @Test
    void shouldSkipInvalidRecords() throws Exception {
        when(userMapper.csvToUserContact(anyString())).thenReturn(Optional.empty());

        AdviceWith.adviceWith(camelContext, "sftp-file-processor", route -> {
            route.replaceFromWith("direct:sftp-start");
            route.weaveByToUri(DIRECT_SEND_TO_API).replace().to("mock:api-endpoint");
            route.weaveByToUri("direct:sendEmail").replace().to("mock:sendEmail");
        });

        camelContext.start();

        mockSendEmail.expectedMessageCount(1);
        mockSendEmail.expectedHeaderReceived("To", "admin@ferpinan.eus");
        mockSendEmail.expectedHeaderReceived("Subject", "User validation error in file: ");
        mockSendEmail.expectedBodiesReceived("Following record could not be parsed: \n\ninvalid;record");

        mockApiEndpoint.expectedMessageCount(0);

        producerTemplate.sendBody("direct:sftp-start", "invalid;record");

        mockSendEmail.assertIsSatisfied();
        mockApiEndpoint.assertIsSatisfied();
    }
}