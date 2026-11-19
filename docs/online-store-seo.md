# Online Store: share previews and search engines (v1.53.0)

## What the owner gets

- A link to the shop, to a product or to an information page pasted in WhatsApp, Facebook, Telegram, LinkedIn or X shows a card: picture, name, short description and price.
- Google can show the price, the stock state and the star rating of a product in its results (after it has visited the shop; this takes days or weeks and is never guaranteed).
- `https://your-shop/sitemap.xml` and `https://your-shop/robots.txt` exist. In Google Search Console you can give the sitemap address once.

What makes a good card: a product picture (the product's own picture or the first one of its gallery), a clear web title and a description in Shop settings > Products on the shop. A product without a picture gives a card without one.

## What must be set up on the server (once)

1. In the application settings, `app.frontend-url` must be the public address of the shop, for example `https://store.niwegroup.com` (no trailing slash). Previews, canonical addresses and the sitemap use it.
2. Add this to the nginx site, next to the existing `location /` and `location /api/` (use the same backend address as your existing `/api/` rule in place of `127.0.0.1:8080`):

```nginx
# in the http { } block: the crawlers that read link previews and index pages
map $http_user_agent $shop_crawler {
    default 0;
    ~*(facebookexternalhit|facebot|whatsapp|twitterbot|linkedinbot|slackbot|telegrambot|discordbot|pinterestbot|skypeuripreview|googlebot|bingbot|duckduckbot|yandexbot|applebot|embedly) 1;
}

# in the server { } block
location = /shop {
    if ($shop_crawler) { rewrite ^ /_seo/shop last; }
    try_files /index.html =404;
}
location ~ ^/shop/info/([a-z-]+)$ {
    if ($shop_crawler) { rewrite ^/shop/info/(.*)$ /_seo/info/$1 last; }
    try_files /index.html =404;
}
location = /_seo/shop {
    internal;
    set $seo_path /api/public/store/seo/shop;
    if ($arg_product ~ "^[0-9]+$") { set $seo_path /api/public/store/seo/product/$arg_product; }
    proxy_pass http://127.0.0.1:8080$seo_path;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
}
location ~ ^/_seo/info/(.+)$ {
    internal;
    proxy_pass http://127.0.0.1:8080/api/public/store/seo/info/$1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
}
location = /sitemap.xml { proxy_pass http://127.0.0.1:8080/api/public/store/sitemap.xml; proxy_set_header Host $host; }
location = /robots.txt  { proxy_pass http://127.0.0.1:8080/api/public/store/robots.txt;  proxy_set_header Host $host; }
```

If your existing `location /` already has `try_files $uri $uri/ /index.html;`, keep the same fallback instead of `/index.html =404` in the first two blocks. Reload nginx (`nginx -t && nginx -s reload`).

## Checking it

- `curl -A "facebookexternalhit/1.1" "https://your-shop/shop?product=12"` must return a small HTML page with `og:title`, `og:image`, `product:price:amount`. The same address without that `-A` returns the normal app.
- Facebook: Sharing Debugger (developers.facebook.com/tools/debug) shows the card and lets you refresh it. WhatsApp keeps a preview for a while: after changing a picture, add `&v=2` to the link to see it again.
- Google: Search Console > Sitemaps (add `sitemap.xml`) and the Rich Results Test on a product address.

## For the technical team

- `OnlineSeoService` builds the pages (title, description, canonical, Open Graph, Twitter, `product:*`, JSON-LD `Product` with `Offer` / `AggregateOffer`, `aggregateRating`), the sitemap (max 5000 addresses; a product with options appears once, under the template's id) and robots.txt. `OnlineSeoController` serves them under `/api/public/store/` (cached 10 minutes). Staff and customer data are never in these pages.
- Product addresses stay `/shop?product=ID`. Prices are the tax-inclusive shop prices; the currency is the business currency.
- In the app, `ShopSeoService` sets the title, description, canonical, share tags and the JSON-LD script (`#shop-ld`) from the shop, product and information screens, and clears them in `ngOnDestroy`.
- Pictures are the existing public picture endpoints, asked at width 1200. Language of the server pages: the shop's staff text as written; the few fixed words follow the request language (English by default).
