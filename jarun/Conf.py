#!/usr/bin/env python
# coding:UTF-8

import ConfigParser

MAIN_SECTION='jboot'

class RepositoryConf:
	def __init__(self, parser, repo):
		self.baseurl = parser.get(repo, 'baseurl')
		self.name = parser.get(repo, 'name')

class CoreConf:
	def __init__(self, conffiles):
		parser = ConfigParser.ConfigParser()
		parser.read(conffiles)
		self.jvm = parser.get(MAIN_SECTION, 'jvm')
		self.jvm32 = parser.get(MAIN_SECTION, 'jvm32')
		self.jvm64 = parser.get(MAIN_SECTION, 'jvm64')
		self.workdir= parser.get(MAIN_SECTION, 'workdir')
		repos = []
		for reponame in parser.get('jboot', 'repositories').split(','):
			repos.append(RepositoryConf(parser, reponame))
		self.repository = repos

