# Local Dev Setup

## Prerequisites
- Docker Desktop
- Java 21
- Stripe CLI (https://stripe.com/docs/stripe-cli)

## Steps

**1. Start Postgres**
```bash
docker-compose up -d
```

**2. Configure local properties**
```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```
Leave the values as-is for now. You will update the webhook secret in step 4.

**3. Run the app**

In IntelliJ: add `local` to the Active Profiles field in the Spring Boot run configuration.

Or from the terminal:
```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Flyway runs on startup and creates the four tables automatically.

**4. Start Stripe CLI forwarding**
```bash
stripe listen --forward-to localhost:8080/webhooks/stripe
```

Copy the webhook signing secret printed by the CLI (starts with `whsec_`) and paste it
into `application-local.properties`:
```
hookrelay.stripe.webhook-secret={whsec_placeholder}
```

Restart the app.

**5. Register demo endpoints**
```bash
bash scripts/register-demo-endpoints.sh
```

This registers three chaos consumers (order-service, email-service, analytics) against
your local server.

**6. Trigger a test event**
```bash
stripe trigger payment_intent.succeeded
```

Watch the app logs. You should see:
- One ingest log line with `deliveries=3`
- Three dispatch attempts across the worker threads
- Some retries if the chaos endpoint fires a 500

## Verify delivery state

```bash
# List all endpoints
curl -H "X-Admin-Key: local-admin-key" http://localhost:8080/admin/endpoints

# Check deliveries for an event (use the event ID from logs)
curl -H "X-Admin-Key: local-admin-key" http://localhost:8080/admin/events/1/deliveries

# Replay a dead-lettered delivery
curl -X POST -H "X-Admin-Key: local-admin-key" http://localhost:8080/admin/deliveries/1/replay

# Metrics
curl http://localhost:8080/actuator/prometheus | grep hookrelay
```
