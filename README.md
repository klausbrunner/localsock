localsock
=========

An experiment to transparently "shortcut" localhost TCP connections within the same JVM.

The idea is to detect if both sides of a TCP connection are within the same JVM and use simple intra-memory copies instead of passing stuff up and down the OS TCP/IP stack. Preferably without changing a single line in application code, so it would work fine with an off-the-shelf application server. All this without relying too much on JDK or even OS internals, so it should work on any standard Java runtime.

Status
------

Pre-alpha, a.k.a. "this might turn out to be a silly idea after all".

*As of this writing, it's just a proof of concept. There are known bugs (leaks, mostly) and probably many unknown ones.*

Requirements
------------

* Java 7 to run
* Maven 3 to build

Usage
-----

See the localsock-test project for usage info.
