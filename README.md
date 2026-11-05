# Allin1Calc Version 1.0

**One App. Every Calculation.**

Allin1Calc is a free, student-first web app for everyday age, date, time, unit, and academic calculations. Each calculator has its own browser page. Java performs the calculations and returns JSON to the pages.

## V1 calculator set

| Group | Calculators |
|---|---|
| Age and date | Age Calculator, Age Difference, Date Difference, Add or Subtract Date, Workdays Counter, Leap Year Checker |
| Time | Time Calculator, Hours Worked, Countdown |
| Units | Unit Converter for length, weight, temperature, speed, data size, and time |
| Student tools | Attendance Calculator, Bunk Planner, Percentage Calculator, Average Calculator, CGPA to Percentage |

The Percentage Calculator supports six operations: find a percent of a number, find what percent one value is of another, find a whole from a part and percent, percentage change, increase by a percent, and decrease by a percent.

Other V1 features include input validation, clear error messages, copy result, light and dark themes, responsive pages, and calculation history saved in the current browser with `localStorage`. The bunk planner can show attendance projections for planned absences. No login or database is used.

The Attendance Calculator separates total classes scheduled, classes completed so far, and classes attended. It shows the current percentage and up to five projections for either attending or missing upcoming scheduled classes. The Bunk Planner continues to show its planned-absence calculation.

The interface follows the project’s teal-and-amber design and uses a custom calculator mark as the app logo and browser icon. Calculator links, form controls, and result cards share the same visual style across desktop and mobile layouts.

## Technology

- Java 17 and `java.time`
- JDK built-in `HttpServer`
- Small in-project `Json.java` parser and serializer
- HTML5, CSS3, JavaScript, and Fetch API
- Browser `localStorage`
- Dockerfile for a future hosting build

The project uses no framework, Maven, or external Java libraries.

## Run locally

Install JDK 17 or later, then open a terminal in the `Allin1Calc` folder:

```text
javac -d out *.java backend/*.java
java -cp out Server
```

Open `http://localhost:8080`. If the browser does not open automatically, enter the address yourself. To use a different port, set the `PORT` environment variable before starting the server.

Each calculator module also includes a text version that can run on its own after compilation:

```text
java -cp out AgeCalculator
java -cp out DateCalculator
java -cp out TimeCalculator
java -cp out UnitConverter
java -cp out AttendanceCalculator
java -cp out PercentageCalculator
java -cp out AverageCalculator
java -cp out CgpaCalculator
```

Dates use `yyyy-MM-dd` in the browser fields and text version; times use `HH:MM` where a time-only value is expected.

## API routes

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

Invalid requests return HTTP 400 with a JSON error message.

For the Attendance Calculator, `POST /api/attendance` accepts `{"scheduled":60,"classesDone":50,"attended":40,"target":75}`. The response includes the current 80% attendance and projections for the next 1–5 scheduled classes, capped by the remaining schedule. The older `total` input remains available to the Bunk Planner.

## Quality and security

The server limits request bodies to 100 KB and numeric text fields to 40 characters. It validates dates from years 1 to 9999 and rejects invalid ranges and arithmetic overflow. Static serving is restricted to the frontend folder, and path traversal is blocked. Responses include Content-Security-Policy, X-Content-Type-Options, X-Frame-Options, and Referrer-Policy headers. CORS is off by default. API rate limiting defaults to 120 requests per minute per client and can be configured with `RATE_LIMIT_PER_MIN`.

Calculation history stays in the current browser profile. It is not sent to a user account or synchronized to a server.

## Tests and release status

The supplied `tests/AllTests.java` contains 130 plain-Java checks for calculation logic, invalid input, API behavior, security rules, and frontend links. The suite has not been rerun after the recent interface and attendance schedule changes. Real-phone testing also remains pending. A free hosting service may sleep after inactivity. Workdays exclude public holidays, and the CGPA multiplier depends on each college's rule.

## Published links

- GitHub: https://github.com/nihalraj-nira/Allin1calc
- Live app: https://allin1calc.onrender.com

Render builds the Docker image during deployment. A local Docker build was not run.

## Project structure

```text
Allin1Calc/
├── Server.java
├── Json.java
├── Validator.java
├── backend/        Eight modular Java calculation files
├── frontend/       Home page, 15 calculator pages, history, shared CSS and JavaScript
├── tests/          AllTests.java
├── Dockerfile
└── README.md
```

## Future scope

V2 ideas are a scientific calculator, statistics, number systems, matrices, equation solving, and geometry. V3 ideas are MySQL/JDBC, user accounts, cloud history, a mobile app, an AI assistant while keeping the calculation engine deterministic, finance and currency tools, and security upgrades. These ideas are not part of V1.
