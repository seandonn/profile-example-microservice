
# Profile example Microservice

This is an example microservice written in Java that offers CRUD (create, read, update, delete) operations on arbitrary JSON data.  
It is designed to serve as the repository of customer profile data and is enhanced to encrypt such data at an application level as 
well as track all updates.  See [design doc](docs/design.pdf) for further details and motivation.

## Compiling

Requires Maven to build:

`mvn package`

Note: Successful encryption requires using at least Java 8 b113 due to the use of AES GCM mode in a 
cipher stream, which was impacted by the bug [JDK-8012900](https://bugs.openjdk.java.net/browse/JDK-8012900).
The JVM should also have the US crypto policies installed to enable strong encryption.

## Running

`java -cp 'target/lib/*:target/profile-microservice-1.0.0-SNAPSHOT.jar' com.example.profile.Server`

will start the server at port 8001 in Linux or OS X environments.  For Windows, please adjust the classpath to use semicolons instead of colons.

## curl Examples

All requests require a JWT token with the correct permissions. The [online JWT builder](http://jwtbuilder.jamiekurtz.com/) is very helpful.

For this code base, the expected parameters should match the configured `jwt.*` properties:

| Field    | Value               |
|----------|---------------------|
| Issuer   | example.com         |
| Audience | Profile Service     |
| Subject  | <client identifier> |

The claims should be of type `Perm` with the permission values detailed in   
the [design doc](docs/design.pdf).

Encryption key is as specified in the `jwt.secretkey` property.

### Posting data
`curl -X POST -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NjU2MjUwNywiZXhwIjoxNTc4MDk4NTA3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJ0ZXN0ZXIiLCJQZXJtIjpbInByb2ZpbGU6cnciLCJwcm9maWxlLiouZm9vOnJ3IiwicHJvZmlsZS4qOnJ3IiwicHJvZmlsZS4qLmJhcjpydyJdfQ.RTSajBr9Gyiz6KXDN1w9vh1p6h0cPZX5_TACdNMdYmU" -H "Content-Type: application/json" -d '{"foo":"bar"}' http://localhost:8001/service/profile`

### Retrieving data
`curl -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLmNvbSIsImlhdCI6MTU0NjU2MjUwNywiZXhwIjoxNTc4MDk4NTA3LCJhdWQiOiJQcm9maWxlIFNlcnZpY2UiLCJzdWIiOiJ0ZXN0ZXIiLCJQZXJtIjpbInByb2ZpbGU6cnciLCJwcm9maWxlLiouZm9vOnJ3IiwicHJvZmlsZS4qOnJ3IiwicHJvZmlsZS4qLmJhcjpydyJdfQ.RTSajBr9Gyiz6KXDN1w9vh1p6h0cPZX5_TACdNMdYmU"  http://localhost:8001/service/profile/`*uuid*

where *uuid* has previously been returned from a POST

