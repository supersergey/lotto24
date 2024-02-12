# Home Assignment by Sergiy Tolokunsky

## Prerequisites
1. JDK 17+
2. Docker Engine 24+

## Build instructions

On Mac and Linux, execute `sh build.sh` from the command line . On other platforms,
run `docker build -t transaction-service .`

## Running the app

After you have finished the build, run `docker-compose up -d`. This will spin up the `Mongo` database
engine and the app itself.

> Mare sure no other application occupies 8080 port.

## Endpoints

The application provides the REST API to its functionality. Swagger UI is available on 
http://localhost:8080/swagger-ui/index.html

### Deposit money to the balance
```
POST /api/transactions
```
books the requested amount on the customer's account

```shell
curl -X 'POST' \
  'http://localhost:8080/api/transactions' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "amount": 10,
  "agent": "string"
}'
```

On success, returns the `transactionId`, which you may need to issue the roll-back request.

>Note: the system will not allow to book more credits than a customer currently has on their account

### List transactions
```
GET /api/transactions
```
List transactions by customer and tenant. This also may server as an audit log.

```shell
curl -X 'GET' \
  'http://localhost:8080/api/transactions?tenantId=3fa85f64-5717-4562-b3fc-2c963f66afa6&customerId=3fa85f64-5717-4562-b3fc-2c963f66afa6&skip=0&limit=100000' \
  -H 'accept: */*'
```

### Rollback a transaction
```
DELETE /api/transactions/{transactionId}
```
Rolls a transaction back. Under the hood, issues a compensating transaction, which will be visible in the transactions 
log.

```shell
curl -X 'DELETE' \
  'http://localhost:8080/api/transactions/e0be275a-78aa-4e4b-8a12-68dad93fb43c' \
  -H 'accept: */*'
```

> There is a corner case here, one may trigger this endpoint many times, and issue more than one
> compensating transactions. Preventing this behavior is up to discussion.

### View balance
```
GET /api/balances
```
Displays the balance for the given tenant and customer.

```shell
curl -X 'GET' \
  'http://localhost:8080/api/balances?tenantId=3fa85f64-5717-4562-b3fc-2c963f66afa6&customerId=3fa85f64-5717-4562-b3fc-2c963f66afa6' \
  -H 'accept: */*'
```

## Design considerations
Account balance design is a known problem. The key consideration here is to ensure the concurrent 
consistency between the transactions log and the balance. I have decided to maintain `Transaction` and `Balance` 
database entities and to use atomic operations to modify the `Balance`. This approach gives reasonable performance
and good consistency. The corner cases may still arise, although they should be quite rare. A solution involving a 
re-processing queue should help resolve those issues.

I have selected MongoDB as a database engine, taking into account the following:
1. Accounting systems usually receive high volume of the requests. Mongo is known for good horizontal
scalability and sharding. Also, it is good for implementing the CQRS pattern, which seems like a good candidate for 
high-loaded systems.
2. Mongo supports atomic upserts, which makes it easier to ensure concurrent consistency.
3. With the recently added support of transactions, we may add some transactional functionality in the future,
should this be needed.
4. Mongo is easy to start, we don't need additional ORM and schema migration frameworks to spin-up the project.

The same functionality can be built on top of an SQL engine (e.g. Postgres). 