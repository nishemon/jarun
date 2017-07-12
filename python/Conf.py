#!/usr/bin/env python
# coding:UTF-8

import os

import ConfigParser

MAIN_SECTION='marun'


class RepositoryConf:
	def __init__(self, parser, repo):
		section = 'repository:' + repo
		self.name = ''
		self.baseurl = ''
		self.type = 'maven'
		self.__dict__.update(dict(parser.items(section)))
	def toDict(self):
		return { 'name': self.name, 'baseurl': self.baseurl, 'type': self.type }


class CoreConf:
	def __init__(self, conffiles):
		parser = ConfigParser.ConfigParser()
		self.jvm = None
		self.repositories = ''
		for cf in conffiles:
			if not os.path.exists(cf):
				pass # todo
			parser.read(cf)
			self.__dict__.update(dict(parser.items(MAIN_SECTION)))
		repos = []
		for reponame in self.repositories.split(','):
			repos.append(RepositoryConf(parser, reponame))
		self.repositories = repos

	def toDict(self):
		return {
			'workdir': self.workdir,
			'repositories': [x.toDict() for x in self.repositories]
		}

