# JedisLite

JedisLite is a small Redis-like server made using Java 21.

I built this project to understand how Redis works internally, especially things like network communication, the RESP2 protocol, in-memory data storage, key expiration, and basic persistence.

## What it supports

### Strings

* SET
* GET
* INCR
* DECR

### Hashes

* HSET
* HGET
* HDEL
* HEXISTS
* HLEN
* HGETALL

### Lists

* LPUSH
* RPUSH
* LPOP
* RPOP
* LRANGE
* LLEN
* LINDEX

### Sets

* SADD
* SMEMBERS
* SREM
* SISMEMBER

### Other commands

* PING
* EXPIRE
* TTL
* SAVE
* BGSAVE

## Tech Used

* Java 21
* Java NIO
* Maven
* RESP2
* JUnit

## Requirements

You need:

* Java 21+
* Maven 3.8+
* redis-cli

## Running the project

Clone the repository:

```bash
git clone https://github.com/YOUR_USERNAME/JedisLite.git
cd JedisLite
```

Build the project:

```bash
mvn clean compile
```

Start the server:

```bash
java -cp target/classes com.jedislite.JedisLiteServer 6379
```

Then open another terminal and connect using:

```bash
redis-cli -p 6379
```

## Example

```text
127.0.0.1:6379> PING
PONG

127.0.0.1:6379> SET user:1 "John"
OK

127.0.0.1:6379> GET user:1
"John"

127.0.0.1:6379> EXPIRE user:1 60
(integer) 1

127.0.0.1:6379> TTL user:1
(integer) 60
```

You can also try hashes, lists, and sets:

```text
127.0.0.1:6379> HSET user:1 name John
(integer) 1

127.0.0.1:6379> HGET user:1 name
"John"
```

## Running Tests

```bash
mvn test
```

## Project Structure

```text
JedisLite
├── src
│   ├── main
│   │   └── java
│   └── test
│       └── java
├── pom.xml
└── README.md
```

## What I learned

While working on this project, I got practical experience with:

* Java NIO and Selector
* TCP connections
* RESP2 protocol
* In-memory data structures
* Key expiration
* Basic persistence
* Writing tests with JUnit
* Maven project setup

## Future Plans

* Add more Redis commands
* Add transactions
* Add Pub/Sub
* Improve persistence
* Add more tests
* Do some performance testing

## License

This project was made for learning and practice.
