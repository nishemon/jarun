# -*- coding: utf-8 -*-

import os
import shutil
import time
import json
import codecs
import tempfile

import Consts

ATTR_INSTALL = 'install'
ATTR_DEPENDENCIES = 'dependencies'
ATTR_MAINS = 'mains'
ATTR_CONTEXT = 'context'
ATTR_JARDIR = 'jardir'
ATTR_RUNNABLE = 'runnable'
ATTR_DEP_NAME = 'name'
ATTR_DEP_REVISION = 'revision'

class AppPod(object):
    def __init__(self, conf, root='.'):
        self.conf = conf
        self.status = {ATTR_CONTEXT: []}
        self.last = None
        self.root = os.path.abspath(root)
        self.status_file = os.path.join(self.root, Consts.APP_STATUS_FILE)
        if not os.path.exists(self.status_file):
            return
        with codecs.open(self.status_file, encoding=Consts.UTF8) as f:
            self.status = json.load(f)
        self.last = str(max([int(x) for x in self.status[ATTR_CONTEXT]]))

    def new_context_builder(self, installs):
        return _AppContextBuilder(self, installs)

    def update(self, newid, context, jardir, keepold=False):
        self.status[ATTR_CONTEXT].append(newid)
        self.status[str(newid)] = context
        if self.last and jardir:
            laststat = self.status[self.last]
            curdir = os.path.join(self.root, laststat.get(ATTR_JARDIR, self.conf.jardirname))
            if laststat.get(ATTR_RUNNABLE, False) or keepold:
                olddir = "%s.%s" % (curdir, self.last)
                self.status[self.last][ATTR_JARDIR] = os.path.basename(olddir)
                os.rename(curdir, olddir)
            else:
                if os.path.exists(curdir):
                    shutil.rmtree(curdir)
                self.status.pop(self.last)
                self.status[ATTR_CONTEXT].remove(int(self.last))
                # TODO: remove oldest / remove if lib is deleted
        libdir = os.path.join(self.root, self.conf.jardirname)
        # XXX: workaround in WSL?
        time.sleep(1)
        os.rename(jardir, libdir)
        tmpfile = self.status_file + "." + str(newid)
        with codecs.open(tmpfile, 'w', Consts.UTF8) as f:
            json.dump(self.status, f, ensure_ascii=False, indent=4)
        os.rename(tmpfile, self.status_file)
        self.last = str(newid)
        return _AppContext(self, context)

    def get_current_context(self):
        if self.last:
            return _AppContext(self, self.status[self.last])
        return None


class _AppContext(object):
    def __init__(self, repository, values):
        # type: (AppPod, dict) -> None
        self.repository = repository
        self.jars = values.get(ATTR_DEPENDENCIES, {})
        self.jardir = repository.conf.jardirname
        self.resourcedir = 'resources'
        self.installs = values[ATTR_INSTALL]
        self.mains = values.get(ATTR_MAINS, {})
        self.lack_jars = None
        self.uncontrols = []

    def _check_jars(self):
        if self.lack_jars is not None:
            return
        need_jarnames = dict([(self.jars[x][ATTR_DEP_NAME], x) for x in self.jars])
        if os.path.isdir(self.jardir):
            self.uncontrols = [x for x in os.listdir(self.jardir) if
                               x.endswith(".jar") and not need_jarnames.pop(x, False)]
        self.lack_jars = need_jarnames

    def get_uncontrol_jars(self):
        self._check_jars()
        return self.uncontrols

    def get_lack_jars(self):
        self._check_jars()
        return self.lack_jars

    def get_jar_path(self, jarname):
        return os.path.join(self.jardir, jarname)

    def get_dependency_dict(self):
        return self.jars

    def set_runnable(self):
        pass

    def get_installs(self):
        return frozenset(self.installs)

    def get_mains(self):
        return self.mains

    def get_jardir(self):
        return self.jardir

    def get_resourcedir(self):
        return self.resourcedir


class _AppContextBuilder(object):
    def __init__(self, repository, installs):
        self.repository = repository
        self.conf = repository.conf
        self.installs = [x for x in installs]
        self.id = int(time.time())
        self.dependencies = {}
        check = os.path.join(repository.root, "%s.%d" % (self.conf.jardirname, self.id))
        try:
            os.mkdir(check)
            self.tempdir = check
        except OSError:
            self.tempdir = tempfile.mkdtemp(prefix=self.conf.jardirname + "-", dir=repository.root)

    def _add(self, jarpath, hard=None):
        name = os.path.basename(jarpath)
        target = os.path.join(self.tempdir, name)
        if hard or (hard is None and self.conf.hardlink):
            try:
                os.link(jarpath, target)
                return name
            except OSError:
                pass
        if self.conf.symboliclink:
            os.symlink(jarpath, target)
        else:
            shutil.copyfile(jarpath, target)
        return name

    def add(self, modid, jarpath, attr, hard=None):
        name = self._add(jarpath, hard)
        attr[ATTR_DEP_NAME] = name
        self.dependencies[modid] = attr

    def add_direct(self, modid, jarname, revision):
        self.dependencies[modid] = {
            ATTR_DEP_NAME: jarname,
            ATTR_DEP_REVISION: revision,
        }

    def get_working(self):
        return self.tempdir

    def commit(self, mains, resources, keepold=False):
        return self.repository.update(self.id, {
            ATTR_INSTALL: self.installs,
            ATTR_DEPENDENCIES: self.dependencies,
            ATTR_MAINS: mains,
        }, self.tempdir, keepold=keepold)

    def revert(self):
        shutil.rmtree(self.tempdir)
