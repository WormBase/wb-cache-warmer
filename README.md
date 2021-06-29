# wb-cache-warmer

FIXME: description

## Installation

Please ensure JVM runtime, Clojure and [Leiningen](https://github.com/technomancy/leiningen) are installed before moving forward.

## Build the Cache Warmer CLI

To run the cache warmer, you need to build the executable for the cache warmer CLI

At the root of project, run

```
lein uberjar
```

The executables will be a `.jar` file in the `target/uberjar/` folder.

## Run the executable (CLI)

After building the executable, you can run it by

```
cd target/uberjar/

java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar [options]

```

_Note: the exact file name (in particular, the "0.1.0-SNAPSHOT" part) would vary based on the version number specificed in [project.clj](project.cli). However, the executable will be the one file with the postfix `-standalone.jar`_

### Options

```
  -H, --hostname HOSTNAME  wormbase-website-preproduction.us-east-1.elasticbeanstalk.com  Host to cache from
  -n, --thread-count N     5                                                              Thread counts
  -a, --schedule-all                                                                      Cache all endpoints that is considered slow
      --schedule-sample                                                                   Cache a preset sample of endpoints
  -h, --help
```



### Examples



...

### Known issues

- The cache warmer will keep retrying failed upstream API endpoints until successful. This ensures that when the problem in the endpoints is addressed, the response will be cached without needing to re-run the cache warmer. This however means that failures in the endpoint that aren't addressed will cause the cahce warmer to run forever. An example of such a failure is timeout in the upstream API endpoint that persists despite of retries. In this case, a person needs to terminate the cache warmer.



## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
