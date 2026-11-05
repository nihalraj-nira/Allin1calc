# Allin1Calc Version 1.0

**One App. Every Calculation.**

Allin1Calc is a free, responsive web application for everyday calculations used by students. It includes 15 calculators for age, dates, time, units, attendance, percentages, averages, and CGPA conversion. The browser uses plain HTML, CSS, and JavaScript; a Java 17 backend performs the calculations.

## Run locally

Install JDK 17 or later. From this folder, compile and run:

```text
javac -d out *.java backend/*.java
java -cp out Server
```

Open `http://localhost:8080` in a browser. To use a different port, pass it to the server, for example `java -cp out Server 9090`, or set the `PORT` environment variable (used by hosting platforms).

Each calculator class also has a small command-line example. After compiling, run a class from the `backend` package directory as a standalone class, for example:

```text
java -cp out AgeCalculator
```

## Calculator list

**Age and date:** Age Calculator, Age Difference, Date Difference, Add or Subtract Date, Workdays Counter, Leap Year Checker.

**Time:** Time Calculator, Hours Worked, Countdown.

**Units:** Unit Converter for length, weight, temperature, speed, data size, and time.

**Student tools:** Attendance Calculator, Bunk Planner, Percentage Calculator, Average Calculator, and CGPA to Percentage.

The percentage operations are: find a percent of a value; find what percent one value is of another; find a whole from a part and percentage; percentage change; increase by a percentage; and decrease by a percentage.

## Architecture

```text
Browser page (HTML/CSS/JavaScript)
        | Fetch API with JSON
        v
Server.java -> Validator.java -> calculator class in backend/
        | JSON result or clear error
        v
Browser result, copy action, and local history
```

The server uses the JDK built-in `HttpServer`. `Json.java` is a small in-project JSON parser and serializer. There is no framework, Maven, external Java library, database, login, or cloud history. Calculation history is held in the current browser's `localStorage`.

## API

GET routes: `/api/health`, `/api/units`, `/api/leap?year=2024`, and `/api/leap?from=2020&to=2030`.

POST routes: `/api/age`, `/api/age/difference`, `/api/date/difference`, `/api/date/add`, `/api/date/workdays`, `/api/time/calc`, `/api/time/hours-worked`, `/api/time/countdown`, `/api/convert`, `/api/attendance`, `/api/percentage`, `/api/average`, and `/api/cgpa`.

Invalid values return HTTP 400 with a JSON `error` message. Request bodies are limited to 100 KB. Per-client API rate limiting defaults to 120 requests per minute and can be changed with `RATE_LIMIT_PER_MIN`.

## Key rules and limitations

- Age uses `java.time.Period`; dates must be valid and within years 1 to 9999.
- Workdays count Monday through Friday, include both endpoints, and do not remove public holidays.
- Attendance target defaults to 75% in the API. The browser asks for the target explicitly. Classes needed are rounded up; safe bunks are rounded down.
- CGPA conversion uses `CGPA × multiplier`; the default is 9.5. The multiplier should match the college's rule.
- Unit conversion supports mm, cm, m, km, inches, feet, yards, miles; mg, g, kg, tonnes, pounds, ounces; Celsius, Fahrenheit, Kelvin; m/s, km/h, mph; decimal and binary data sizes; and milliseconds through days.
- History belongs to one browser and is not synchronized. There are no accounts.

## Docker hosting

The app is deployed on Render using the Dockerfile. The free service may sleep when idle. A local Docker build was not run because Docker CLI is not installed in the preparation environment.

```text
docker build -t allin1calc .
docker run -p 8080:8080 allin1calc
```

## Published links

- GitHub: https://github.com/nihalraj-nira/Allin1calc
- Live app: https://allin1calc.onrender.com

Real-phone testing remains pending.

## Verification status

The Java source compiled successfully with the command above during preparation of this package. The supplied project brief lists `tests/AllTests.java` and 130 checks, but that test source was not included in this generated package and the checks were not run here. The report describes this limitation.

