# -*- coding: utf-8 -*-
# General
UTF8 = 'UTF-8'

# Script
ENV_CONF_FILE = 'MARUN_CONF_FILE'
ENV_MARUN_JAVA = 'MARUN_JAVA'

DEFAULT_CONF_FILE = '/etc/marun.conf'
CONF_MAIN_SECTION = 'marun'

# App
APP_STATUS_FILE = 'marun.json'

# Java
JAVA_CLI_PACKAGE = 'jp.cccis.marun.cli'

# Repository
INIT_REPOSITORY_URLS = ['https://jcenter.bintray.com/', 'http://maven.cccis.jp.s3.amazonaws.com/release']
SPECIAL_REPOSITORIES = {'bintray': True, 'jcenter': True, 'central': True}

SYS_JARS = [
    ('org.apache.ivy', 'ivy', '2.4'),
    ('com.google.code.gson', 'gson', '2.8'),
    ('jp.cccis.marun', 'marun', '0.1'),
]
