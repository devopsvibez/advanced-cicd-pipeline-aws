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
            check(client, base, "/books", "GET", 200, "Malgudi Days");
            check(client, base, "/books/malgudi-days", "GET", 200, "R. K. Narayan");
            check(client, base, "/books/maha-prasthanam", "GET", 200, "Sri Sri");
            check(client, base, "/books/the-white-tiger", "GET", 200, "Aravind Adiga");
            check(client, base, "/books/not-a-book", "GET", 404, "Not found");
            check(client, base, "/contact", "GET", 200, "No real orders");
            check(client, base, "/", "GET", 200, "Turn a page.");
            check(client, base, "/", "GET", 200, "NO REAL ORDERS OR PAYMENTS");
            check(client, base, "/books", "POST", 405, "Method not allowed");
            check(client, base, "/books?q=Malgudi", "GET", 200, "Showing <strong>1</strong> books");
            check(client, base, "/books?q=not-a-real-title", "GET", 200, "No books on this shelf.");
            check(client, base, "/books?category=TELUGU+POETRY", "GET", 200, "Showing <strong>2</strong> books");
            check(client, base, "/books?category=ENGLISH+MONEY&sort=price", "GET", 200, "Rich Dad Poor Dad");
            check(client, base, "/books?sort=title", "GET", 200, "The Psychology of Money");
            check(client, base, "/books?q=%3Cscript%3E", "GET", 200, "&lt;script&gt;");
            check(client, base, "/books?q", "GET", 200, "Showing <strong>12</strong> books");
            check(client, base, "/books?unknown=1", "GET", 200, "Showing <strong>12</strong> books");
            for (App.Book book : App.BOOKS) {
                check(client, base, "/books/" + book.slug(), "GET", 200, App.esc(book.author()));
            }
            if (!App.query("q=%ZZ", "q").isEmpty()) throw new AssertionError("Malformed query must be safe");
            if (!App.query("x=1&q=hello+reader", "q").equals("hello reader")) throw new AssertionError("Query decoding");
            if (!App.esc("<&\"'>").equals("&lt;&amp;&quot;&#39;&gt;")) throw new AssertionError("HTML escaping");
            if (!App.cards().contains("data-add-book='psychology-of-money'")) throw new AssertionError("Demo cart card");
            if (!App.cartData().contains("psychology-of-money") || !App.cartData().contains("The Psychology of Money")) throw new AssertionError("Cart data");
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/books")).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (!response.headers().firstValue("Content-Security-Policy").orElse("").contains("script-src 'sha256-")) throw new AssertionError("Cart CSP");
            if (!response.body().contains("id='cart-open'") || !response.body().contains("Add to cart +")) throw new AssertionError("Cart controls");
            System.out.println("PASS: HTTP routes, every book detail, search, filters, sort, escaping, cart markup and security headers");
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
