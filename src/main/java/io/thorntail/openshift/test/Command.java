package io.thorntail.openshift.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static org.fusesource.jansi.Ansi.ansi;

public class Command {
    private final String description;
    private final List<String> command;

    private BiConsumer<String, InputStream> outputConsumer = consoleOutput();

    public Command(String... command) {
        this(Arrays.asList(command));
    }

    public Command(List<String> command) {
        this.description = descriptionOfProgram(command.get(0));
        this.command = command;
    }

    public Command outputToFile(Path file) {
        outputConsumer = fileOutput(file);
        return this;
    }

    public Command outputToConsole() {
        outputConsumer = consoleOutput();
        return this;
    }

    public Command outputToString(StringBuilder output) {
        outputConsumer = stringOutput(output);
        return this;
    }

    private static String descriptionOfProgram(String program) {
        if (program.contains(File.separator)) {
            return program.substring(program.lastIndexOf(File.separator) + 1);
        }
        return program;
    }

    public void runAndWait() throws IOException, InterruptedException {
        System.out.println(ansi().a("running ").fgYellow().a(String.join(" ", command)).reset());
        Process process = new ProcessBuilder()
                .redirectErrorStream(true)
                .command(command)
                .directory(new File(".").getAbsoluteFile())
                .start();

        new Thread(() -> {
            outputConsumer.accept(description, process.getInputStream());
        }, "stdout consumer for command " + description).start();

        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException(description + " failed (executed " + command + ", return code " + result + ")");
        }
    }

    private static BiConsumer<String, InputStream> fileOutput(Path file) {
        return (description, is) -> {
            try (OutputStream outStream = Files.newOutputStream(file)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }
            } catch (IOException ignored) {
            }
        };
    }

    private static BiConsumer<String, InputStream> consoleOutput() {
        return (description, is) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(ansi().fgCyan().a(description).reset().a("> ").a(line));
                }
            } catch (IOException ignored) {
            }
        };
    }

    private static BiConsumer<String, InputStream> stringOutput(StringBuilder output) {
        return (description, is) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            } catch (IOException ignored) {
            }
        };
    }
}
