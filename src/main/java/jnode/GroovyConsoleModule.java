package jnode;

import groovy.lang.Binding;
import jnode.event.IEvent;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GroovyConsoleModule extends JnodeModule {

    private final Logger logger = Logger.getLogger(getClass());
    private final Executor executor = Executors.newCachedThreadPool();
    private static final int MILLISEC_IN_SEC = 1000;
    private static final int SEC_IN_TEN_MINUTES = 600;

    public static void main(String[] args) throws JnodeModuleException {
        GroovyConsoleModule module = new GroovyConsoleModule("C:\\workspaces\\untitled\\jnode-groovy\\config\\groovyConsole.config");
        module.start();
    }

    public GroovyConsoleModule(String configFile) throws JnodeModuleException {
        super(configFile);

        int listenPort = Integer.parseInt(properties.getProperty("groovyConsole.listenPort", "3113"));

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(listenPort, 0, InetAddress.getByName(null));
        } catch (IOException e) {
            throw new JnodeModuleException("fail open server socket", e);
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
                throw new JnodeModuleException("fail accept socket", e);
            }

            executor.execute(new HandleAccept(s));

            logger.l5(String.format("add new thread %s", executor));
        }
    }


    @Override
    public void start() {

    }

    @Override
    public void handle(IEvent iEvent) {

    }

    private class HandleAccept implements Runnable {
        private final Socket s;

        public HandleAccept(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            try {
                logger.l5(String.format("handle connection %s", s));
                TelnetStream telnetStream = new TelnetStream(s.getInputStream(), s.getOutputStream());

                telnetStream.getOutputStream().writeWONT(34); // linemode
                telnetStream.getOutputStream().writeWILL(1); // echo
                telnetStream.getOutputStream().writeWILL(3); // supress go ahead

                Groovysh sh = new Groovysh(new Binding(), new IO(telnetStream.getInputStream(),
                        telnetStream.getOutputStream(), telnetStream.getOutputStream()));

                sh.run();
                logger.l5(String.format("bye connection %s", s));
            } catch (Throwable e) {
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