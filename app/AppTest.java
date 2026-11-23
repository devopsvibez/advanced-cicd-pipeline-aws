import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class AppTest {
    private AppTest() {}

    public static void main(String[] args) throws Exception {
        var server = App.start(0);
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            check(client, base, "/", "GET", 200, "Hello from DevOpsVibez");
            check(client, base, "/healthz", "GET", 200, "ok\n");
            check(client, base, "/missing", "GET", 404, "Not found");
            check(client, base, "/healthz", "POST", 405, "Method not allowed");
            check(client, base, "/metrics", "GET", 200, "lab_http_requests_total 5");
            check(client, base, "/books", "GET", 200, "Pride and Prejudice");
            check(client, base, "/books/pride-and-prejudice", "GET", 200, "Jane Austen");
            check(client, base, "/books/alice-in-wonderland", "GET", 200, "Lewis Carroll");
            check(client, base, "/books/sherlock-holmes", "GET", 200, "Arthur Conan Doyle");
            check(client, base, "/books/not-a-book", "GET", 404, "Not found");
            check(client, base, "/contact", "GET", 200, "No real orders");
            check(client, base, "/", "GET", 200, "Turn a page.");
            check(client, base, "/", "GET", 200, "NO REAL ORDERS OR PAYMENTS");
            check(client, base, "/books", "POST", 405, "Method not allowed");
            System.out.println("PASS: fourteen HTTP checks (health, metrics and bookstore pages)");
        } finally {
            server.stop(0);
        }
    }

    private static void check(HttpClient client, String base, String path, String method,
                              int status, String expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != status || !response.body().contains(expected)) {
            throw new AssertionError(method + " " + path + ": " + response.statusCode() + " " + response.body());
        }
    }
}
