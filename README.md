# Allin1Calc

**One App. Every Calculation.**

Allin1Calc is a free, student-first web toolkit that brings everyday age, date, time, unit and academic calculations into one app. Version 1.0 has **15 calculator pages in four groups**. All calculation logic is written in **plain Java 17**, served by the JDK's built-in `HttpServer`, with a responsive HTML, CSS and JavaScript frontend. There is no framework, no Maven build, no external library and no database.

- **Live app:** https://allin1calc.onrender.com
- **Source code:** https://github.com/nihalraj-nira/Allin1calc

> Hosted on a free Render plan, so the first request after a long idle period can be slow while the service wakes up.

---

## Contents

- [Features](#features)
- [Calculators](#calculators)
- [Attendance Calculator and Bunk Planner](#attendance-calculator-and-bunk-planner)
- [Tech stack](#tech-stack)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Validation and security](#validation-and-security)
- [Testing](#testing)
- [Docker and deployment](#docker-and-deployment)
- [Limitations](#limitations)
- [Roadmap](#roadmap)
- [About the project](#about-the-project)

---

## Features

- **15 calculators** in four groups: Age & Date, Time, Units and Student Tools.
- **Java does the maths.** Dates and times use the `java.time` API, so leap years, month lengths and weekdays are handled by the library rather than hand-written rules.
- **Student-focused planning tools:** attendance percentage, classes needed, safe bunks and a full projection for every remaining class, plus a CGPA-to-percentage converter with a configurable multiplier.
- **Validation on the Java side** with short, clear error messages (for example, 31 February is rejected).
- **Private by design:** no accounts, no database. Calculation history and the theme choice stay in your own browser (`localStorage`).
- **Copy-result button**, **light and dark themes** and **responsive pages** for phones and computers.
- **Every calculator class can run on its own** as a text program from the command line.
- **Server safeguards:** request size limit, rate limit, security headers and protection against path traversal.
- **Automated plain-Java test suite** and a **Dockerfile** for hosting.

## Calculators

| Group | Page | Java class | API route |
|---|---|---|---|
| Age & Date | Age Calculator | `AgeCalculator` | `POST /api/age` |
| | Age Difference | `AgeCalculator` | `POST /api/age/difference` |
| | Date Difference | `DateCalculator` | `POST /api/date/difference` |
| | Add / Subtract Date | `DateCalculator` | `POST /api/date/add` |
| | Workdays Counter | `DateCalculator` | `POST /api/date/workdays` |
| | Leap Year Checker | `DateCalculator` | `GET /api/leap` |
| Time | Time Calculator | `TimeCalculator` | `POST /api/time/calc` |
| | Hours Worked | `TimeCalculator` | `POST /api/time/hours-worked` |
| | Countdown | `TimeCalculator` | `POST /api/time/countdown` |
| Units | Unit Converter | `UnitConverter` | `POST /api/convert` |
| Student Tools | Attendance Calculator | `AttendanceCalculator` | `POST /api/attendance` |
| | Bunk Planner | `AttendanceCalculator` | `POST /api/attendance` |
| | Percentage Calculator | `PercentageCalculator` | `POST /api/percentage` |
| | Average Calculator | `AverageCalculator` | `POST /api/average` |
| | CGPA to Percentage | `CgpaCalculator` | `POST /api/cgpa` |

The app also has a **History** page that lists recent calculations stored in the current browser.

**Unit categories:** length, weight, temperature, speed, data size (1024-based steps) and time.

**Percentage operations (six):** percent of a number, what percent one value is of another, find the whole from a part and a percent, percentage change, increase by a percent, and decrease by a percent.

## Attendance Calculator and Bunk Planner

The Attendance Calculator separates three numbers: **total classes scheduled**, **classes done so far** and **classes attended**. It shows the current percentage, a status, and a projection for every remaining scheduled class, both if you attend all of them and if you miss all of them.

Example (60 scheduled, 50 done, 40 attended, target 75%):

| Result | Value |
|---|---|
| Current attendance | 80% |
| Safe bunks | 3 classes |
| Miss 3 more classes | 75.47% (still above the target) |
| Miss a 4th class | 74.07% (below the target) |

**Status rules:** *Safe* when attendance is at least 3 points above the target, *Borderline* when it is above the target but closer than that, and *Shortage* when it is below the target.

**Formulas:**

- Attendance % = `100 x attended / total`
- Classes needed = `max(0, ceil((target x total - 100 x attended) / (100 - target)))`
- Safe bunks = `0` below the target, otherwise `floor(100 x attended / target - total)`

The Bunk Planner shows attendance after a planned number of absences. The target must be between 1 and 99.

## Tech stack

- Java 17 and `java.time`
- JDK built-in `HttpServer`
- A small in-project JSON parser and serializer (`Json.java`) and shared input rules (`Validator.java`)
- HTML5, CSS3, JavaScript and the Fetch API
- Browser `localStorage`
- Plain-Java tests (`tests/AllTests.java`)
- Dockerfile, Git/GitHub and Render for packaging and hosting

## How it works

```text
Browser (HTML, CSS, JavaScript)
   |  JSON request via Fetch
   v
Server.java  ->  size limit, rate limit, security headers
   |
Validator.java + Json.java  ->  parse and check every value
   |
backend/*.java  ->  the matching calculator class
   |
JSON result (or HTTP 400 with a clear error)  ->  shown on the page, saved to history
```

1. The user fills a form and presses **Calculate**. JavaScript sends the values to the matching `/api` route.
2. The server checks the request size and rate, then `Validator` parses and checks every value.
3. If a value is invalid, the server returns HTTP 400 with a short JSON error message.
4. Otherwise the calculator class computes the result and returns it as JSON.
5. The page shows the result as text and stores a history entry in `localStorage`.

The same Java process also serves the frontend files, so the whole product is a single deployable unit. Every backend class has its own `main()` method, so the server and the command line share one source of calculation behaviour.

## Project structure

```text
Allin1Calc/
├── Server.java          HTTP server, routing, safeguards
├── Json.java            JSON reading and writing
├── Validator.java       Shared input rules
├── backend/             Eight calculator classes
│   ├── AgeCalculator.java
│   ├── DateCalculator.java
│   ├── TimeCalculator.java
│   ├── UnitConverter.java
│   ├── AttendanceCalculator.java
│   ├── PercentageCalculator.java
│   ├── AverageCalculator.java
│   └── CgpaCalculator.java
├── frontend/            Home page, 15 calculator pages, history, shared CSS and JavaScript
├── tests/
│   └── AllTests.java    Plain-Java test suite
├── Dockerfile
└── README.md
```

## Getting started

### Prerequisites

- **JDK 17 or later** (the full JDK, so that `javac` is available)
- A web browser

### Compile and run

From the `Allin1Calc` folder:

```bash
javac -d out *.java backend/*.java
java -cp out Server
```

Open **http://localhost:8080**. If the browser does not open automatically, enter the address yourself. To use a different port, set the `PORT` environment variable before starting the server.

### Run a single calculator as a text program

After compiling, each module can run on its own:

```bash
java -cp out AgeCalculator
java -cp out DateCalculator
java -cp out TimeCalculator
java -cp out UnitConverter
java -cp out AttendanceCalculator
java -cp out PercentageCalculator
java -cp out AverageCalculator
java -cp out CgpaCalculator
```

Dates use `yyyy-MM-dd` in the browser fields and the text versions. Times use `HH:MM` where a time-only value is expected.

## Configuration

All settings are optional environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Port the server listens on. Hosting platforms usually set this for you. |
| `RATE_LIMIT_PER_MIN` | `120` | API requests allowed per minute per client. |
| `TRUST_PROXY` | off | Trust proxy headers to identify clients. Turned on automatically when `PORT` is set by a host. |
| `ALLOWED_ORIGIN` | unset | Enables CORS for one origin. CORS is off by default. |
| `FRONTEND_DIR` | auto-detected | Path to the frontend folder, if it is not in the default place. |
| `NO_BROWSER` | unset | If set, the server does not try to open a browser on start. |

## API reference

All API routes live under `/api`. Invalid requests return **HTTP 400** with a JSON error message.

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/health` | Health and version |
| GET | `/api/units` | Unit categories and supported units |
| GET | `/api/leap?year=2024` | Check one year |
| GET | `/api/leap?from=2020&to=2030` | List leap years in a range |
| POST | `/api/age` | Age calculation |
| POST | `/api/age/difference` | Age difference |
| POST | `/api/date/difference` | Date difference |
| POST | `/api/date/add` | Add or subtract a date interval |
| POST | `/api/date/workdays` | Weekdays in a date range |
| POST | `/api/time/calc` | Add or subtract HH:MM values |
| POST | `/api/time/hours-worked` | Worked duration, including overnight shifts |
| POST | `/api/time/countdown` | Remaining time to a target |
| POST | `/api/convert` | Unit conversion |
| POST | `/api/attendance` | Attendance and planning results |
| POST | `/api/percentage` | Percentage operation |
| POST | `/api/average` | Arithmetic average |
| POST | `/api/cgpa` | CGPA conversion |

**Examples** (with the server running locally):

```bash
# Is 2024 a leap year?
curl "http://localhost:8080/api/leap?year=2024"

# Attendance: 60 scheduled, 50 done, 40 attended, 75% target
curl -X POST http://localhost:8080/api/attendance \
  -H "Content-Type: application/json" \
  -d '{"scheduled":60,"classesDone":50,"attended":40,"target":75}'
```

The attendance response includes the current 80% attendance and projections for every remaining scheduled class. The older `total` input remains available for the Bunk Planner.

## Validation and security

**Validation** happens on the Java side before values reach the calculation logic:

- Dates must be real calendar dates in years 1 to 9999; invalid ranges are rejected.
- Numeric text fields are limited to 40 characters.
- Attended classes cannot be negative or exceed total classes; the attendance target must be between 1 and 99.
- Selected units must belong to the selected category; temperatures below absolute zero are rejected.
- Results that overflow or leave the supported range are rejected rather than shown.
- Unexpected failures return a generic message, so no stack trace or internal detail reaches the browser.

**Server safeguards:**

| Safeguard | Purpose |
|---|---|
| Frontend-only file serving | Only files inside the frontend folder are served; paths are normalised so traversal attempts are blocked. |
| Request size limit | API request bodies are capped at 100 KB. |
| Rate limit | 120 API requests per minute per client by default. |
| Security headers | Content-Security-Policy, X-Content-Type-Options, X-Frame-Options and Referrer-Policy. |
| CORS off by default | Avoids broad cross-origin access to the API. |
| Text-only output | Results are inserted into the page as text, not HTML. |
| Local history | History and theme stay in the browser; there are no accounts and no server-side personal data. |

## Testing

The project has a plain-Java test suite with **no test framework**. From the `Allin1Calc` folder:

```bash
javac -d out *.java backend/*.java tests/AllTests.java
java -cp out AllTests
```

Every line says `PASS` or `FAIL`, and the program exits with code 1 if anything failed. The suite covers:

- **Calculation logic:** leap days, month ends, zero values and worked examples.
- **Invalid input:** empty, negative, non-numeric and impossible values such as 31 February.
- **API and security behaviour** on a real running server, including error responses.
- **Frontend links** between pages.

Expected results were worked out independently of the program, so the tests check the logic rather than repeat it. A few examples:

| Calculator | Input | Expected |
|---|---|---|
| Leap year | 1900, 2000, 2024 | No, Yes, Yes |
| Hours worked | 22:00 to 06:00 | 8 hours (overnight) |
| Unit: length | 1 mile to kilometre | 1.609344 |
| Temperature | 100 Celsius to Fahrenheit | 212 |
| Attendance | 40 of 50, target 75% | 80%; 3 safe bunks |
| CGPA | 7.5 with multiplier 9.5 | 71.25% |
| Validation | Attended 50, total 40 | Rejected with a clear error |

## Docker and deployment

The included `Dockerfile` builds the project on `eclipse-temurin:17-jdk`, compiles it with `javac`, runs it as a non-root user and starts the server on `PORT` (default `8080`).

```bash
docker build -t allin1calc .
docker run -p 8080:8080 allin1calc
```

**Deploying on Render:** create a Web Service from the GitHub repository, choose the **Docker** environment, and deploy. Render sets `PORT` automatically, and the server reads it. The live app runs this way at https://allin1calc.onrender.com.

## Limitations

- The workday calculation counts Monday to Friday only and does not know public holidays or institution-specific closures.
- CGPA conversion depends on the multiplier chosen (default 9.5); each institution may use a different rule, so confirm the one your college applies.
- History is stored in one browser and is not shared between devices; clearing site data removes it. There are no accounts or cloud synchronisation.
- Free hosting may sleep when idle, so the first request after a long break can be slow.

## Roadmap

These are proposals only. None of them is part of Version 1.0.

| Stage | Ideas |
|---|---|
| Next feature | BMI calculator |
| Version 2 | Scientific calculator, statistics, matrix operations, number-system conversion, equation solver, geometry |
| Version 3 | MySQL/JDBC storage, user accounts, cloud history, mobile app, AI assistant (keeping the calculation engine deterministic), finance and currency tools, further security upgrades |

Any account or cloud-history feature would need a new privacy and security design, because Version 1.0 deliberately stores nothing about the user on the server.

## About the project

Allin1Calc Version 1.0 is a Java mini-project by **Nihal Raj** (B.Tech Mathematics & Computing, 3rd Year, 5th Semester), Department of Mathematics, School of Physical Sciences, Central University of Karnataka, under the guidance of **Raghavendra Ichangai**, Assistant Professor, Department of Computer Science.

Developed by Nihal Raj.
