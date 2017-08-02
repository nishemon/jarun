# -*- coding: utf-8 -*-

import os
import sys
import urllib
from logging import getLogger
from xml.etree import ElementTree

import Java

logger = getLogger(__name__)


def read_online_xml(url):
    logger.info("[TRY] %s", url)
    try:
        urlobj = urllib.urlopen(url)
        if urlobj.getcode() == 200:
            return ElementTree.parse(urlobj)
    except:
        pass
    return None


class DownloadStatus(object):
    """
    Show download status to console.
    """
    def __init__(self):
        self.size = 0
        self.bar = None
        self.total = sys.maxint

    def update(self, size, total):
        if not self.bar:
            if total != -1:
                self.total = total
        self.size += size
        sys.stdout.write("Download: %s / %s\r" % (min(self.size, self.total), self.total))

    def finish(self):
        sys.stdout.write("\n")


def download_package(repos, org, name, save, verstr=None):
    for r in repos:
        base = '/'.join([r.rstrip('/'), org.replace('.', '/'), name])
        maven = base + '/maven-metadata.xml'
        tree = read_online_xml(maven)
        if not tree:
            continue
        logger.info("[GOT] %s", maven)
        versioning = tree.find('versioning')
        if not verstr:
            verstr = versioning.find('release').text
        for v in versioning.find('versions').findall('version'):
            if v.text == verstr:
                jarname = '%s-%s.jar' % (name, verstr)
                jarurl = '/'.join([base, verstr, jarname])
                logger.info("[FOUND] %s %s in %s", name, verstr, jarurl)
                jarpath = os.path.join(save, jarname)
                stat = DownloadStatus()
                urllib.urlretrieve(jarurl, jarpath, lambda cnt, size, total: stat.update(size, total))
                stat.finish()
                return jarpath
    return None


def find_cmds(paths, name):
    return [b for b in [os.path.join(p, name) for p in paths] if os.access(b, os.X_OK)]


def find_javas():
    paths = []
    jh = os.getenv('JAVA_HOME')
    if jh and os.path.exists(jh):
        paths.append(jh)
    paths.extend(os.getenv('PATH').split(':'))
    javabins = find_cmds(paths, 'java')
    return javabins


def mkdirs(dirpath):
    if not os.path.exists(dirpath):
        os.makedirs(dirpath)


def new_sys_java(conf):
    rootdir = conf.workdir
    libdir = os.path.join(rootdir, 'lib')
    java = Java.Java(conf)
    java.chdir(rootdir)
    java.add_syspath(libdir)
    return java
