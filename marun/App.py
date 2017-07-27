#!/usr/bin/env python
# coding:UTF-8

import os
import shutil
import time
import ConfigParser
import json
import codecs
import tempfile

import Consts
import util

ATTR_INSTALL='install'
ATTR_DEPENDENCIES='dependencies'
ATTR_MAINS='mains'
ATTR_CONTEXT='context'
ATTR_JARDIR='jardir'
ATTR_RUNNABLE='runnable'
ATTR_DEP_NAME='name'

class AppRepository:
	def __init__(self, conf):
		self.conf = conf
		self.status = { ATTR_CONTEXT: [] }
		self.last = None
		if not os.path.exists(Consts.APP_STATUS_FILE):
			return
		with codecs.open(Consts.APP_STATUS_FILE, encoding=Consts.UTF8) as f:
			self.status = json.load(f)
		self.last = str(max([int(x) for x in self.status[ATTR_CONTEXT]]))
	def new_context_builder(self, installs):
		return _AppContextBuilder(self, installs)
	def _update(self, newid, context, jardir, keepold=False):
		self.status[ATTR_CONTEXT].append(newid)
		self.status[str(newid)] = context
		if self.last and jardir:
			laststat = self.status[self.last]
			curdir = laststat.get(ATTR_JARDIR, self.conf.jardirname)
			if laststat.get(ATTR_RUNNABLE, False) or keepold:
				olddir = "%s.%s" % (curdir, self.last)
				self.status[self.last][ATTR_JARDIR] = olddir
				os.rename(curdir, olddir)
			else:
				if os.path.exists(curdir):
					shutil.rmtree(curdir)
				self.status.pop(self.last)
				self.status[ATTR_CONTEXT].remove(int(self.last))
			# TODO: remove oldest / remove if lib is deleted
		os.rename(jardir, self.conf.jardirname)
		tmpfile = Consts.APP_STATUS_FILE + "." + str(newid)
		with codecs.open(tmpfile, 'w', Consts.UTF8) as f:
			json.dump(self.status,  f, ensure_ascii=False, indent=4)
		os.rename(tmpfile, Consts.APP_STATUS_FILE)
	def get_current_context(self):
		if self.last:
			return _AppContext(self, self.status[self.last])
		return None

class _AppContext:
	def __init__(self, repository, values):
		self.repository = repository
		self.jars = values.get(ATTR_DEPENDENCIES, {})
		self.jardir = repository.conf.jardirname
		self.installs = values[ATTR_INSTALL]
		self.mains = values.get(ATTR_MAINS, {})
	def _check_jars(self):
		need_jarnames = dict([(self.jars[x][ATTR_DEP_NAME], x) for x in self.jars])
		if not os.path.isdir(self.jardir):
			self.uncontrols = []
		else:
			self.uncontrols = [x for x in os.listdir(self.jardir) if x.endswith(".jar") and not need_jarnames.pop(x, False)]
		self.luck_jars = need_jarnames
	def get_uncontrol_jars(self):
		self._check_jars()
		return self.uncontrols
	def get_luck_jars(self):
		self._check_jars()
		return self.luck_jars
	def get_jar_path(self, jarname):
		return os.path.join(self.jardir, jarname)
	def get_dependency_dict(self):
		return self.jars
	def set_runnable(self):
		pass
	def get_installs(self):
		return self.installs
	def get_mains(self):
		return self.mains
	def get_jardir(self):
		return self.jardir

class _AppContextBuilder:
	def __init__(self, repository, installs):
		self.repository = repository
		self.conf = repository.conf
		self.installs = installs
		self.id = int(time.time())
		self.dependency = {}
		check = "%s.%d" % (self.conf.jardirname, self.id)
		try:
			os.mkdir(check)
			self.tempdir = check
		except:
			self.tempdir = tempfile.mkdtemp(prefix=conf.jardirname + "-", dir=".")

	def _add(self, jarpath, hard=None):
		name = os.path.basename(jarpath)
		target = os.path.join(self.tempdir, name)
		if hard or (hard == None and self.conf.hardlink):
			try:
				os.link(jarpath, target)
				return name
			except:
				pass
		if self.conf.symboliclink:
			os.symlink(jarpath, target)
		else:
			shutil.copyfile(jarpath, target)
		return name

	def add(self, modId, jarpath, attr, hard=None):
		name = self._add(jarpath, hard)
		attr[ATTR_DEP_NAME] = name
		self.dependency[modId] = attr

	def commit(self, mains, resources, keepold=False):
		self.repository._update(self.id, {
			ATTR_INSTALL: self.installs,
			ATTR_DEPENDENCIES: self.dependency,
			ATTR_MAINS: mains,
		}, self.tempdir, keepold=keepold)
		print resources
		return True

	def revert(self):
		shutil.rmtree(self.tempdir)

