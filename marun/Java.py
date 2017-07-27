#!/usr/bin/env python
# -*- coding: utf-8 -*-

import subprocess
import json
import sys

import util
import Consts

_XKEY_VALUE_FLAGS = [ 'Xmx', 'Xms', 'Xss' ]

"""
 unified XX flags and KeyValue type options
 TODO classpath
"""
def _merge_options(jvmflags, strargs):
	baseflags = jvmflags[:]
	if strargs:
		xxs = []
		options = []
		for x in strargs.split():
			if not x:
				continue
			if x.startswith('-XX:'):
				xxs.append(x[4:])
				continue
			options.append(x)
		baseflags.append({ 'xx': ' '.join(xxs), 'options': ' '.join(flags) })
	options = {}
	xsizes = {}
	xxflags = {}
	xxoptions = {}
        for f in baseflags:
                for xx in f.get('xx', '').split():
                        if xx.startswith('+'):
                                xxflags[xx[1:]] = True
                        elif xx.startswith('-'):
                                xxflags[xx[1:]] = False
                        else:
                                kv = xx.split('=', 2)
                                xxoptions[kv[0]] = kv[1]
                for o in f.get('options', '').split():
			o = o.strip()
			if not o:
				continue
                        kv = o.split('=', 2)
                        if len(kv) == 2:
				options[kv[0][1:]] = kv[1]
				continue
			is_xsize = False
			for xkv in _XKEY_VALUE_FLAGS:
				if o[1:].startswith(xkv):
					xsizes[xkv] = o[4:]
					is_xsize = True
					break
			if not is_xsize:
                        	options[o[1:]] = True
        applys = [('-' + o) if v == True else '-%s=%s' % (o, v) for o, v in options.iteritems()]
	applys.extend(['-%s%s' % (k, v) for k, v in xsizes.iteritems()])
        applys.extend(['-XX:%s=%s' % (xx, v) for xx, v in xxoptions.iteritems()])
        applys.extend(['-XX:%s%s' % ('+' if f else '-', xx) for xx, f in xxflags.iteritems()])
	return applys

class Java:
	def __init__(self, conf):
		self.javabin = conf.jvm or util.find_javas()[0]
		self.is32 = True
		self.rundir = '.'
		self.syspath = []

	def runClass(self, classpaths, flavorargs, javaargstr, clazz, cmds):
		args = [self.javabin, '-classpath', ':'.join(classpaths)]
		args.extend(_merge_options(flavorargs, javaargstr))
		args.append(clazz)
		args.extend(cmds)
		print args
		subprocess.call(args)

	def chdir(self, d):
		self.rundir = d

	def add_syspath(self, classpath, isJarDir=True):
		if isJarDir:
			self.syspath.append(classpath + '/*')
		else:
			self.syspath.append(classpath)

	def sysRun(self, args, inconfs):
		cmds = [self.javabin, '-classpath', ':'.join(self.syspath)]
		if isinstance(args, list):
			start = len(cmds)
			cmds.extend(args)
			cmds[start] = Consts.JAVA_CLI_PACKAGE + '.' + cmds[start]
		else:
			cmds.append(Consts.JAVA_CLI_PACKAGE + '.' + args)
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

