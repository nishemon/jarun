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


def download_package(repos, org, name, save='.', verstr=None):
    for r in repos:
        base = '/'.join([r.rstrip('/'), org.replace('.', '/'), name])
        maven = base + '/maven-metadata.xml'
        tree = read_online_xml(maven)
        if not tree:
            continue
        logger.info("[GOT] %s", maven)
        versioning = tree.find('versioning')
        release = versioning.find('release').text
        verstr = verstr and verstr.replace('+', '')
        if not verstr or release.startswith(verstr):
            verstr = release
        for v in versioning.find('versions').findall('version')[::-1]:
            if v.text.startswith(verstr):
                jarname = '%s-%s.jar' % (name, v.text)
                jarurl = '/'.join([base, v.text, jarname])
                logger.info("[FOUND] %s %s in %s", name, v.text, jarurl)
                jarpath = os.path.join(save, jarname)
                stat = DownloadStatus()
                urllib.urlretrieve(jarurl, jarpath, lambda cnt, size, total: stat.update(size, total))
                stat.finish()
                return jarname, v.text
    return None


def find_cmds(name, paths = None):
    search = paths
    if not paths:
        search = os.getenv('PATH').split(':')
    return [b for b in [os.path.join(p, name) for p in search] if os.access(b, os.X_OK)]


def find_javas():
    paths = []
    jh = os.getenv('JAVA_HOME')
    if jh and os.path.exists(jh):
        paths.append(jh)
    paths.extend(os.getenv('PATH').split(':'))
    javabins = find_cmds('java', paths)
    return javabins


def mkdirs(dirpath):
    if not os.path.exists(dirpath):
        os.makedirs(dirpath)


def new_sys_java(conf, libdir=None):
    rootdir = conf.workdir
    libdir = libdir or os.path.join(rootdir, 'lib')
    java = Java.Java(conf)
    java.chdir(rootdir)
    java.add_syspath(libdir)
    return java

