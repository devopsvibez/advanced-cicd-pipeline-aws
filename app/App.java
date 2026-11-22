import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** A small dependency-free service for the Jenkins lab. */
public final class App {
    private App() {}

    static HttpServer start(int port) throws IOException {
        AtomicLong requests = new AtomicLong();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                reply(exchange, 405, "text/plain", "Method not allowed\n");
            } else if ("/".equals(path)) {
                reply(exchange, 200, "application/json", "{\"service\":\"pipeline-lab\",\"message\":\"Hello from DevOpsVibez\"}\n");
            } else if ("/healthz".equals(path)) {
                reply(exchange, 200, "text/plain", "ok\n");
            } else if ("/metrics".equals(path)) {
                reply(exchange, 200, "text/plain; version=0.0.4", "# HELP lab_http_requests_total Requests received by this process.\n# TYPE lab_http_requests_total counter\nlab_http_requests_total " + requests.get() + "\n");
            } else {
                reply(exchange, 404, "text/plain", "Not found\n");
            }
        });
        server.start();
        return server;
    }

    private static void reply(HttpExchange exchange, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        } finally {
            exchange.close();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("pipeline-lab listening on port " + server.getAddress().getPort());
    }
}
