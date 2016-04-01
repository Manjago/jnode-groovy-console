package jnode;

import groovy.lang.Binding;
import jnode.event.IEvent;
import jnode.jscript.JscriptExecutor;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

import javax.script.Bindings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GroovyConsoleModule extends JnodeModule {

    private final Logger logger = Logger.getLogger(getClass());
    private final Executor executor = Executors.newCachedThreadPool();
    private static final int MILLISEC_IN_SEC = 1000;
    private static final int SEC_IN_TEN_MINUTES = 600;

    public static void main(String[] args) throws JnodeModuleException {
        if (args.length < 1) {
            return;
        }
        GroovyConsoleModule module = new GroovyConsoleModule(args[0]);
        module.start();
    }

    public GroovyConsoleModule(String configFile) throws JnodeModuleException {
        super(configFile);
    }


    @Override
    public void start() {
        int listenPort = Integer.parseInt(properties.getProperty("groovyConsole.listenPort", "3113"));
        boolean debug = properties.getProperty("groovyConsole.debug", "").length() != 0;

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(listenPort, 0, InetAddress.getByName(null));
        } catch (IOException e) {
            logger.l1("fail open server socket", e);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {

                while (!Thread.interrupted()) {
                    try {
                        logger.l5(String.format("Still alive, thread pool %s, next report after 10 minutes", executor));
                        Thread.sleep(SEC_IN_TEN_MINUTES * MILLISEC_IN_SEC);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            }
        });

        while (!Thread.interrupted()) {

            final Socket s;
            try {
                logger.l5("accept...");
                s = serverSocket.accept();
                logger.l5(String.format("got connection %s", s.toString()));
            } catch (IOException e) {
                logger.l1("fail accept socket", e);
                return;
            }

            executor.execute(new HandleAccept(s, debug));

            logger.l5(String.format("add new thread %s", executor));
        }

    }

    @Override
    public void handle(IEvent iEvent) {

    }

    private class HandleAccept implements Runnable {
        private static final int LINE_MODE = 34;
        private static final int ECHO = 1;
        private static final int SUPRESS_GO_AHEAD = 3;
        private final Socket s;
        private boolean debug;

        public HandleAccept(Socket s, boolean debug) {
            this.s = s;
            this.debug = debug;
        }

        @Override
        public void run() {
            try {
                logger.l5(String.format("handle connection %s", s));
                TelnetStream telnetStream = new TelnetStream(s.getInputStream(), s.getOutputStream());

                // linemode
                telnetStream.getOutputStream().writeWONT(LINE_MODE);
                // echo
                telnetStream.getOutputStream().writeWILL(ECHO);
                // supress go ahead
                telnetStream.getOutputStream().writeWILL(SUPRESS_GO_AHEAD);

                final IO io = new IO(telnetStream.getInputStream(),
                        telnetStream.getOutputStream(), telnetStream.getOutputStream());

                final Binding binding = new Binding();
                binding.setProperty("console", new Object() {
                            public void println(Object data) {
                                io.out.println(String.valueOf(data));
                            }

                            public void print(Object data) {
                                io.out.print(String.valueOf(data));
                            }
                        }
                );

                if (!debug) {
                    Bindings b = JscriptExecutor.createBindings();
                    for (Map.Entry<String, Object> item : b.entrySet()) {
                        binding.setProperty(item.getKey(), item.getValue());
                    }
                }
                Groovysh sh = new Groovysh(binding, io);

                sh.run();
                logger.l5(String.format("bye connection %s", s));
            } catch (Exception e) {
                logger.l1("fail", e);
            } finally {
                try {
                    logger.l5(String.format("close %s", s));
                    s.close();
                } catch (IOException e) {
                    logger.l5("fail close socket - ignored");
                }
            }
        }
    }
}
