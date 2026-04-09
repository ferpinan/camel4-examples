package eus.ferpinan.cameltutorial.routes;

import eus.ferpinan.cameltutorial.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static eus.ferpinan.cameltutorial.constants.RouteConstants.DIRECT_SEND_TO_API;

@Component
@RequiredArgsConstructor
public class SftpFileProcessorRoute extends RouteBuilder {

    private final UserMapper userMapper;

    @Value("${sftp.host:localhost}")
    private String sftpHost;

    @Value("${sftp.port:2222}")
    private String sftpPort;

    @Value("${sftp.username:user}")
    private String sftpUsername;

    @Value("${sftp.password:pass}")
    private String sftpPassword;

    @Value("${sftp.directory:upload}")
    private String sftpDirectory;

    @Value("${sftp.poll.delay:10000}")
    private String pollDelay;

    @Override
    public void configure() {

        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Error processing file: ${exception.message}")
                .logStackTrace(true);

        from(buildSftpUri())
                .routeId("sftp-file-processor")
                .log("Processing file: ${header.CamelFileName}")
                .convertBodyTo(String.class, "UTF-8")
                .split(body().tokenize("\n"))
                .streaming()
                .filter(body().isNotEqualTo(""))
                .setProperty("originalLine", body())
                .bean(userMapper, "csvToUserContact")
                .choice()
                    .when(simple("${body.isPresent()}"))
                        .setBody(simple("${body.get()}"))
                        .marshal().json()
                        .removeHeaders("Camel*", "sftp*")
                        .setHeader("Content-Type", constant("application/json"))
                        .to(DIRECT_SEND_TO_API)
                    .otherwise()
                        .log(LoggingLevel.WARN, "Invalid record, skipping")
                        .setHeader("To", constant("admin@ferpinan.eus"))
                        .setHeader("Subject", simple("User validation error in file: ${header.CamelFileName}"))
                        .setBody(simple("Following record could not be parsed: \n\n${exchangeProperty.originalLine}"))
                        .to("direct:sendEmail")
                    .endChoice()
                .end()
                .log("File processed");
    }

    private String buildSftpUri() {
        return String.format(
                "sftp://%s@%s:%s/%s?password=%s&noop=true&initialDelay=500&delay=%s&charset=UTF-8",
                sftpUsername, sftpHost, sftpPort, sftpDirectory, sftpPassword, pollDelay
        );
    }
}