# Class Diagrams – CineMax Movie Ticket Booking

These Mermaid diagrams show the domain classes, the inheritance and strategy hierarchies, and how the modules relate. Method names and parameters match the source code. Only the main attributes are shown, and DTOs and controllers are left out for readability.

Render with any Mermaid viewer (GitHub, the VS Code Mermaid extension, or https://mermaid.live).

---

## 1. User & authentication (Member 1)

```mermaid
classDiagram
    direction TB

    class User {
        <<abstract>>
        -Long id
        -String fullName
        -String email
        -String phone
        -String passwordHash
        -LocalDateTime createdAt
        +getRole()* UserRole
        +getFullName() String
        +getEmail() String
        +getPasswordHash() String
        +setPasswordHash(String hash)
    }

    class Customer {
        -int loyaltyPoints
        +getRole() UserRole
        +getLoyaltyPoints() int
        +addLoyaltyPoints(int points)
    }

    class Admin {
        -String permissionLevel
        +getRole() UserRole
        +getPermissionLevel() String
        +setPermissionLevel(String level)
    }

    class UserRole {
        <<enumeration>>
        CUSTOMER
        ADMIN
    }

    class AuthStrategy {
        <<interface>>
        +supportedRole() UserRole
        +authenticate(String email, String rawPassword) User
    }

    class AbstractAuthStrategy {
        <<abstract>>
        #users UserRepository
        #encoder PasswordEncoder
        +authenticate(String email, String rawPassword) User
        #findByEmail(String email)* User
    }

    class CustomerAuthStrategy {
        +supportedRole() UserRole
    }

    class AdminAuthStrategy {
        +supportedRole() UserRole
    }

    class AuthService {
        +login(AuthStrategy strategy, String email, String password) User
        +loginCustomer(String email, String password) User
        +loginAdmin(String email, String password) User
    }

    class CustomerService {
        +register(RegisterCustomerRequest req) UserResponse
        +getById(Long id) UserResponse
        +updateProfile(Long id, CustomerUpdateRequest req) UserResponse
        +delete(Long id)
    }

    class AdminService {
        +listAdmins() List~UserResponse~
        +createAdmin(CreateAdminRequest req) UserResponse
        +updatePermission(Long actorId, Long id, UpdateAdminRequest req) UserResponse
        +deleteAdmin(Long actorId, Long id)
    }

    User <|-- Customer
    User <|-- Admin
    User ..> UserRole
    AuthStrategy <|.. AbstractAuthStrategy
    AbstractAuthStrategy <|-- CustomerAuthStrategy
    AbstractAuthStrategy <|-- AdminAuthStrategy
    AuthService --> AuthStrategy : uses
    CustomerService ..> Customer : manages
    AdminService ..> Admin : manages
```

---

## 2. Movie catalogue (Member 2)

```mermaid
classDiagram
    direction LR

    class Movie {
        <<abstract>>
        -Long id
        -String title
        -String genre
        -String language
        -Integer durationMinutes
        -String rating
        -String description
        -String posterUrl
        -LocalDate releaseDate
        +getFormat()* String
        +getSurcharge()* BigDecimal
        +getTitle() String
        +setTitle(String title)
        +getDurationMinutes() Integer
    }

    class StandardMovie {
        +getFormat() String
        +getSurcharge() BigDecimal
    }

    class ImaxMovie {
        +getFormat() String
        +getSurcharge() BigDecimal
    }

    class ThreeDMovie {
        +getFormat() String
        +getSurcharge() BigDecimal
    }

    class MovieFactory {
        <<factory>>
        +create(String format) Movie
    }

    class MovieService {
        +search(String title, String genre) List~MovieResponse~
        +getById(Long id) MovieResponse
        +create(MovieRequest req) MovieResponse
        +update(Long id, MovieRequest req) MovieResponse
        +delete(Long id)
    }

    Movie <|-- StandardMovie
    Movie <|-- ImaxMovie
    Movie <|-- ThreeDMovie
    MovieFactory ..> Movie : creates
    MovieService --> MovieFactory : uses
    MovieService ..> Movie : manages
```

---

## 3. Theatres and screens (Member 3)

```mermaid
classDiagram
    direction LR

    class Theatre {
        -Long id
        -String name
        -String city
        -String address
        -String phone
    }

    class Screen {
        <<abstract>>
        -Long id
        -String name
        -Integer rowCount
        -Integer seatsPerRow
        -Theatre theatre
        +getScreenType()* String
        +getPriceMultiplier()* BigDecimal
        +getCategory()* String
        +setLayout(int rows, int seatsPerRow)
        +getTotalSeats() int
        +isValidSeat(String seatCode) boolean
        +getAllSeatCodes() List~String~
    }

    class StandardScreen {
        +getPriceMultiplier() BigDecimal
    }

    class PremiumScreen {
        +getPriceMultiplier() BigDecimal
    }

    class VipScreen {
        +getPriceMultiplier() BigDecimal
    }

    class ScreenFactory {
        <<factory>>
        +create(String screenType) Screen
    }

    class TheatreService {
        +list() List~TheatreResponse~
        +get(Long id) TheatreResponse
        +update(Long id, TheatreRequest req) TheatreResponse
        +delete(Long id)
        +findEntity(Long id) Theatre
    }

    class ScreenService {
        +list(Long theatreId) List~ScreenResponse~
        +get(Long id) ScreenResponse
        +create(ScreenRequest req) ScreenResponse
        +update(Long id, ScreenRequest req) ScreenResponse
        +delete(Long id)
    }

    Theatre "1" o-- "*" Screen : has
    Screen <|-- StandardScreen
    Screen <|-- PremiumScreen
    Screen <|-- VipScreen
    ScreenFactory ..> Screen : creates
    ScreenService --> ScreenFactory : uses
    TheatreService ..> Theatre : manages
```

---

## 4. Showtime scheduling and pricing (Member 4)

```mermaid
classDiagram
    direction TB

    class Showtime {
        -Long id
        -Movie movie
        -Screen screen
        -LocalDateTime startTime
        -BigDecimal basePrice
        +Showtime(Movie movie, Screen screen, LocalDateTime startTime, BigDecimal basePrice)
        +updateDetails(Movie movie, Screen screen, LocalDateTime startTime, BigDecimal basePrice)
        +getMovie() Movie
    }

    class PricingStrategy {
        <<interface>>
        +supports(LocalDateTime showTime) boolean
        +priceFor(BigDecimal basePrice, LocalDateTime showTime) BigDecimal
        +name() String
        +priority() int
    }

    class WeekdayPricingStrategy {
        +priority() int
    }

    class WeekendPricingStrategy {
        +priority() int
    }

    class MatineePricingStrategy {
        +priority() int
    }

    class ShowService {
        +listAll() List~ShowResponse~
        +getById(Long id) ShowResponse
        +create(ShowRequest req) ShowResponse
        +update(Long id, ShowRequest req) ShowResponse
        +delete(Long id)
    }

    class Movie {
        <<abstract>>
    }
    class Screen {
        <<abstract>>
    }

    PricingStrategy <|.. WeekdayPricingStrategy
    PricingStrategy <|.. WeekendPricingStrategy
    PricingStrategy <|.. MatineePricingStrategy
    Showtime --> Movie
    Showtime --> Screen
    Showtime ..> PricingStrategy : priced by (highest priority that supports the time)
    ShowService --> Showtime : manages
```

---

## 5. Booking and seats (Member 5)

```mermaid
classDiagram
    direction TB

    class Booking {
        -Long id
        -String reference
        -Customer customer
        -Showtime show
        -Set~String~ seatCodes
        -BigDecimal totalAmount
        -BookingStatus status
        -LocalDateTime createdAt
        +Booking(String reference, Customer customer, Showtime show, Set~String~ seats, BigDecimal totalAmount)
        +markConfirmed()
        +getSeatCount() int
        +getSeatCodes() Set~String~
        +getStatus() BookingStatus
    }

    class BookingStatus {
        <<enumeration>>
        PENDING_PAYMENT
        CONFIRMED
        CANCELLED
    }

    class CancellationPolicy {
        <<interface>>
        +supports(long hoursBeforeShow) boolean
        +refundFor(BigDecimal bookingTotal) BigDecimal
        +name() String
    }

    class FullRefundPolicy {
        +refundFor(BigDecimal bookingTotal) BigDecimal
    }

    class PartialRefundPolicy {
        +refundFor(BigDecimal bookingTotal) BigDecimal
    }

    class NoRefundPolicy {
        +refundFor(BigDecimal bookingTotal) BigDecimal
    }

    class BookingService {
        +listMine(Long customerId) List~BookingResponse~
        +getMine(Long customerId, Long bookingId) BookingResponse
        +listAll() List~BookingResponse~
        +updateSeats(Long customerId, Long bookingId, BookingUpdateRequest req) BookingResponse
        +adminDelete(Long bookingId)
    }

    Booking --> BookingStatus
    CancellationPolicy <|.. FullRefundPolicy
    CancellationPolicy <|.. PartialRefundPolicy
    CancellationPolicy <|.. NoRefundPolicy
    BookingService ..> CancellationPolicy : selects by hours before show
    BookingService --> Booking : manages
```

---

## 6. Payments and file handling (Member 6)

```mermaid
classDiagram
    direction TB

    class Payment {
        -Long id
        -String transactionRef
        -Booking booking
        -BigDecimal amount
        -BigDecimal convenienceFee
        -BigDecimal refundedAmount
        -PaymentMethodType method
        -PaymentStatus status
        -String maskedReference
        +getMethod() PaymentMethodType
        +getStatus() PaymentStatus
        +setStatus(PaymentStatus status)
        +refund(BigDecimal refundAmount, BigDecimal bookingTotal)
    }

    class PaymentStatus {
        <<enumeration>>
        SUCCESS
        REFUNDED
        PARTIALLY_REFUNDED
    }

    class PaymentMethodType {
        <<enumeration>>
        CARD
        WALLET
        CASH_COUNTER
    }

    class PaymentProcessor {
        <<interface>>
        +method() PaymentMethodType
        +convenienceFee(BigDecimal bookingTotal) BigDecimal
        +validateReference(String reference)
        +maskReference(String reference) String
        +describe() String
        +charge(BigDecimal bookingTotal, String reference) PaymentCharge
    }

    class AbstractPaymentProcessor {
        <<abstract>>
        +charge(BigDecimal bookingTotal, String reference) PaymentCharge
        +convenienceFee(BigDecimal bookingTotal) BigDecimal
    }

    class CardPaymentProcessor {
        +method() PaymentMethodType
        +validateReference(String reference)
        +convenienceFee(BigDecimal bookingTotal) BigDecimal
        +maskReference(String reference) String
    }

    class WalletPaymentProcessor {
        +method() PaymentMethodType
        +validateReference(String reference)
        +maskReference(String reference) String
    }

    class CashCounterPaymentProcessor {
        +method() PaymentMethodType
        +validateReference(String reference)
        +maskReference(String reference) String
    }

    class PaymentCharge {
        <<record>>
        +BigDecimal amount
        +BigDecimal convenienceFee
        +String maskedReference
        +String description
    }

    class PaymentService {
        +pay(Long customerId, PaymentRequest req) PaymentResponse
        +listMine(Long customerId) List~PaymentResponse~
        +listAll() List~PaymentResponse~
        +updateStatus(Long id, PaymentStatusRequest req) PaymentResponse
        +delete(Long id)
    }

    class FileService {
        +importMovies() ImportResult
        +importUsers() ImportResult
        +importShowtimes() ImportResult
        +exportMovies()
        +exportShowtimes()
        +exportSalesReport() Path
    }

    class ImportResult {
        <<record>>
        +String file
        +int imported
        +int skipped
        +List~String~ errors
    }

    PaymentProcessor <|.. AbstractPaymentProcessor
    AbstractPaymentProcessor <|-- CardPaymentProcessor
    AbstractPaymentProcessor <|-- WalletPaymentProcessor
    AbstractPaymentProcessor <|-- CashCounterPaymentProcessor
    AbstractPaymentProcessor ..> PaymentCharge : returns
    PaymentService --> PaymentProcessor : selects by method
    PaymentService --> Payment : manages
    Payment --> PaymentStatus
    Payment --> PaymentMethodType
    FileService ..> ImportResult : returns
```

---

## 7. Cross-module relationships (overview)

```mermaid
classDiagram
    direction LR

    class Customer
    class Admin
    class Movie
    class Screen
    class Theatre
    class Showtime
    class Booking
    class Payment

    Theatre "1" o-- "*" Screen
    Movie "1" <-- "*" Showtime
    Screen "1" <-- "*" Showtime
    Customer "1" <-- "*" Booking
    Showtime "1" <-- "*" Booking
    Booking "1" <-- "*" Payment
    Admin ..> Movie : manages
    Admin ..> Showtime : manages
    Admin ..> Booking : views, deletes cancelled
    Admin ..> Payment : refunds, deletes refunded
```

### Delete rules (enforced by services)

| Parent | Blocked while … |
|---|---|
| Movie | showtimes exist |
| Theatre | screens exist |
| Screen | showtimes exist |
| Showtime | bookings exist |
| Customer | bookings exist |
| Admin | the caller is not SUPER_ADMIN (checked in `AdminService`) |
