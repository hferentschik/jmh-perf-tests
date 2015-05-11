# JMH Performance for HSearch faceting

Performance test harness comparing the performance of Hibernate Search built-in faceting approach
using `FieldCache` and Lucene [dynamic faceting](http://blog.mikemccandless.com/2013/05/dynamic-faceting-with-lucene.html)
approach.

## How to build

    mvn clean install

## How to run

   java -jar target/benchmarks.jar