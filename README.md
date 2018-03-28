# marun - Maven Artifact Runner
* deploying jar files and their dependencies from maven repositories
* run easily

Marun is a tool to install and run Java programs from maven repositories.
It has no capability to compile, archive and do other build commands unlike Apache Maven or Gradle, but it can read pom.xml and resolve dependencies using Apache Ivy.

## usage
1. install marun
```
> sudo pip install marun
```

2. install a jar (example: jruby)
```
> sudo marun install org.jruby:jruby-core:1.7.+
```

3. run
```
> sudo marun run jruby.Main -v
```

## configuration
It is expected that you have some private maven repositories.
You can use Amazon S3(e.g. [aws-maven](https://github.com/spring-projects/aws-maven)), [Nexus](http://www.sonatype.org/nexus/), [Artifactory](https://www.jfrog.com/artifactory/) or a http server for your private repository.

```
#/etc/marun.conf
...
repositories=yours,jcenter

[repository:yours]
baseurl=http://...

...
```

## requirements
* Java8
* Python 2.7

