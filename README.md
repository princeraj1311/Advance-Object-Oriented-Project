# CineSmart - Movie Ticket Booking & Recommendation System (AOOP project)

Java Swing desktop application, organised as Model / View / utility classes.

## Run it

Requires JDK 17 or newer (`java -version` to check).

```sh
./run.sh              # compile src -> out, copy resources, start the app
./run.sh clean        # also delete out/ and data/ before rebuilding
./run.sh jar          # the above, plus write the self-contained dist/cinesmart.jar
RELEASE=17 ./run.sh   # compile for Java 17 so the classes/jar run on other machines too
```

Once the jar exists it is standalone - classes and the `cinesmart/assets` logo are inside it and
`Main-Class` is set, so it starts from any folder:

```sh
java -jar dist/cinesmart.jar
```

The `data/` folder is always created in the folder you *run from*, not next to the jar, so one
jar can be copied to another machine and used there as is.

Without the script, the same three commands are:

```sh
mkdir -p out
javac -d out $(find src -name '*.java')
cp -R src/cinesmart/assets out/cinesmart/assets   # needed: the logo is a classpath resource
java -cp out cinesmart.CineSmartApp
```

Or from an IDE: open the folder with `src` as the source folder, set
`cinesmart.CineSmartApp` as the main class and run it. The logo is found through the classpath
first and through `src/cinesmart/assets/logo.png` second, so it shows up either way.

Sample accounts are created on the first start. The admin's 12-digit sign-in ID is printed in
the terminal. The seeded demo customer's placeholder email cannot receive a code; register a
customer with an email address you can access.

## Email sign-in and live catalogue

Sign-in uses the saved account details and an email code: customers enter their 10-digit phone
number, name and registered email; admins enter their 12-digit admin ID, name and registered
email. To receive codes, copy `.env.example` to `.env` without overwriting an existing `.env`
(for example, `cp -n .env.example .env` on macOS),
then set `smtpUser` to your Gmail address and `smtpPass` to a Google App Password. Do not use
your regular Gmail password or share the `.env` file. Set `adminEmail` to the administrator's
real address before first launch; an existing seeded admin with the default placeholder address
is updated from this setting.

For the live 2026 movie catalogue, set `tmdbApiKey` in `.env` to a TMDb API key. CineSmart loads
up to 50 high-rated titles (prioritizing ratings of 6.5+ with at least 20 votes, then using a
6.0+ fallback when needed), with TMDb ratings and vote counts, and links to TMDb's review pages;
full review text is not copied into the app. Movie information is provided by TMDb. OpenStreetMap's Overpass
API independently supplies up to four named cinema listings per supported city when the data is
available. These public listings are informational; ticket booking continues to use the app's
local theatre inventory. OpenStreetMap data is © OpenStreetMap contributors.

Live services refresh in the background. The app remains usable with its cached/demo catalogue
when a key or internet connection is unavailable; without `tmdbApiKey`, only the local
cached/demo movies appear. The show calendar creates at least three days of screenings, using
only the 9:00 AM–12:00 PM, 2:30 PM–5:00 PM and 9:00 PM–12:00 AM slots. Older local schedules
using retired time slots are migrated to these supported slots on startup without changing
existing bookings.

## What is inside (Stages 1-6 and Stage 10)

| Package | Files | Responsibility |
| --- | --- | --- |
| `cinesmart` | `CineSmartApp` | entry point: look and feel, theme, splash, main frame |
| `cinesmart.model` | `User`, `Customer`, `Admin`, `Movie`, `Theatre`, `ShowTime`, `SeatMap`, `Booking`, `MenuItem`, `FoodOrder`, `Locality`, `Offer`, `GiftCard`, `CastMember`, `Review` | account objects, the catalogue, the seat rules, the purchase, the food menu, the location/offer/gift-card records and the credits and reviews; every model writes itself as one `\|`-separated line and reads one line back |
| `cinesmart.service` | `AuthService`, `UserService`, `AuthResult`, `CatalogueService`, `BookingService`, `PaymentService`, `CanteenService`, `LocationService`, `OfferService`, `GiftCardService`, `CastService`, `ReviewService` | validation, login/registration rules, every film/cinema/show-time rule, every booking, payment and cancellation rule, the location, coupon and prepaid-card rules, and the credits and review rules - no Swing |
| `cinesmart.util` | `DataStore`, `PasswordHasher`, `IDGenerator`, `Session`, `Theme`, `UiUtil`, `ValidationUtil`, `Logo`, `PosterLoader`, `GeoUtil` | files, SHA-256 + salt hashing, ids, current user, colours/fonts, reusable widgets, rules, branding, poster download + caching, haversine distance |
| `cinesmart.view` | `MainFrame`, `SplashScreen`, `LoginPanel`, `RegisterPanel`, `DashboardPanel`, `MovieCardPanel`, `MovieDetailsDialog`, `SeatSelectionPanel`, `PaymentPanel`, `BookingsPanel`, `CanteenPanel`, `OffersPanel`, `GiftCardsPanel`, `LocationDialog`, `CastPanel`, `ReviewsPanel`, `FormPanel`, `StrengthBar` | the single window, its CardLayout screens, the credits and review blocks, and the region popup |

Data lives in `data/users.txt`, `data/movies.txt`, `data/theatres.txt`, `data/showtimes.txt`,
`data/bookings.txt` (never a plain password) and `data/settings.properties` (remember-me user,
selected city, accent colour). A show line has nine required columns; Stage 4 adds a tenth, optional
one with the seats that were held (`A1;C7;C8`), so a file written by Stage 3 still loads. A movie
line has twelve required columns and an optional thirteenth, `posterUrl`, in exactly the same way.
Stage 10 adds two more optional columns to the theatre line (`latitude`, `longitude`) and two whole
files, `data/canteen.txt` (one line per menu item) and `data/foodorders.txt` (one line per order).
Three more files came with location, offers and gift cards: `data/localities.txt` (one line per
place), `data/offers.txt` (one line per running coupon) and `data/giftcards.txt` (one line per card
sold). An offer line has ten required columns and two optional ones - the list of user names who
already used the code, and the day it was switched off by hand - so a ten-column file still loads.
Two more files came with the credits and reviews: `data/cast.txt` (one line per credited person -
`M001|CAST|1|Leonardo DiCaprio|Actor|Dom Cobb|<photoUrl>`) and `data/reviews.txt` (one line per
review, customer or critic). The movie line grew two more optional columns behind the poster,
`totalReviews` and `reviewTags` (`Good=120;Mixed=18;Bad=5`), so a twelve- or thirteen-column file
still loads unchanged.
A booking line has ten columns: id, customer, show, seats, amount, method, transaction id, state,
booking time and cancellation time. The three catalogue files are created on the first start with a
sample cinema: 12 films (10 out today, 2 announced), 10 cinemas in the 8 cities of the city
selector and 120 show times spread over today and the next three days, so the dashboard has
something to show from the very first run.

## Film posters

The cards and the details dialog show the **real poster** of each film, fetched over the network by
`cinesmart.util.PosterLoader`:

* **Where the address lives** - the optional 13th column of `data/movies.txt`. It belongs to the
  film, not to the screen, so every part of the app shows the same picture for the same film.
* **Which host** - `image.tmdb.org` (The Movie Database image CDN) and `upload.wikimedia.org` for
  the Indian films. Both serve plain poster **files with no API key**, which is why the official
  TMDB search API (which does need a key) is not used. Every URL in the sample data was checked by
  hand: it was downloaded and the picture was looked at.
* **Three-step lookup, cheapest first** - decoded image already in memory → `data/posters/*.jpg` on
  disk → the network. So the **second start works with no internet at all**, which matters when the
  project is demonstrated in a viva room with unreliable Wi-Fi.
* **Never on the UI thread** - downloads run on a small daemon pool and the result is handed back
  with `SwingUtilities.invokeLater`, so the window never freezes. A film with no poster address, a
  dead link, a 404 or no network simply keeps the gradient artwork the card paints itself, so a
  tile is never blank and never shows a broken image.
* **Housekeeping** - a failing URL is remembered and not retried, downloads are written to a
  `.part` file and moved into place so a crash cannot leave a truncated jpg, and `Movie.setPosterUrl`
  only accepts `http`/`https` addresses.

## Stage 2 - the home dashboard

`DashboardPanel` is the card both roles land on after logging in (`MainFrame.CARD_HOME`):

* five live stat cards - films out, films announced, cinemas in the chosen city, shows today,
  seats still free today;
* the filter row: free-text search (title, genre, cast, director), language, genre, duration
  band, sort order (IMDb rating, most booked, title, duration, newest release) and Reset;
  the language and genre lists are read from the catalogue, never hard coded;
* two tabs - **Now Showing** lists only the films this city actually screens, **Coming Soon**
  lists the announced ones;
* the poster grid: `MovieCardPanel` shows the film's real downloaded poster (scaled to cover the
  tile, never stretched) and falls back to the gradient artwork it paints itself - colour taken from
  the film id, so a film always looks the same - while no picture has arrived; and the small
  coloured line under each title says what it means right now - `2 shows · from Rs 200` in green,
  `Opens 13 Oct 2026` in orange or `No show left in Kochi` in red.
* `WrapLayout` is a FlowLayout that tells the truth about its size. Plain FlowLayout always
  asks for one row, so inside a scroll pane the poster grid is sized for a single row and the
  second row of films can never be scrolled to; WrapLayout counts the rows for the width the
  panel really has, which is also what keeps the filter row intact when the window is narrow.

The city box in the blue header is global: changing it re-runs `DashboardPanel.refresh()`, so
the title, the stats and the grid always agree with the city that is displayed. The menu bar is
role aware too - a customer sees the **Booking** menu, an admin sees **Manage**, and neither
appears before login (`MainFrame.updateMenus()`).

## Stage 3 - movies, cinemas and show times

`ShowTime` is the association table: it stores `movieId` and `theatreId`, never the objects,
which is how the two text files stay independent. `CatalogueService` performs the joins the
screens need - `showTimesFor`, `showTimesByTheatre` (one block per cinema in the details
dialog), `bookableShows`, `playsInCity`, `filterForCity` - and owns the comparators, so a sort
order behaves identically on every screen. Setters keep the data honest (a show can never hold
more booked seats than the screen has), a show can only be added when both its film and its
cinema exist, and deleting a film or a cinema cascades - `DataStore` removes the shows that
pointed at it, so `data/showtimes.txt` can never keep an orphan row.

## Stage 4 - seat selection and the smart suggestion

`SeatMap` is the shape of one auditorium plus every seat rule of the stage:

* the grid is derived from the seat count of the show, never hard coded - `columnsFor` gives a 60
  seat screen eight seats per row and a 400 seat screen sixteen - and two aisles are cut into
  every row (`seatsBeforeFirstAisle`, `seatsBeforeSecondAisle`), so the map looks like a real hall
  for every cinema and `rowLetter` stops at Z;
* a Stage 3 line records only HOW MANY seats are gone, so `seatsForCount(showId, total, booked)`
  spreads that count over the hall with a hash of the show id: the same show always shows the same
  grey seats instead of reshuffling on every visit. Stage 4 names the seats it held in the tenth
  column of `data/showtimes.txt`, and `BookingService.seatMapFor` combines both groups - the named
  seats plus the rest of the sold count - so the map can never offer a seat that is already sold;
* `stateOf(seat)` answers free / taken / chosen by you / suggested, and `toggle(seat)` is the only
  way a seat change happens - it enforces the rules (the seat must exist, must be free, at most
  `MAX_SEATS` = 10 seats per booking) and returns the reason when it says no;
* `suggest(count, preference)` is the recommendation engine: it walks every unbroken run of the
  wanted length inside every block of every row and scores each candidate from the middle of the
  hall, lowest score wins - **Near Center** favours the middle rows and columns, **Away From
  Screen** the back rows, **Easy Exit** any run that touches an aisle, **Couple/Adjacent Seats**
  two adjacent middle seats, **No Preference** the best run left. When the hall is too broken up
  for a whole run it falls back to the best single seats, and `explanation()` returns the sentence
  that says why those seats were picked.

`BookingService` is the gate in front of the data: `seatMapFor(show)` builds the map from the
stored seats, `seatDataNote(show)` says whether the grey seats are held names or a sold count,
`problemWith(show, map)` is the single place that decides whether a booking may be saved (show
still in the programme, not started, at least one seat, at most ten, enough seats left, every seat
free) and `holdSeats(show, map)` writes the seats onto the show, saves `data/showtimes.txt` and -
since Stage 5 - opens the booking that waits for payment. If either file cannot be written the seats
are released again, so a half saved booking cannot exist. `Receipt` then carries the film, cinema,
screen, time, seats, amount and the new booking id.

`SeatSelectionPanel` is the screen (`MainFrame.CARD_SEATS`, opened by
`DashboardPanel.pickShow(ShowTime)` -> `MainFrame.openSeatSelection(show)`):

* the header repeats the choice - film, cinema, screen, date and time, seats still free - and the
  note about the seat data of this show;
* the map is one square `JButton` per seat: light blue free, blue for your choice, green for a
  suggestion, grey for taken, with a legend underneath and the screen strip on top. The squares
  are painted by `BasicButtonUI` with an empty border, because a 32 pixel button has no room for
  the padding a look and feel reserves and the seat number would silently disappear;
* the booking card shows the chosen seats, the count, the price per seat and the amount, and the
  suggestion block holds the preference (read from the profile and saved back when it is used),
  the number of seats to fill, **Suggest best seats** and **Use these seats**;
* every line is written from the model after every change, so the counts, the price and the
  button states can never disagree with the map;
* holding seats rebuilds the map from the store, so the held seats turn grey at once and the free
  count is true again;
* the booking card then hands over to Stage 5: the hold asks **Pay now?**, and whichever way that
  is answered the **Hold these seats** button becomes **Proceed to payment &middot; Rs <amount>**,
  so the way out of Stage 4 is on the screen and the customer never has to know the Booking menu
  exists. Once the booking is paid the button disappears again, because there is nothing left to
  pay for;
* an admin account gets the same map read only: the card says so, **Hold these seats** stays
  disabled and the hold action itself refuses with a message, so no admin can take a seat.

The look and feel is installed before the palette in `CineSmartApp`, and `docs/tools/ShotTool`
does the same, so a screenshot shows the same window the customer gets on any operating system.

## Stage 5 - payment, confirmation and My bookings

The way in is `SeatSelectionPanel.goToPayment()`: it finds the unpaid booking of this customer for
the show on screen and hands it to `MainFrame.openPayment(bookingId)`. Both the **Pay now?** question
after a hold and the **Proceed to payment** button call it, and so does `docs/tools/ShotTool`, so
the journey the screenshots capture is the journey the customer walks - if the wiring is ever broken
the capture fails loudly instead of quietly producing a good-looking payment screen.

Holding seats no longer stops at a receipt: `BookingService.holdSeats` now also OPENS a
`Booking`, so the seats, the amount and the customer are written to `data/bookings.txt` in the same
breath and stay blocked until they are paid or cancelled. `Booking.Status` is the state machine of
the whole purchase and the file always remembers it:

| State | Meaning | Written when |
| --- | --- | --- |
| `AWAITING_PAYMENT` | the seats are held, nobody has paid | seats held (Stage 4) |
| `PAID` | the money was taken, the booking IS the ticket | `PaymentService.pay` succeeded |
| `CANCELLED` | Stage 6 gave the seats back and refunded | `BookingService.cancelBooking` succeeded |

`PaymentService` is the Stage 5 service and it follows the same two rules as `BookingService`: the
DataStore is the truth (the booking is read back by id, so a booking paid in another window cannot
be paid twice) and nothing is written unless every check passed.

* `problemsWith(booking, details)` returns every reason at once, so the form can show all of them
  in one dialog instead of one mistake per attempt. It checks that the booking exists, belongs to
  the signed in customer, is neither paid nor cancelled, that the show is still in the programme and
  has not started, that every seat of the booking is still held, and then the details of the chosen
  method only: card = Luhn checksum, 3 digit CVV, `MM/YY` still in the future, name on the card; UPI
  = `name@bank`; net banking = 4 digit PIN (all three rules already exist in `ValidationUtil`);
* `pay(booking, details)` re-checks, mints a transaction id with `IDGenerator.transactionId()`,
  marks the booking paid and writes it. Only after that succeeds does `Movie.registerBooking` count
  the tickets, so the dashboard's best-seller number and the Stage 9 recommendations count paid
  tickets only. Card number and PIN are never stored and the form clears them after a payment;
* `PaymentDetails` is one small value object with a setter per field, built by the form and handed
  to the service - the service never reads a Swing component.

`PaymentPanel` (`MainFrame.CARD_PAYMENT`) shows the booking being bought on the left and the method
on the right. Choosing a method hides the fields of the other two, and the **Pay** button is only
enabled while the booking is actually unpaid - opening the screen with nothing due explains what to
do instead of showing a dead form. `BookingsPanel` (`MainFrame.CARD_BOOKINGS`, **Booking > My
bookings**) rebuilds itself from the service on every visit and turns each booking into a ticket
card: state chip, booking id, film, cinema, screen, time, seats, amount, and the refund line once a
booking is cancelled.

## Stage 6 - cancelling a booking

`BookingService` grew two methods, and they are used twice: the **Cancel booking** button asks
`cancelProblemWith(booking)` to decide whether it is enabled (and prints the reason underneath when
it is not), and `cancelBooking(booking)` refuses the very same cases again on the service path, so a
disabled button and the service can never disagree. A booking may be cancelled when it is your own,
has not been cancelled yet and its show has not started - an unpaid hold can be given back too, so
seats are never lost by a customer who changes their mind.

Cancelling does three things in this order and stops if the first write fails: it releases only the
seats of that booking on the show, takes the tickets back out of `Movie.timesBooked` with
`releaseBooking`, and keeps the booking in `data/bookings.txt` marked `CANCELLED` with the time of
the cancellation - it is never deleted, so **My bookings** can still show the ticket and the
refunded amount.

One Stage 4 rule had to be corrected for this to be honest: `reserveSeats` used to raise the sold
count only when the named seats outnumbered it, so on a show that already had sold seats from the
sample data a cancellation would have released seats that were never held by name. Every seat named
by a hold now also counts as one more sold seat (`Set.add` only counts a seat that was not held
yet), and a release lowers the count by exactly what was freed - so a cancelled booking gives back
its own seats and nobody else's.

## Stage 10 - food and canteen, ordered from the nearest hall

`CanteenPanel` (`MainFrame.CARD_CANTEEN`, **Booking > Food and canteen**) answers three questions in
the order a customer asks them: **which hall**, **what does it sell**, **what do I pay**.

**Which hall.** Every cinema carries its real latitude and longitude in the theatre line, and the
customer picks their area once in the strip at the top. `GeoUtil.distanceKm` then measures the
great-circle (haversine) distance between that area and each hall, and `theatresNear(city, lat, lon)`
sorts the halls of the selected city nearest-first. The distance on screen is that real number
("0 m away", "16 km away"), and the screen says plainly that it is measured from the middle of the
chosen area - it never guesses. A hall with no coordinates keeps a distance of `-1` and sorts to the
end rather than pretending to be near.

* A desktop program cannot ask a phone where it is, so the customer states their area once and the
  app measures from there. Everything works offline: no GPS, no API key, no network.
* The coordinates are the centre of the neighbourhood each cinema is in (looked up on OpenStreetMap),
  so they are accurate to roughly a kilometre - which is all "how far is this hall" needs.
* `hasPosition()` treats a `(0, 0)` pair as "no position", because no cinema on earth is there.

**What it sells.** Each hall has its **own** menu: a `MenuItem` belongs to one `theatreId`, so
switching halls genuinely changes the food and the prices rather than showing the same list twice.
PVR Orion sells a Truffle Popcorn at Rs 450 and a Cold Brew at Rs 220; Q Cinemas in Kochi sells
Unniappam and beef empanada. `menuOf(theatreId)` groups the items into Popcorn, Beverages, Snacks,
Combos and Desserts - a fixed order from `MenuItem.CATEGORIES`, not file order, so two halls read
the same way. Every row shows a green or red veg dot, the name, the price and an **Add** button.

**What it pays.** `CanteenService.Basket` is screen state, not a file: item id -> quantity. The total
is **always** worked out from the menu by `basket.totalOf(service)` at the moment it is shown and
again when the order is saved, so a price edited in another window is reflected at once and a basket
entry whose item has left the menu is skipped rather than silently priced at zero.
`problemsWith(hall, basket, bookingId)` refuses an empty basket, a guest, an admin, and an item that
belongs to a different hall; `placeOrder(...)` checks everything first, mints a `FO...` id with
`IDGenerator.foodOrderId()`, writes the line, and only then reports success. **My food orders**
prints every order the customer placed, read back from `data/foodorders.txt`.

## Cast, crew and reviews

The **About the film** tab of the movie details dialog is the part that mirrors a BookMyShow film
page: the synopsis, then **Cast** and **Crew** as rows of tiles, then **Top reviews** (audience)
and **Critic reviews**. The second tab, **Shows in [city]**, keeps every show time where it was, so
booking is one click away.

### Cast and crew

`data/cast.txt` holds one line per credited person, seven columns:

```
M001|CAST|1|Leonardo DiCaprio|Actor|Dom Cobb|https://upload.wikimedia.org/....jpg
M001|CREW|1|Christopher Nolan|Director||
```

Columns are film id, kind (`CAST` or `CREW`), credit order, name, role, character and photo address.
The character is empty for the crew and for an unnamed part, and the photo may be empty - any
address that does not start with `http` is discarded on load, so a typo can never point the
downloader somewhere unexpected. Both files are seeded by `DataStore` on first start, so the tab is
populated offline; `deleteMovie` removes the film's credits and reviews with it.

`CastService` holds the reading rules, and `CastPanel` only draws what it is handed:

* `castOf(movie)` and `crewOf(movie)` filter by kind, drop anyone with no name (`isNamed()`), and
  sort by credit order, so the tiles always read top-billing first. `firstOf(movie, group, n)` is
  how one screen asks "just the first four that fit".
* `directorOf(movie)` prefers the crew row and falls back to the film's own director field, so a film
  whose credits were never seeded still names the right person - it simply has no photograph.
  `producerOf(movie)` does the same for the producer.
* `otherMoviesOf(member, except)` matches the name against the other films' `cast`/`director`
  strings, so a tile tooltip can say where else the person appears.

The credits live in their own file and **do not** overwrite the older `Movie.cast` comma string or the
director field, so a film edited by an admin keeps both views.

Portraits go through the same `PosterLoader` as film posters: cached in `data/posters`, fetched off
the Event Dispatch Thread, and the tile paints its **initials on a coloured circle** until - or
unless - the picture arrives. A rate-limited or unreachable Wikimedia therefore costs a photograph,
never a layout.

### Reviews

`data/reviews.txt` holds one line per review, eleven columns:

```
RS01|M001|AUDIENCE|Arjun Menon|10|846|0|2026-10-03|#Blockbuster|Watched it twice|Wonderful ...
RS02|M001|CRITIC|The Indian Express|9|412|28|2025-11-12||A light spectacle|Blockchain physicist ...
```

Columns are review id, film id, kind (`AUDIENCE` or `CRITIC`), writer (the publication for a critic),
rating out of ten, likes, dislikes, date, hashtags, headline and body. These are **seeded local
records, not a live review API** - there is no network call in the reviews path at all.

`ReviewService` is where the sorting and the scoring live, so a second screen cannot disagree:

* `audienceReviews(movie)` sorts **by likes**, most-appreciated first, because that is what a reader
  is looking for. `criticReviews(movie)` keeps the **file order**, which is the editorial order.
* `audienceScore` / `criticScore` average the cards and round half-up to one decimal;
  `audienceScoreText` / `criticScoreText` add the count ("8.4/10 from 12 user reviews"), because one
  critic at 9/10 means something very different from six critics at 9/10.
* `summaryText(movie)` builds the "Summary of 26.9K reviews." line, and `topTags(movie)` returns the
  first four `Movie.ReviewTag` chips parsed out of the film's own `reviewTags` column.
  `countTag(movie, tag)` counts one word across the customer reviews.
* `reviewCountText(movie)` is the heading count, and it is deliberately **not**
  `Movie.reviewCountText()`. The stored `totalReviews` is a curated headline number, but a
  `movies.txt` written before this stage has no such column and reads zero - printing "0 reviews"
  directly above seven review cards would be a lie. So a stored total above zero wins, and a stored
  zero falls back to counting the cards actually on screen.
* Anything unshowable - a review with no writer, or with neither headline nor body (`isShowable()`)
  - is skipped rather than drawn as an empty box. Loading is defensive all the way down: a short
  line, a rating above 10 or a date that will not parse is dropped by `DataStore.readCatalogFile`
  with a message on stderr, never thrown at a paint call.

## Screenshots of the finished screens

`docs/screenshots` holds a PNG of every screen that works today, captured by painting the
component tree of the real windows into an image (no screen recording needed):

| File | Screen |
| --- | --- |
| `00-splash.png` | the splash screen shown while the app starts |
| `01-login.png` | login card, remember-me and the Register Now link |
| `02-register.png` | registration form with the live password strength bar |
| `03-home-now-showing.png` | dashboard: stats, filters, Now Showing poster grid |
| `04-home-coming-soon.png` | the Coming Soon tab (announced films, no show times) |
| `05-home-search.png` | the same grid narrowed by the free-text search |
| `06-home-city-kochi.png` | the city selector set to Kochi - fewer cinemas, fewer shows |
| `07-movie-details.png` | film details dialog, opening on the About tab: story and the poster |
| `08-home-admin.png` | the dashboard signed in as admin (Manage menu instead of Booking) |
| `09-seat-selection.png` | the seat map of a show, before a seat is chosen |
| `10-seat-suggestion.png` | the smart suggestion marking four seats green |
| `11-seat-chosen.png` | four seats chosen: blue on the map, seats, count and Rs 800 in the card |
| `12-seat-held-awaiting-payment.png` | the seats held: they turn grey and the card offers **Proceed to payment** |
| `12-payment.png` | Stage 5 payment: the booking on the left, card fields on the right |
| `13-my-bookings.png` | My bookings: one paid ticket and one still awaiting payment |
| `14-booking-cancelled.png` | Stage 6: the cancelled ticket, its refund line and a disabled cancel button |
| `15-canteen-halls.png` | Stage 10: the halls of Bengaluru sorted nearest-first, real distances, an empty basket |
| `16-canteen-menu-basket.png` | Stage 10: PVR Orion's own menu with prices, two items in the basket, total Rs 1120 |
| `17-location-detect.png` | the region popup: search box, red **Detect my location**, popular-city chips, real distances |
| `18-offers.png` | the Offers screen: six running codes with their terms and a Copy button each |
| `19-gift-cards.png` | the gift-card wallet: the code, the balance left and the buy-a-card counter |
| `20-movie-about-1.png` | the About tab from the top: the story block, then the **Cast** row of portrait tiles, each captioned with the part played |
| `20-movie-about-2.png` | the **Crew** row - director, writer, producer, cinematographer - then **Top reviews** with the "Summary of 38.9K reviews." line and the four sentiment chips |
| `20-movie-about-3.png` | the audience cards with their likes, dislikes and relative dates, and the **Critic reviews** heading |
| `20-movie-about-4.png` | the critic cards in full - publication, star score, headline, body, likes and date |

Regenerate them after changing a screen (they are painted, not screen-recorded, so no screen
recording permission is needed):

```sh
javac -cp out -d /tmp/shot docs/tools/ShotTool.java docs/tools/Analyse.java
java  -cp out:/tmp/shot ShotTool     # writes the PNGs into docs/screenshots
java  -cp /tmp/shot     Analyse      # prints size / colour statistics for each PNG
```

The Stage 5 and Stage 6 shots hold and pay for real seats, so run ShotTool from a scratch folder -
it creates its `data/` folder in the working directory, and the project's own `data/` stays clean:

```sh
mkdir -p /tmp/shots && cd /tmp/shots
java -cp /path/to/project/out:/tmp/shot ShotTool
```

## Location, offers and gift cards

Three features modelled on the website, built the same way as everything else: a model, a service
that owns the rules, a screen that only shows what the service says.

### Detect my location

The blue header carries a **Detect my location** button that opens the same popup the website shows -
a search box, a red *Detect my location* line, a row of popular-city chips and the full place list.

A desktop JVM cannot ask a phone's GPS, and pretending otherwise would be dishonest. So
`LocationService` works the position out from what the machine already knows, in this order:

| Order | Source | Example | How sure |
| --- | --- | --- | --- |
| 1 | `-Dcinesmart.location=lat,lon` | `12.9716,77.5946` | exact, and the way screenshots and tests are pinned |
| 2 | a system property naming a city | `user.timezone=Asia/Kolkata` | a good guess, and the screen says so |
| 3 | the locality saved in `settings.properties` | `Marine Drive, Kochi` | the customer's own last choice |
| 4 | nothing | - | detection fails cleanly and the picker opens instead |

Whatever it finds is a row of `data/localities.txt` carrying **real coordinates** (looked up for the
neighbourhood, not invented), so the distance the app shows is a genuine haversine distance from
`GeoUtil` - the same number a phone would produce. A value such as `America/Los_Angeles` deliberately
matches nothing rather than guessing a nearby city, and the result always carries the reason it was
reached, which is printed under the answer.

```sh
java -Dcinesmart.location=9.9650,76.2420 -cp out cinesmart.CineSmartApp   # act as if in Marine Drive
```

### Offers

`data/offers.txt` holds the running coupons. An offer knows what it is worth; `OfferService` decides
whether *this* customer may use it on *this* order. The seeded set covers every rule the model knows -
a flat discount, a percentage with a cap, a minimum order value, a city restriction, a tickets-only
code, a food-only code and one that has already expired.

* **One use per customer**, remembered in the file, so a second attempt with the same account fails.
* **Checked twice** - typing a code only *checks* it; it is recorded at the moment the money is taken.
  Applying a code and then walking away has not used it up.
* **The screen never owns the arithmetic** - `Offer.discountOn` decides, the service checks, and
  `PaymentService.pay` re-reads the booking before writing the reduced amount, so the ticket, the
  refund and My bookings all agree on the same number.
* **Codes stay visible after they stop working**, greyed out with the reason, because a customer who
  saw a code yesterday needs to read why it stopped working.

### Gift cards

`data/giftcards.txt` holds every card sold. The wallet screen shows the code, what is left and the
full movement history; the code can then be paid with on the payment screen.

* The card **must cover the whole amount** after the discount. Partial cover is not offered, because
  it would mean quietly charging the difference to a method the customer did not pick.
* The balance **never goes negative** - `GiftCard.spend` refuses and returns false, and if the booking
  write then fails the card is refunded and the failure reported.
* The codes leave out `0/O` and `1/I`, because a card is read off a printed slip by a human.

### Tests

`docs/tools/Smoke.java` is a self-check for all three services and for the Stage 5 journey with a
discount and a gift card. It runs against real data files in a scratch folder:

```sh
javac -d out $(find src -name '*.java')
javac -cp out -d /tmp/smoke docs/tools/Smoke.java
cd /tmp/smoke && java -cp <project>/out:/tmp/smoke \
     -Dcinesmart.location=9.9650,76.2420 Smoke
```

It prints one line per check and exits non-zero if any fail. The last section deliberately calls the
old two-argument `PaymentService.pay` to prove the Stage 5 journey still works unchanged.

## The logo

`cinesmart/util/Logo.java` loads `src/cinesmart/assets/logo.png` **once** and caches every
scaled size, so the splash screen, the blue header, the login and register cards, the About
dialog and the Dock/taskbar icon all show the same picture:

* `Logo.icon(76)`  - plain artwork with a transparent background (blue backgrounds)
* `Logo.badge(58)` - artwork on a rounded blue badge (white cards and dialogs)
* `Logo.wordmark(30)` - logo + "CineSmart" text for the header
* `Logo.windowIcons()` - 16/24/32/48/64/128/256 px for `setIconImages`
* If the PNG is missing, a Java2D badge with a play triangle is drawn instead, so no screen
  can ever show a broken image.

Change the picture without rebuilding: `java -Dcinesmart.logo=/path/to/any.png -cp out cinesmart.CineSmartApp`.
`assets/logo_original.jpg` keeps the original artwork the transparent PNG was cut from.

## Next stages

Stages 5 and 6 are in: holding seats opens a booking, `PaymentService` turns it into a paid ticket
with a transaction id, **My bookings** lists every ticket of the customer, and Stage 6 gives a
booking back before the show starts - releasing the seats, refunding the amount and keeping the
cancelled record. What is still only a plan: the admin forms behind the **Manage** menu (films,
cinemas, show times, users - `MainFrame.laterStage` marks each one today), the reports and the
recommendation engine that reads the paid bookings.
