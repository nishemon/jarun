import os
import urllib

from xml.etree import ElementTree
from logging import getLogger

logger = getLogger(__name__)

def getOnlineXML(url):
	logger.info('[TRY] ' + url)
	urlobj = urllib.urlopen(url)
	try:
		return ElementTree.parse(urlobj)
	except:
		return None

def downloadIvy(repos, path, verstr=None):
	for r in repos:
		base = r + '/org/apache/ivy/ivy/'
		maven = base + 'maven-metadata.xml'
		tree = getOnlineXML(maven)
		if not tree:
			continue
		logger.info('[GOT] ' + maven)
		versioning = tree.find('versioning')
		if not verstr:
			verstr = versioning.find('release').text
		for v in versioning.find('versions').findall('version'):
			if v.text == verstr:
				ivyjarurl = base + verstr + '/ivy-' + verstr + '.jar'
				logger.info('[FOUND] Ivy ' + verstr + ' in ' + ivyjarurl)
				ivyjarpath = os.path.join(path, 'ivy-' + verstr + '.jar')
				urllib.urlretrieve(ivyjarurl, ivyjarpath)
				return ivyjarpath
	return None

downloadIvy(['https://jcenter.bintray.com'], './')

