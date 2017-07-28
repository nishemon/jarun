# marun - Maven Artifact Runner

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
Use Amazon S3, Nexus, Artifactory or a http server.

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

## internal
Marun is based on Apache Ivy.

