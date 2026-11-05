# Only needed when the project is put on a hosting site (Render, Railway, ...).
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac --release 17 -d out *.java backend/*.java \
    && useradd --system --no-create-home appuser
USER appuser
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "out", "Server"]
