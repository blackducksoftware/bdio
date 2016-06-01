# Black Duck I/O

Black Duck I/O is a specification for how to transfer data between Black Duck products, specifically Bill of Material (BOM) and scan related data. It is also an API for producing and consuming data conforming to the specification. The specification leverages [JSON-LD][json-ld] to represent data. You can find some simple examples of using the API to generate data on the [wiki][wiki].

## Requirements

The Black Duck I/O API requires Java 7 or later. The formated data must conform to the [JSON-LD 1.0][json-ld-1.0] specification.

## Dependency Information

Gradle
````
compile 'com.blackducksoftware.bdio:bdio:x.y.z'
````

Maven
````
    <dependency>
      <groupId>com.blackducksoftware.bdio</groupId>
      <artifactId>bdio</artifactId>
      <version>x.y.z</version>
    </dependency>
````

## Documentation

Please refer to the [project wiki][wiki].

## Build

````
$ git clone git@github.com:blackducksoftware/bdio.git
$ cd bdio/
$ ./gradlew build
````

## License

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.

[json-ld]: http://json-ld.org
[json-ld-1.0]: http://www.w3.org/TR/json-ld/
[wiki]: https://github.com/blackducksoftware/bdio/wiki