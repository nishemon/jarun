# marun - Maven Artifact Runner

## usage
1. install marun
 > pip install marun
or
 > git clone 
 > python setup.py

2. download system jar files
 > marun init

3. install a sample jar
 > mkdir sample; cd sample
 > marun install jp.cccis.marun:sample:+

## configuration
It is expected that you have some private maven repository.
Use Amazon S3, Nexus, Artifactory or a http server.

 #/etc/marun.conf
 ...
 repositories=yours,jcenter
 
 [repository:yours]

## requirements
* Java8
* Python 2.7

## limitation
Now on development.

## internal
Marun is based on Apache Ivy.

