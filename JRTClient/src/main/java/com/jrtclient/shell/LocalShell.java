package com.jrtclient.shell;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import netscape.javascript.JSObject;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class LocalShell implements Shell {
    private PtyProcess process;
    private final String[] commands;

    private final ObjectProperty<Reader> inputReaderProperty;
    private final ObjectProperty<Reader> errorReaderProperty;
    private final ObjectProperty<Writer> outputWriterProperty;

    private final JSObject terminalIO;

    public LocalShell(JSObject io, String[] cmd) {
        this.commands = cmd;
        this.inputReaderProperty = new SimpleObjectProperty<>();
        this.errorReaderProperty = new SimpleObjectProperty<>();
        this.outputWriterProperty = new SimpleObjectProperty<>();
        this.terminalIO = io;



        inputReaderProperty.addListener((observable, oldValue, newValue) -> {
            Thread thread = new Thread(() -> {
                printStream(newValue);
            });
            thread.start();
        });

        errorReaderProperty.addListener((observable, oldValue, newValue) -> {
            Thread thread = new Thread(() -> printStream(newValue));
            thread.start();
        });
    }

    private void printStream(Reader bufferedReader) {
        try {
            int read;
            final char[] buffer = new char[1024];

            while((read = bufferedReader.read(buffer, 0, buffer.length)) != -1) {
                final StringBuilder builder = new StringBuilder(read);
                builder.append(buffer, 0, read);
                Platform.runLater(() -> terminalIO.call("print", builder.toString()));
            }

        } catch(final Exception e) {
            e.printStackTrace();
        }
    }

    public void command(String text) {
        try {
            getOutputWriterProperty().write(text);
            getOutputWriterProperty().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onTerminalReady() {
        Platform.runLater(() -> {
            try {
                initProcess();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void initProcess() throws IOException {
        Map<String, String> envs = new HashMap<>(System.getenv());
        envs.remove("TERM_PROGRAM"); // for OS X
        envs.put("TERM", "xterm");
        this.process = PtyProcess.exec(commands, envs, System.getProperty("user.home"));
        setInputReaderProperty(new BufferedReader(new InputStreamReader(process.getInputStream())));
        setErrorReaderProperty(new BufferedReader(new InputStreamReader(process.getErrorStream())));
        setOutputWriterProperty(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())));
    }

    @Override
    public void resizeShell(int columns, int rows) {
        process.setWinSize(new WinSize(columns, rows));
    }

    public void disconnect() {
        try {
            getOutputWriterProperty().close();
            getInputReaderProperty().close();
            getErrorReaderProperty().close();
        } catch (IOException ignored) {

        }
        process.destroy();
    }

    public Reader getInputReaderProperty() {
        return inputReaderProperty.get();
    }

    public ObjectProperty<Reader> inputReaderPropertyProperty() {
        return inputReaderProperty;
    }

    public Reader getErrorReaderProperty() {
        return errorReaderProperty.get();
    }

    public ObjectProperty<Reader> errorReaderPropertyProperty() {
        return errorReaderProperty;
    }

    public Writer getOutputWriterProperty() {
        return outputWriterProperty.get();
    }

    public void setInputReaderProperty(Reader inputReaderProperty) {
        this.inputReaderProperty.set(inputReaderProperty);
    }

    public void setErrorReaderProperty(Reader errorReaderProperty) {
        this.errorReaderProperty.set(errorReaderProperty);
    }

    public void setOutputWriterProperty(Writer outputWriterProperty) {
        this.outputWriterProperty.set(outputWriterProperty);
    }
}
