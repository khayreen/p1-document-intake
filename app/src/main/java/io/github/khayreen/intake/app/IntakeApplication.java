package io.github.khayreen.intake.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Set;

/**
 * Local version of the pipeline. With no arguments it serves the review page on
 * http://localhost:8080. With a command ({@code eval}, {@code aws-eval}, {@code aws-review}) it runs
 * that command and exits with its status code.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IntakeApplication {

    private static final Set<String> COMMANDS = Set.of("eval", "aws-eval", "aws-review");

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(IntakeApplication.class);
        boolean command = args.length > 0 && COMMANDS.contains(args[0]);
        if (command) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext context = app.run(args);
        if (command) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
