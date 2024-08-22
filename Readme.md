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
| GraalVM                               | Used graalvm-jdk-22.0.2+9.1 native image, as specified in .java-version                         |
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
to heavily optimize some specific cases, which increases branch-misses. (TODO test this with perf).

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
} else if (hashcode == 12595200) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == 14684160) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -2143277056) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -2141188096) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -1071640576) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | (((((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == 8405040) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 8413200) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 12591120) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == -2143281136) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 545275936) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 61440) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -2147454976) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -1073729536) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 100) + (((value2 >>> 24) & 0xFF) * 10) + ((value2 >>> 32) & 0xFF))));
} else if (hashcode == -536866816) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF)) << 8) | ((((value2 >>> 32) & 0xFF))));
} else if (hashcode == 32880) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 49200) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 57360) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 8388720) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 12582960) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == 14680080) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
} else if (hashcode == -2147467216) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == -2147459056) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == -2143289296) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 100) + (((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == -2141192176) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
} else if (hashcode == -1073733616) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | (((((value2 >>> 16) & 0xFF) * 10) + ((value2 >>> 24) & 0xFF))));
} else if (hashcode == -1071644656) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF)) << 8) | ((((value2 >>> 24) & 0xFF))));
} else if (hashcode == 1610645568) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 536903776) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 536920096) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 1619001408) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 545259616) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 1619017728) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 545284096) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == 549453856) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 549462016) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == -1610596320) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == -1606418400) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | (((((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == -1606410240) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == 1075871808) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 1084227648) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 1084243968) {Remove
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 10502176) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == -536838144) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 1610661888) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == 536928256) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == -536854528) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 100) + (((value2 >>> 8) & 0xFF) * 10) + ((value2 >>> 16) & 0xFF))));
} else if (hashcode == -1610588160) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == -536862720) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 16) | (((value2 & 0xFF)) << 8) | ((((value2 >>> 16) & 0xFF))));
} else if (hashcode == -2141159424) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == -1071611904) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 6324288) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 2130016) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == 1075888128) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 2146336) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == 14680128) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 100) + ((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == 10485856) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 100) + (((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == 14680096) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 100) + (((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == -1071628288) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | ((((value2 & 0xFF) * 10) + ((value2 >>> 8) & 0xFF))));
} else if (hashcode == -2145370080) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == -2141192160) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 16) | ((((value1 >>> 56) & 0xFF)) << 8) | ((((value2 >>> 8) & 0xFF))));
} else if (hashcode == -2143248384) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
} else if (hashcode == 4235328) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
} else if (hashcode == 1073782848) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == 12591168) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | (((((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
} else if (hashcode == 1082138688) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | ((((value1 >>> 24) & 0xFF)) << 16) | (((((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == 1082155008) {
    return (int) (((((value1 & 0xFF) * 10) + ((value1 >>> 8) & 0xFF)) << 24) | (((((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == -2147426304) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | ((((value1 >>> 32) & 0xFF)) << 8) | (((((value1 >>> 48) & 0xFF) * 100) + (((value1 >>> 56) & 0xFF) * 10) + (value2 & 0xFF))));
} else if (hashcode == -1073700864) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 100) + (((value1 >>> 40) & 0xFF) * 10) + ((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == 1073799168) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 100) + (((value1 >>> 24) & 0xFF) * 10) + ((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == -1073717248) {
    return (int) (((((value1 & 0xFF) * 100) + (((value1 >>> 8) & 0xFF) * 10) + ((value1 >>> 16) & 0xFF)) << 24) | ((((value1 >>> 32) & 0xFF)) << 16) | ((((value1 >>> 48) & 0xFF)) << 8) | (((value2 & 0xFF))));
} else if (hashcode == -2147434464) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | ((((value1 >>> 32) & 0xFF)) << 8) | (((((value1 >>> 48) & 0xFF) * 10) + ((value1 >>> 56) & 0xFF))));
} else if (hashcode == -2143256544) {
    return (int) ((((value1 & 0xFF)) << 24) | ((((value1 >>> 16) & 0xFF)) << 16) | (((((value1 >>> 32) & 0xFF) * 10) + ((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
} else if (hashcode == 4227168) {
    return (int) ((((value1 & 0xFF)) << 24) | (((((value1 >>> 16) & 0xFF) * 10) + ((value1 >>> 24) & 0xFF)) << 16) | ((((value1 >>> 40) & 0xFF)) << 8) | ((((value1 >>> 56) & 0xFF))));
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
which is faster for 1-2% then the first one.

Yet this would take me at least few days, and I'm already working on this for a week,
so I decided to stick to my implementation.

#### Work Stealing Execution
I tried using ForkJoinPool, which did not help. It's faster when code manages work stealing. 



## Results
Tested on Intel® Core™ i7-9750H CPU with 16GB RAM.

| Size          | Average Time | Standard Deviation | Range                | Number of Runs | Max Memory |
|---------------|--------------|--------------------|----------------------|----------------|------------|
| 10            | 225.1 ms     | 6.1 ms             | 219.6 ms - 252.9 ms  | 100            | 11 MB      |
| 10'000        | 246.4 ms     | 3.4 ms             | 241.5 ms - 256.5 ms  | 100            | 12 MB      |
| 1'000'000     | 501.8 ms     | 7.7 ms             | 492.7 ms - 533 ms    | 100            | 551 MB     |
| 100'000'000   | 904.0 ms     | 38.1 ms            | 812.9 ms - 1000.9 ms | 100            | 1.4 GB     |
| 1'000'000'000 | 8.833 s      | 532 ms             | 7.993 s -  9.560 s   | 10             | 10.6 GB    |
| 8'000'000'000 | 67.723 s     | 2.419 s            | 63.975 s - 72.091 s  | 10             | 13.1G BG   |


