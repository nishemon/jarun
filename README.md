# marun - Maven Artifact Runner
* get jar files and their dependencies from maven repositories
* run easily

Marun is a support tool install and run java programs from maven repositories.
It has no capability to compile, archive and do other build commands unlike Apache Maven or Gradle, but it can read pom.xml and resolve dependencies using Apache Ivy.

## usage
1. install marun
```
> pip install marun
```

2. install a jar (gradle short format)
```
> marun install org.apache.commons:commons-compress:+
```

3. run
```
> marun run org.apache.commons.compress.archivers.sevenz.CLI
```

## configuration
It is expected that you have some private maven repository.
Use Amazon S3(e.g. [aws-maven](https://github.com/spring-projects/aws-maven)), [Nexus](http://www.sonatype.org/nexus/), [Artifactory](https://www.jfrog.com/artifactory/) or a http server.

```
#/etc/marun.conf
...
repositories=yours,jcenter

[repository:yours]
...
```

## requirements
* Java8
* Python 2.7

## limitation
This is WIP.

