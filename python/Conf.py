#!/usr/bin/env python
# coding:UTF-8

import os
import shutil
import time
import ConfigParser

import Consts
import util

class RepositoryConf:
	def __init__(self, parser, repo):
		section = 'repository:' + repo
		self.name = ''
		self.baseurl = ''
		self.type = 'maven'
		self.__dict__.update(dict(parser.items(section)))
	def toDict(self):
		return { 'name': self.name, 'baseurl': self.baseurl, 'type': self.type }

class AppConf:
	def __init__(self, conf):
		self.install = []

	def add_install(self, artifact):
		self.install.append(artifact)
	def toDict(self):
		return {
			'install': self.install
		}

class CoreConf:
	def __init__(self, conffiles):
		parser = ConfigParser.ConfigParser()
		self.jvm = None
		self.repositories = ''
		self.jardirname = 'lib'
		self.hardlink = True
		self.symboliclink = False
		for cf in conffiles:
			if not os.path.exists(cf):
				pass # todo
			parser.read(cf)
			self.__dict__.update(dict(parser.items(Consts.CONF_MAIN_SECTION)))
		repos = []
		for reponame in self.repositories.split(','):
			repos.append(RepositoryConf(parser, reponame))
		self.repositories = repos
		self.parser = parser
	def toappconf(self):
		return AppConf(self)
	def get_flavor_conf(self, flavor, default):
		section = 'flavor:' + flavor
		if self.parser.has_section(section):
			return self.parser.items(section)
		return default
	def toDict(self):
		return {
			'workdir': self.workdir,
			'repositories': [x.toDict() for x in self.repositories]
		}

