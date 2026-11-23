import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Dependency-free bookstore demo. No orders, payments or personal data. */
public final class App {
    private App() {}
    record Book(String slug, String title, String author, String category, String price, String color, String detail) {}
    static final List<Book> BOOKS = List.of(
        new Book("pride-and-prejudice", "Pride and Prejudice", "Jane Austen", "CLASSIC FICTION", "299", "rose", "A meeting of sharp minds, mistaken impressions and unexpected affection. A classic for readers who enjoy wit and character."),
        new Book("alice-in-wonderland", "Alice's Adventures in Wonderland", "Lewis Carroll", "FANTASY", "249", "blue", "Follow a curious reader down a rabbit hole into a world of wordplay and impossible things."),
        new Book("sherlock-holmes", "The Adventures of Sherlock Holmes", "Arthur Conan Doyle", "MYSTERY", "349", "green", "A collection of mysteries for the observant reader, with Holmes and Watson at the centre of every puzzle.")
    );

    static HttpServer start(int port) throws IOException {
        AtomicLong requests = new AtomicLong();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                reply(exchange, 405, "text/plain", "Method not allowed\n");
            } else if ("/healthz".equals(path)) {
                reply(exchange, 200, "text/plain", "ok\n");
            } else if ("/metrics".equals(path)) {
                reply(exchange, 200, "text/plain; version=0.0.4", "# HELP lab_http_requests_total Requests received by this process.\n# TYPE lab_http_requests_total counter\nlab_http_requests_total " + requests.get() + "\n");
            } else if ("/".equals(path)) {
                reply(exchange, 200, "text/html", page("A good book changes the day", home()));
            } else if ("/books".equals(path)) {
                reply(exchange, 200, "text/html", page("The bookshelf", "<section class='intro'><p class='eyebrow'>THE COLLECTION</p><h1>Find your next<br>good read.</h1><p>Three classics. A little room for wonder.</p></section>" + cards()));
            } else if ("/contact".equals(path)) {
                reply(exchange, 200, "text/html", page("About this demo", "<section class='intro'><p class='eyebrow'>HELLO, READER</p><h1>Made for the<br>love of books.</h1><p>Chapter &amp; Co. is a bookstore demo built for a DevOps learning project.</p><div class='notice'><h2>No real orders. No hidden forms.</h2><p>Prices are sample display values, not offers to sell. We do not accept payments, collect contact details or store customer data. There is no checkout.</p><p>Use the collection to explore book pages. Technical health and metrics endpoints support the deployment lab.</p></div><a class='button' href='/books'>Back to the bookshelf &rarr;</a></section>"));
            } else {
                Book found = BOOKS.stream().filter(b -> path.equals("/books/" + b.slug())).findFirst().orElse(null);
                if (found == null) {
                    reply(exchange, 404, "text/html", page("Page not found", "<section class='intro'><p class='eyebrow'>404</p><h1>Not found.</h1><p>This page is not on our shelf.</p><a class='button' href='/books'>Browse books &rarr;</a></section>"));
                } else {
                    reply(exchange, 200, "text/html", page(found.title(), detail(found)));
                }
            }
        });
        server.start();
        return server;
    }

    static String cover(Book book) {
        return "<div class='cover " + book.color() + "'><span>CHAPTER CLASSICS</span><strong>" + esc(book.title()) + "</strong><small>" + esc(book.author()) + "</small><i aria-hidden='true'>&#10043;</i></div>";
    }
    static String cards() {
        StringBuilder out = new StringBuilder("<section class='grid' aria-label='Book collection'>");
        for (Book b : BOOKS) {
            out.append("<article><a class='book-link' href='/books/").append(b.slug()).append("'>").append(cover(b)).append("<p class='eyebrow'>").append(b.category()).append("</p><h3>").append(esc(b.title())).append("</h3><p class='author'>").append(esc(b.author())).append("</p><div class='price'>&#8377;").append(b.price()).append(" <small>sample price</small><span>View book &nearr;</span></div></a></article>");
        }
        return out.append("</section>").toString();
    }
    static String home() {
        return "<section class='hero'><div><p class='eyebrow'>A SMALL SHOP. A WORLD OF STORIES.</p><h1>Turn a page.<br><em>Find a world.</em></h1><p class='lead'>For slow mornings, long journeys and<br class='desktop'> the chapters in between.</p><a class='button' href='/books'>Explore the bookshelf &rarr;</a><p class='micro'>Thoughtfully selected classics, just for this demo.</p></div><div class='hero-art' aria-label='Illustrated stack of classic books'><div class='sun'></div><div class='stack s1'>WONDER &amp; CURIOSITY</div><div class='stack s2'>A LITTLE MYSTERY</div><div class='standing'>CHAPTER<br>&amp; CO.<span>STORIES<br>TO KEEP</span></div><div class='stack s3'>ONE MORE CHAPTER</div><span class='spark' aria-hidden='true'>&#10043;</span></div></section><section class='section-head'><div><p class='eyebrow'>THE READING LIST</p><h2>A shelf worth exploring.</h2></div><a href='/books'>See all books &rarr;</a></section>" + cards() + "<section class='closing'><p class='eyebrow'>YOUR NEXT CHAPTER STARTS HERE</p><h2>A little less scrolling.<br>A little more story.</h2><a href='/contact'>About this little bookshop &rarr;</a></section>";
    }
    static String detail(Book b) {
        return "<p class='breadcrumb'><a href='/books'>&larr; All books</a></p><section class='detail'><div class='detail-cover'>" + cover(b) + "</div><div><p class='eyebrow'>" + b.category() + "</p><h1>" + esc(b.title()) + "</h1><p class='lead'>by " + esc(b.author()) + "</p><p>" + esc(b.detail()) + "</p><p class='detail-price'>&#8377;" + b.price() + " <small>sample price</small></p><div class='notice'>Display-only catalog. No purchase or checkout is available.</div><a class='button' href='/books'>Keep browsing &rarr;</a></div></section>";
    }
    static String page(String title, String body) {
        return "<!doctype html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>" + esc(title) + " | Chapter &amp; Co.</title><style>" + CSS + "</style></head><body><div class='demo'>DEMO BOOKSTORE &middot; NO REAL ORDERS OR PAYMENTS</div><header><a class='brand' href='/'>Chapter <em>&amp;</em> Co.<small>A PLACE FOR GOOD STORIES</small></a><nav aria-label='Main navigation'><a href='/'>Home</a><a href='/books'>Books</a><a href='/contact'>About &amp; contact</a></nav><a class='nav-cta' href='/books'>Find a book &nearr;</a></header><main>" + body + "</main><footer><a class='brand' href='/'>Chapter &amp; Co.</a><p>A demo storefront by DevOpsVibez.<br>Sample catalog and prices. No customer data collected.</p><p>Hello from DevOpsVibez<br><a href='/contact'>About &amp; contact</a></p></footer></body></html>";
    }
    static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static void reply(HttpExchange exchange, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
        finally { exchange.close(); }
    }
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("Bookstore listening on port " + server.getAddress().getPort());
    }
    static final String CSS = """
        *{box-sizing:border-box}body{margin:0;background:#f9f6ef;color:#282c24;font-family:Arial,sans-serif}a{color:inherit;text-decoration:none}a:focus-visible{outline:3px solid #ab583b;outline-offset:5px}.demo{background:#2d4338;color:#fff;text-align:center;font-size:10px;letter-spacing:2px;padding:10px}header,main,footer{max-width:1200px;margin:auto}header{display:flex;align-items:center;justify-content:space-between;padding:27px 35px;border-bottom:1px solid #dedbd1}.brand{font-family:Georgia,serif;font-size:29px;font-weight:bold}.brand em{color:#a04e38}.brand small{display:block;font:8px Arial,sans-serif;letter-spacing:2.3px;margin-top:7px}nav{display:flex;gap:27px;font-size:12px}.nav-cta{font-size:12px;border-bottom:1px solid;padding:8px 0}main{padding:0 35px}.hero{display:grid;grid-template-columns:1.12fr 1fr;gap:30px;align-items:center;padding:65px 0 55px}.eyebrow{font-size:9px;font-weight:bold;letter-spacing:2px;color:#68715e;margin:0 0 18px}h1,h2,h3{font-family:Georgia,serif;font-weight:normal}h1{font-size:64px;line-height:1.06;letter-spacing:-2px;margin:0 0 22px}h1 em{color:#9f513c;font-weight:normal}.lead{font-size:15px;line-height:1.8;color:#687060;margin:0 0 26px}.button{display:inline-block;background:#2d4338;color:white;padding:16px 23px;font-size:12px;border-radius:2px}.micro{font-size:10px;color:#7a7c70;margin-top:18px}.hero-art{height:345px;position:relative;background:#ece9dd;border-radius:48% 48% 4px 4px;overflow:hidden}.sun{position:absolute;width:180px;height:180px;border-radius:50%;background:#d8b777;top:28px;right:35px}.stack{position:absolute;bottom:30px;height:40px;border-radius:4px;box-shadow:5px 7px 0 #0001;font-family:Georgia,serif;letter-spacing:2px;font-size:11px;display:flex;align-items:center;padding-left:25px}.s1{background:#e9c1ac;width:230px;bottom:115px;left:30px;transform:rotate(-7deg)}.s2{background:#335749;color:#fff;width:250px;bottom:75px;left:25px;transform:rotate(3deg)}.s3{background:#c09956;width:330px;bottom:30px;left:30px}.standing{position:absolute;right:45px;bottom:71px;background:#a14f3c;color:#f9e6c8;width:145px;height:240px;transform:rotate(9deg);padding:25px 18px;font:25px Georgia,serif;box-shadow:9px 6px 0 #703b2c;border:1px solid #b97458}.standing span{display:block;font:10px Arial,sans-serif;letter-spacing:3px;margin-top:45px}.spark{position:absolute;top:30px;left:30px;font-size:65px;color:#345143}.section-head{display:flex;justify-content:space-between;align-items:end;margin:15px 0 25px}.section-head h2{font-size:32px;margin:0}.section-head .eyebrow{margin-bottom:10px}.section-head>a{font-size:11px;border-bottom:1px solid;padding-bottom:6px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:30px;margin-bottom:60px}.book-link{display:block}.cover{height:285px;display:flex;flex-direction:column;position:relative;justify-content:center;padding:33px;border-left:8px solid #0002;box-shadow:5px 5px 0 #0001}.cover>span{font-size:8px;letter-spacing:3px;margin-bottom:30px}.cover strong{font:31px Georgia,serif;line-height:1.15;max-width:220px}.cover small{font-size:11px;margin-top:17px}.cover i{font-size:38px;position:absolute;bottom:21px;right:25px}.rose{background:#ad594b;color:#fff2d9}.blue{background:#b7cccf;color:#263d4c}.green{background:#3f5a4b;color:#f2e4bc}article .eyebrow{margin:23px 0 10px}h3{font-size:22px;margin:0 0 8px}.author{font-size:12px;color:#6c7164;margin:0 0 18px}.price{font-size:15px;border-top:1px solid #dedbd1;padding-top:14px}.price small,.detail-price small{font:10px Arial,sans-serif;color:#757a6b}.price span{float:right;font-size:11px;padding-top:3px}.closing{text-align:center;border-top:1px solid #dedbd1;padding:40px 0 55px}.closing h2{font-size:35px;line-height:1.2;margin:0 0 20px}.closing>a{font-size:12px;border-bottom:1px solid}footer{border-top:1px solid #dedbd1;padding:30px 35px;display:flex;justify-content:space-between;gap:25px}footer .brand{font-size:23px}footer p{font-size:10px;line-height:1.8;color:#6c7164}.intro{max-width:750px;margin:65px 0 45px}.intro>p:not(.eyebrow){line-height:1.8;color:#687060}.notice{border:1px solid #d8d5c9;background:#efeee4;padding:22px;margin:25px 0;font-size:13px;line-height:1.8}.notice h2{margin-top:0}.breadcrumb{font-size:12px;margin:30px 0}.detail{display:grid;grid-template-columns:0.8fr 1.2fr;gap:65px;align-items:center;padding:20px 0 65px}.detail-cover{padding:30px;background:#eeece2}.detail-cover .cover{height:390px}.detail h1{font-size:49px}.detail p{line-height:1.8}.detail-price{font-size:27px}.book-link:hover h3{color:#9f513c}@media(max-width:700px){header{padding:20px;flex-wrap:wrap;gap:20px}.brand{font-size:25px}.nav-cta{display:none}nav{gap:15px;font-size:11px}main{padding:0 20px}.hero{grid-template-columns:1fr;padding-top:38px;gap:25px}h1{font-size:47px}.hero-art{height:320px}.grid{grid-template-columns:1fr;gap:35px}.cover{height:320px}.section-head h2{font-size:27px}.section-head>a{white-space:nowrap;margin-left:12px}.section-head{align-items:center}.detail{grid-template-columns:1fr;gap:30px}.detail h1{font-size:40px}.detail-cover{max-width:400px}footer{padding:25px 20px;flex-direction:column;gap:8px}.intro{margin-top:40px}.demo{font-size:8px;letter-spacing:1px}}
        """;
}
