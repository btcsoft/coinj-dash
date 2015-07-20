### Welcome to coinj-dash

This is implementation of Coinj project inner API [coinj](https://github.com/btcsoft/coinj) for [DASH](https://dashpay.io) crypto-currency. 

It may be used as a standalone library to access DASH network and managing your own wallet. Coinj is a fork of Bitcoinj project therefore this library offers exactly the same features.

Or it may be used in conjunction with another implementation of Coinj inner API (e.g. [coinj-litecoin](https://github.com/btcsoft/coinj-litecoin)) to implement cross-network applications.  

Either way defining features of a whole Coinj stack are easy, clean and standardized alt-coin Java libraries development and crypto-coin cross-network development.

Contains experimental modes of networking, including full or partial Masternodes network syncing with full or partial IntantX technology support.  

### Technologies

* Java 6 for the core modules, Java 7 for everything else
* [Maven 3+](http://maven.apache.org) - for building the project
* [Orchid](https://github.com/subgraph/Orchid) - for secure communications over [TOR](https://www.torproject.org)
* [Google Protocol Buffers](https://code.google.com/p/protobuf/) - for use with serialization and hardware communications
* [BitcoinJ](https://github.com/bitcoinj/bitcoinj) - upstream library, inner API of which was made alt-coins friendly and less static constants oriented
* [concurrentlinkedhashmap](https://github.com/ben-manes/concurrentlinkedhashmap) - A high performance version of ```java.util.LinkedHashMap``` for use as a software cache.