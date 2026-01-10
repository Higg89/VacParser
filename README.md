# Vacancy Parser

This is a Spring Boot application designed to parse job vacancies from various popular Russian job search websites. It aggregates the data and provides a simple API to access the results.

## Features

- **Multi-site Parsing:** Supports parsing vacancies from the following websites:
  - [Habr Career](https://career.habr.com)
  - [HH.ru](https://hh.ru)
- **Full Results Pagination:** The parser fetches all vacancies from every page of the search results, not just the first page.
- **REST API:** Provides a simple REST API to initiate parsing and retrieve the collected vacancy data.
- **Asynchronous Parsing:** Parsing tasks are executed in the background to prevent blocking API requests.

## Technologies Used

- **Java 17**
- **Spring Boot 3**
- **Maven**
- **Spring Data JPA** (with H2 in-memory database)
- **Lombok**
- **Jsoup** (for HTML scraping)
- **Jackson** (for JSON parsing)
- **Spring WebFlux `WebClient`** (for making HTTP requests)

## How to Run

1.  **Build the application:**
    ```bash
    ./mvnw clean package
    ```

2.  **Run the application:**
    ```bash
    java -jar target/VacParser-0.0.1-SNAPSHOT.jar
    ```

The application will start on `http://localhost:8080`.

## API Usage

### Start Parsing

To start a new parsing task, send a `POST` request to the `/parse` endpoint with the URL of the search results page.

**Endpoint:** `POST /parse`

**Body:**

```json
{
    "url": "URL_of_the_search_results_page"
}
```

**Example `curl` commands:**

-   **Habr Career:**
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{"url": "https://career.habr.com/vacancies?q=java&type=all"}' http://localhost:8080/parse
    ```

-   **HH.ru:**
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{"url": "https://hh.ru/search/vacancy?text=Java&area=1"}' http://localhost:8080/parse
    ```

### Get Results

To retrieve the parsed vacancies, send a `GET` request to the `/answer` endpoint. You can also filter the results by providing a `keyword` query parameter.

**Endpoint:** `GET /answer`

**Query Parameter:**

-   `keyword` (optional): A string to filter vacancies by title or company.

**Example `curl` commands:**

-   **Get all vacancies:**
    ```bash
    curl http://localhost:8080/answer
    ```

-   **Get vacancies filtered by a keyword:**
    ```bash
    curl "http://localhost:8080/answer?keyword=developer"
    ```
