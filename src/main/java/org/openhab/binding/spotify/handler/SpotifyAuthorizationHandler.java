package org.openhab.binding.spotify.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.openhab.binding.spotify.internal.AuthorizationCodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class SpotifyAuthorizationHandler {

    private final Logger logger = LoggerFactory.getLogger(SpotifyAuthorizationHandler.class);

    private HttpServer server;
    private int port;
    private String state = null;
    private String resource = null;
    private AuthorizationCodeListener listener;

    public SpotifyAuthorizationHandler(int port) {
        this.port = port;
        try {
            this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
            logger.error("Creating server on the listining port '{}'", this.port);
        } catch (IOException e) {
            logger.error("Error creating the authorization http server");
        }
    }

    public void setResourceCallback(String resource, AuthorizationCodeListener listener) {
        this.resource = resource;
        this.listener = listener;
        this.server.createContext(String.format("/%s", this.resource), new ResponseHandler(this.state));
        this.server.setExecutor(null); // creates a default executor
    }

    public void setState(String state) {
        this.state = state;
    }

    class ResponseHandler implements HttpHandler {
        private String state;

        public ResponseHandler(String state) {
            if (state != null) {
                this.state = state;
            } else {
                this.state = null;
            }
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Registration completed";
            String requestURI = t.getRequestURI().toString();
            String answer = requestURI.split("\\?")[1];
            String[] answerSplit = answer.split("\\&");
            String code = answerSplit[0].replaceAll("code\\=", "");
            String state = answerSplit[1].replaceAll("state\\=", "");

            if (this.state != null && !state.equals(this.state)) {
                logger.error("The 'state' received is incorrect, fake response");
                return;
            }
            listener.setAuthorizationCode(code);

            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    public void start() {
        logger.debug("Starting authorization handler");
        this.server.start();
    }

    public void stop() {
        logger.debug("Stopping authorization handler");
        this.server.stop(1);
    }

}
