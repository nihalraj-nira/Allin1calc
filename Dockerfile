FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN javac -d out *.java backend/*.java

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/out ./out
COPY frontend ./frontend
EXPOSE 8080
CMD ["java", "-cp", "out", "Server"]

