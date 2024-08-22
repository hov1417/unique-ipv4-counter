#!/usr/bin/env sh
javac UniqueIPCounter.java
native-image -H:+ForeignAPISupport UniqueIPCounter -O3
