# -*- coding: utf-8 -*-

import os
import shutil
import time
import ConfigParser

import Consts
import util


class StaticRepository(object):
    def __init__(self, url):
        self.name = url
        self.baseurl = url

    def to_dict(self):
        return {
            'baseurl': self.baseurl,
            'type': 'maven',
            'name': self.name,
        }


class RepositoryConf(object):
    def __init__(self, parser, repo):
        section = 'repository:' + repo
        self.name = repo
        self.baseurl = None
        self.type = 'maven'
        self.accesskey = None
        self.secretkey = None
        if parser:
            self.__dict__.update(dict(parser.items(section)))

    def to_dict(self):
        return {
            'baseurl': self.baseurl,
            'type': self.type,
            'name': self.name,
            'accessKey': self.accesskey,
            'secretKey': self.secretkey,
        }


class AppConf(object):
    def __init__(self, conf):
        self.install = []

    def add_install(self, artifact):
        self.install.append(artifact)

    def to_dict(self):
        return {
            'install': self.install
        }


class CoreConf(object):
    def __init__(self, conffiles):
        parser = ConfigParser.ConfigParser()
        self.jvm = None
        self.repositories = ''
        self.jardirname = 'lib'
        self.hardlink = True
        self.symboliclink = False
        self.flavors = ''
        self.flavordir = 'flavors'
        self.found_conffiles = bool(conffiles)
        self.workdir = '/var/lib/marun'
        for cf in conffiles:
            if not os.path.exists(cf):
                continue
            parser.read(cf)
            self.__dict__.update(dict(parser.items(Consts.CONF_MAIN_SECTION)))
            self.valid = True
        repos = []
        for reponame in self.repositories.split(','):
            if parser.has_section('repository:' + reponame):
                repos.append(RepositoryConf(parser, reponame))
            elif Consts.SPECIAL_REPOSITORIES.get(reponame, False):
                repos.append(RepositoryConf(None, reponame))
        self.repository_list = repos
        self.parser = parser

    def toappconf(self):
        return AppConf(self)

    def get_flavor_conf(self, flavor, default=None):
        section = 'flavor:' + flavor
        if self.parser.has_section(section):
            return dict(self.parser.items(section))
        return default

    def to_dict(self):
        return {
            'workdir': os.path.abspath(self.workdir),
            'repositories': [x.to_dict() for x in self.repository_list]
        }

    def isValid(self):
        return self.valid
