import os
import urllib
import sys

from xml.etree import ElementTree
from logging import getLogger
from progressbar import DataTransferBar

import Java

logger = getLogger(__name__)

def getOnlineXML(url):
	logger.info('[TRY] %s', url)
	urlobj = urllib.urlopen(url)
	try:
		urlobj = urllib.urlopen(url)
		if urlobj.getcode() == 200:
			return ElementTree.parse(urlobj)
	except:
		pass
	return None

class DownloadBar:
	def __init__(self):
		self.size = 0
		self.bar = None

	def update(self, size, total):
		if not self.bar:
			if total == -1:
				self.bar = DataTransferBar(max_value=progressbar.UnknownLength)
				self.total = sys.maxint
			else:
				self.bar = DataTransferBar(max_value=total)
				self.total = total
			self.bar.start()
		self.size += size
		self.bar.update(min(self.size, self.total))

	def finish(self):
		self.bar.finish()

def downloadPackage(repos, org, name, save, verstr=None):
	for r in repos:
		base = '/'.join([r.rstrip('/'), org.replace('.', '/'), name])
		maven = base + '/maven-metadata.xml'
		tree = getOnlineXML(maven)
		if not tree:
			continue
		print maven
		logger.info('[GOT] %s', maven)
		versioning = tree.find('versioning')
		if not verstr:
			verstr = versioning.find('release').text
		for v in versioning.find('versions').findall('version'):
			if v.text == verstr:
				jarname = '%s-%s.jar' % (name, verstr)
				jarurl = '/'.join([base, verstr, jarname])
				logger.info('[FOUND] %s %s in %s', name, verstr, jarurl)
				jarpath = os.path.join(save, jarname)
				bar = DownloadBar()
				urllib.urlretrieve(jarurl, jarpath, lambda cnt, size, total:bar.update(size, total))
				bar.finish()
				return jarpath
	return None

def getsysjars(repos, dest):
	print repos
	repourls = [x.baseurl for x in repos if x.type == 'maven']
	downloadPackage(repourls, 'org.apache.ivy', 'ivy', dest)
	downloadPackage(repourls, 'com.google.code.gson', 'gson', dest)
	downloadPackage(['http://maven.cccis.jp.s3.amazonaws.com/release'], 'jp.cccis.marun', 'marun', dest)

def find_cmds(paths, name):
        return [b for b in [os.path.join(p, name) for p in paths] if os.access(b, os.X_OK)]

def find_javas():
        javabins = []
        javabins.extend(find_cmds(os.getenv('PATH').split(':'), 'java'))
        return javabins

def mkdirs(dirpath):
	if not os.path.exists(dirpath):
		os.makedirs(dirpath)


def new_sys_java(conf):
        rootdir = conf.workdir
        libdir = os.path.join(rootdir, 'lib')
        java = Java.Java(conf)
        java.chdir(rootdir)
        java.add_classpath(libdir)
        return java

