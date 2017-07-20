#!/usr/bin/env python
# -*- coding: utf-8 -*-

import subprocess
import json
import sys

import util
import Consts

class Java:
	def __init__(self, conf):
		self.javabin = conf.jvm or util.find_javas()[0]
		self.is32 = True
		self.rundir = '.'
		self.classpath = []

	def runAs32(self, cmds):
		if not self.is32:
			pass
		args = [self.javabin, '-d32']
		print args
#		subprocess.call(self.javabin)

	def chdir(self, d):
		self.rundir = d

	def add_classpath(self, classpath, isJarDir=True):
		if isJarDir:
			self.classpath.append(classpath + '/*')
		else:
			self.classpath.append(classpath)

	def sysRun(self, args, inconfs):
		cmds = [self.javabin, '-Djavacpp.platform=linux-x86_64' , '-classpath', ':'.join(self.classpath)]
		if isinstance(args, list):
			start = len(cmds)
			cmds.extend(args)
			cmds[start] = Consts.JAVA_CLI_PACKAGE + '.' + cmds[start]
		else:
			cmds.append(Consts.JAVA_CLI_PACKAGE + '.' + args)
		print json.dumps(inconfs)
		proc = subprocess.Popen(cmds,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=sys.stdout)
		stdout,_ = proc.communicate(json.dumps(inconfs))
		return (proc.poll(), json.loads(stdout) if stdout else {})

def find_cmd(paths, name):
	return [b for b in [os.path.join(p, name) for p in paths] if os.access(b, os.X_OK)]

def find_java():
	javabins = []
	javabins.extend(find_cmd(os.getenv('PATH').split(':'), 'java'))
	return javabins

#def create_helper_env():
#	os.geteuid()

