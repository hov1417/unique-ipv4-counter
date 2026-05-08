# Unique IPv4 Counter
This is my attempt of [this assignment](https://github.com/Ecwid/new-job/blob/master/IP-Addr-Counter.md).

## Implementation Overview
The code is in [UniqueIPCounter.java](./UniqueIPCounter.java) file.

UniqueIPCounter class generally speaking does this steps

- Spawn child process
- Child process memory maps the input file
- Spawn threads each loads small chunk parses sets in bitmap, then loads next chunk
- Count number of set bits and print

 ### Used Technics For Speedup

| Optimization                          | Description                                                                                     |
|---------------------------------------|-------------------------------------------------------------------------------------------------|
| GraalVM                               | Used graalvm-jdk native image, as specified in .java-version                                    |
| Parallelization                       | Used as many threads as processors available to the Java virtual machine.                       |
| Reducing allocations                  | Removed almost all the allocations                                                              |
| Small Chunks                          | Parsing in small chunks, each thread when available takes next the chunk, 8MB worked best       |
| Memory Mapping                        | Memory mapping the file, to reduce unnecessary memory usage                                     |
| Unsafe                                | Using Unsafe to remove pointer bound checks when reading from mapped memory                     |
| Reading IPv4 Addresses as longs       | Reading IPv4 addresses as `long`s (1-2 long per line) instead of `char[]`, `byte[]` or `String` |
| SWAR                                  | (SIMD Within A Register) Using bitwise operator to process data if possible                     |
| BitSet                                | Using Atomic Long Array as a Bitset instead of `HashSet` or `boolean[]`                         |
| Skipping Unmapping with child process | Spawn Child process, which will be killed before unmapping, thus reusing unmapping delay        |
| Thread Priority                       | Setting priority to Thread.MAX_PRIORITY on all threads                                          |


### Technics That Had Negative Or Zero Impact

#### Vector API / SIMD
Implemented [this](http://0x80.pl/notesen/2023-04-09-faster-parse-ipv4.html) SIMD-ification of IPv4 parsing code.
Vector API lacks some instructions ([PMADDUBSW](https://www.felixcloutier.com/x86/pmaddubsw),
[PALIGNR](https://www.felixcloutier.com/x86/palignr)), which can be worked around. But using workarounds in the API combined with bound checks of JVM,
make it slower than current implementation.

#### Profile Guided Optimization of GrallVM
Using PGO made it a little bit slowed, I suspect the reason is that GraalVM PGO adds conditions for hot code segments,
to heavily optimize some specific cases, added overhead slows down (perf stat: 101,478,420,847 vs 60,508,794,883 branches).

#### GC tuning
VisualVM shows very little GC overhead, so doesn't make sense to optimize the GC.

#### Per case SWAR
Let's consider cases of IPv4 addresses: each segment can be from 1 to 3 digits, so overall `3 * 3 * 3 * 3 = 81` cases.
Using some bitwise operations we can create a perfect hash function unique for every case.
I used following hashcode
```java
long x1 = getDotCode(value1);
long x2 = getDotCode(value2) | getLFCode(value2);
long hashcode = (x2 >>> 2) | x1;
hashcode = (hashcode >>> 33) ^ (hashcode & 0xff_ff_ff_ffL);
```
where `getDotCode` and `getLFCode` return dot and newline masks,
where if character matches then byte is non-zero, otherwise it is zero.

We can calculate probability for each case, then sort if-else clauses by them, or use switch case, which may
be translated into binary search or [tableswitch](https://docs.oracle.com/javase/specs/jvms/se6/html/Instructions2.doc14.html).
Then generate parsing code for each case
```java
// x.x.x.x case is far simpler
if (nextLine - lineStart == 8) {
    var value = UNSAFE.getLong(lineStart);
    return (int) ((value & 0x00_00_00_00_00_00_00_0FL) |
        ((value & 0x00_00_00_00_00_0F_00_00L) >> 8) |
        ((value & 0x00_00_00_0F_00_00_00_00L) >> 16) |
        ((value & 0x00_0F_00_00_00_00_00_00L) >> 24)
    );
}
var c = UNSAFE.getLong(lineStart + 8);
c = c & ((1L << (Long.numberOfTrailingZeros(getLFCode(c)))) - 1);

var value1 = UNSAFE.getLong(lineStart) & 0x0F_0F_0F_0F_0F_0FRemove_0F_0FL;
var value2 = c & 0x0F_0F_0F_0F_0F_0F_0F_0FL;

int hashcode = (int) getHashcode(value1, value2);
if (hashcode == -268435456) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | (((((value2 >>> 32) & 0xFF) * 100) + (((value2 >>> 40) & 0xFF) * 10) + ((value2 >>> 48) & 0xFF))));
} else if (hashcode == 15728640) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
} else if (hashcode == -2140143616) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
} else if (hashcode == -1070596096) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
} else if (hashcode == -535822336) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | (((((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF))));
} else if (hashcode == 8417280) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
        ...
} else if (hashcode == 12583008) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
}
return 0;
```

However, this doesn't help much, speedup is statistically insignificant.

This can be further optimized when examining each case thoroughly.
For example this code
```java
return (int) (
    ((((value1 & 0xFF) * 2560) + (value1 & 0xFF00)) << 16)
        | ((((((value1 >>> 8) & 0xFF0000) * 100)) + (((value1 >>> 16) & 0xFF0000) * 10) + ((value1 >>> 24) & 0xFF0000)))
        | (((((value1 >>> 56) & 0xFF) * 25600) + ((value2 & 0xFF) * 2560) + ((value2) & 0xFF00)))
        | (((((value2 >>> 24) & 0xFF) * 100) + (((value2 >>> 32) & 0xFF) * 10) + ((value2 >>> 40) & 0xFF)))
)
```
is equivalent to
```java
private static final long MULTIPLIER_0 = 0x1 + 10 * 0x100L + 0x10000L * 100;
private static final long MULTIPLIER_2_1 = 0x1_00_00 + 10 * 0x1_00_00_00L;
...
return (int) (
    (value1 * MULTIPLIER_2_1)
        | (((value1 >>> 24) * MULTIPLIER_0) & 0xFF0000L)
        | (((((value1 >>> 56) | ((value2 << 8) & 0xFFFF00)) * MULTIPLIER_0) >> 8) & 0xFF00L)
        | ((((value2 >>> 24) * MULTIPLIER_0) >>> 16) & 0xFFL)
)
```
which is faster for 1-2% than the first one.

Yet this would take me at least few days, and I'm already working on this for a week,
so I decided to stick to my implementation.

#### Work Stealing Execution
I tried using ForkJoinPool, which did not help. It's faster when code manages work stealing.


#### Per Thread Bitset
Storing bitset per thread without atomics, then combining and adding.

I also tried storing in an array per chunk then locking global bitset and adding all values. 


## Results
Tested on `AMD Ryzen 9 9950X 16-Core Processor` CPU with 64GB RAM.

| Size          | Average Time | Standard Deviation | Range                | Number of Runs | Max Resident Memory |
|---------------|--------------|--------------------|----------------------|----------------|---------------------|
| 10            | 261.5 ms     | 5.4 ms             | 249.3 ms - 278.7 ms  | 100            | 537 MB              |
| 10'000        | 265.3 ms     | 5.1 ms             | 255.2 ms - 276.9 ms  | 100            | 536 MB              |
| 1'000'000     | 341.4 ms     | 8.7 ms             | 326.0 ms - 373.4 ms  | 100            | 551 MB              |
| 100'000'000   | 834.5 ms     | 11.1 ms            | 813.2 ms - 884.5 ms  | 100            | 537 MB              |
| 1'000'000'000 | 5.485 s      | 30 ms              | 5.446 s -  5.532 s   | 10             | 14.4 GB             |
| 8'000'000'000 | 19.446 s     | 0.569 s            | 18.434 s - 20.636 s  | 10             | 42.3 GB             |


## Running

```bash
./compile.sh

./uniqueipcounter [path/to/the/input/file]
```


### References
- https://questdb.io/blog/billion-row-challenge-step-by-step/
- https://www.jbang.dev/documentation/guide/latest/index.html
- https://perf.wiki.kernel.org/index.php/Main_Page
- https://docs.oracle.com/javase/8/docs/technotes/guides/visualvm/
- https://github.com/async-profiler/async-profiler
- https://questdb.io/blog/1brc-merykittys-magic-swar/
- http://0x80.pl/notesen/2023-04-09-faster-parse-ipv4.html#scalar-conversion
- https://lemire.me/blog/2023/06/08/parsing-ip-addresses-crazily-fast/
