# wb-cache-warmer

The cache warmer is a command line tool to help pre-cache a set of slow endpoints of the WormBase webapp.

## Technical overview

The cache warmer pings each endpoints of the webapp, resulting in the endpoints to be cached. The slow endpoints are described as parameterized URL patterns that, when combined with IDs (such as a WBGene ID), produce the actual URLs for the endpoints. The actual cache mechanism is implemented by the webapp.

The cache warmer is build for **parallelization**. It spawns multiple processes to cache different endpoints in parallel. The only constraint is how much load the upstream API can take.

The cache warmer script can be **resumed if interrupted**. It uses a job queue backed by the disk to track the progress of endpoint caching.

The bulk of the code can be found at [src/wb_cache_warmer/core.clj](src/wb_cache_warmer/core.clj).

## Prerequisites

_Developing on the shared dev instance would meant that all the following prerequisites are met without any additional work._

_Similarly, if you have developed for the [WormBase/wormbase_rest](https://github.com/WormBase/wormbase_rest), the prerequisites are likely to be the same.

Otherwise, please ensure Java, Clojure and [Leiningen](https://github.com/technomancy/leiningen) are installed before moving forward.

This app is developed with Clojure 1.9.0, Leiningen (lein) 2.8.3, and OpenJDK 1.8.0_265.

## Build the cache warmer CLI (executable jar)

To run the cache warmer, you need to build the executable for the cache warmer CLI

At the root of project, run

```
lein uberjar
```

The executables will be a `.jar` file in the `target/uberjar/` folder.

## Run the executable jar

After building the executable, you can run it by

```
cd target/uberjar/
```

and then,

```
java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar [options]

```

_Note: the exact file name (in particular, the "0.1.0-SNAPSHOT" part) would vary based on the version number specificed in [project.clj](project.cli). However, the executable will be the one file with the postfix `-standalone.jar`_


## (For development) Run CLI without building the jar

In development, it may be eaiser to run the clojure code without building a jar. In this case, you could run the following command:

```
lein run -m wb-cache-warmer.core
```

You could also use the `lein repl` to interactively build and test the script.

### Options

The following command line arguemnts are available:

```
  -H, --hostname HOSTNAME  wormbase-website-preproduction.us-east-1.elasticbeanstalk.com  Host to cache from
  -n, --thread-count N     5                                                              Thread counts
  -a, --schedule-all                                                                      Cache all endpoints that is considered slow
      --schedule-sample                                                                   Cache a preset sample of endpoints
  -h, --help
```


### Examples

#### Test run cache warmer on example endpoints

As a full run of the cache warmer on all the endpoints takes about a day, you may want to try a few hardcoded endpoints for testing. To do so, run

```
java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar --schedule-sample
```

#### Run cache warmer on all endpoints

To build cache in preparation for a release, the cache warmer should be run on all endpoints that is determined to be slow.

This will take about a day.

Hence, it might be a good idea to run it with Screen, in case the process is interrupted.

```
java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar --schedule-all
```

#### Change the degree of parallelization

To specify the degree of parallelization, use the `--thread-count` argument in combination with other argument you may need.

The default thread count of 5 should work for our upstream API without overwhelming it with the precaching load.


```
java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar --schedule-sample --thread-count 2
```

#### Stop and resume a job

The cache warmer uses a _persistent_ job queue to store all endpoints to be cached. This allows the cache warmer process to be stopped and resumed at a later time without losing the jobs in the queue.

To stop the cache warmer process, interrupt it with `Ctrl-C`.

To resume the cache warmer, run the cache warmer **without** the `--schedule-all` and `--schedule-sample` argument:

```
java -jar wb-cache-warmer-0.1.0-SNAPSHOT-standalone.jar
```

You may change the degree of paralleleization when resuming with the `--thread-count` argument, if needed.

#### Clear the job queue

Occasionally, it may make sense to clear the job queue. To do so, run

```
rm -r /tmp/cache_warmer_queue
```

This removes the queue that has been persisted to disk.


#### Terminate the cache warmer process (Important!)

You likely need to manually terminte the cache warmer process, ie with `Ctrl-C`.

By design, the cache warmer will keep retrying failed upstream API endpoints until successful. This ensures that when the problem in the endpoints is addressed, the response will be cached without needing to re-run the cache warmer on all endpoints. This however means that failures in the endpoint that aren't addressed will cause the cahce warmer to run forever. For example, a timeout in the upstream API endpoint that persists despite of retries. In this case, a person needs to terminate the cache warmer.



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
