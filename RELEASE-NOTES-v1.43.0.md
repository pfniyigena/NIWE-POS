# Release 1.43.0 - Online Store: advertising

The shop can now sell space to other businesses.

- **Online Store > Advertising** (new): *Advertisers* (the businesses that pay), *Ads* (picture + link + places + dates + fee note + paid flag) and a *Report*.
- **Three places** in the shop: top of the home page, above the product list, and the order tracking page. Several ads in a place take turns; every ad is labelled "Advertisement"; links open in a new tab.
- **Counting**: an ad counts as shown when it is really on screen (once per visitor and half an hour), and a click goes through the server, which counts it and sends the visitor on to the saved address. Counts are kept per day and place.
- **Report**: views, clicks and click rate per ad for a period and an optional advertiser, with the fee and whether it is paid; print it (or save as PDF) or download it as CSV.
- Links must be plain web addresses (http or https); the picture is checked by its content, and a big JPEG or PNG is shrunk. Pictures up to 5 MB.
- Migration V78 (three tables: online_advertiser, online_ad, online_ad_stat). French texts. See `docs/online-store-advertising.md`.

Tested on PostgreSQL through the API (validation of links, places, dates and pictures; the public list hides expired, future and switched-off ads; views counted once per visitor, clicks redirect and count; report totals) and in a headless browser (ads in the home page and above the product list with the label and a working picture, view counts sent only when on screen, the staff screen: add an advertiser, add an ad with a picture, missing-picture message, report and CSV). Not yet checked: the order tracking page with an ad, the printed report page, French screens, phone layout. Nothing is invoiced from the fee: it is a note for the shop. The manuals are not updated yet.
