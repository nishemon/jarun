# -*- coding: utf-8 -*-
# General
UTF8 = 'UTF-8'

# Script
ENV_CONF_FILE = 'MARUN_CONF_FILE'
ENV_MARUN_JAVA = 'MARUN_JAVA'

DEFAULT_CONF_FILE = '/etc/marun.conf'
CONF_MAIN_SECTION = 'marun'
DEFAULT_WORKDIR = '/var/lib/marun'
CONF_FILE_BASE_FILE = 'marun.conf.example'

# App
APP_STATUS_FILE = 'marun.json'

# Java
JAVA_CLI_PACKAGE = 'jp.cccis.marun.cli'

# Repository
INIT_REPOSITORY_URLS = ['https://jcenter.bintray.com/', 'http://maven.cccis.jp.s3.amazonaws.com/release']
SPECIAL_REPOSITORIES = {'bintray': True, 'jcenter': True, 'central': True}

SYS_JARS = [
    ('jp.cccis.marun', 'marun', '0.1.+'),
    ('org.apache.ivy', 'ivy', '2.4.+'),
    ('com.google.code.gson', 'gson', '2.8.+'),
]

S3_ADDONS = frozenset([
    'com.amazonaws:aws-java-sdk-s3:1.11.+'
])
