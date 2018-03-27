# -*- coding: utf-8 -*-

import shutil
import os

import App
import Conf
import Consts
import util
import sub_install


def _init_sys(conf, syspod):
    sysbuilder = syspod.new_context_builder([':'.join(Consts.SYS_JARS[0])])
    try:
        for j in Consts.SYS_JARS:
            pv = util.download_package(Consts.INIT_REPOSITORY_URLS, j[0], j[1], sysbuilder.get_working(), j[2] if 2 < len(j) else None)
            if pv:
                sysbuilder.add_direct(':'.join(j[0:2]), pv[0], pv[1])
        sysjava = util.new_sys_java(conf, sysbuilder.get_working())
        code, output = sysjava.sys_run(['Health'], conf.to_dict())
        if code != 0:
            raise Exception("Fail to execute marun java library.\n" + str(output))
    except:
        sysbuilder.revert()
        raise
    return sysbuilder.commit(None, None)


def init(conf, force=False):
    javas = util.find_javas()
    if len(javas) == 0:
        return False, "Command \"java\" is not found. Set JAVA_HOME/PATH/MARUN_JAVA or config file."
    rootdir = conf.workdir
    if force:
        shutil.rmtree(rootdir, True)
    util.mkdirs(rootdir)
    syspod = App.AppPod(conf, rootdir)
    sysctx = syspod.get_current_context()
    if not sysctx:
        sysctx = _init_sys(conf, syspod)
    if [x for x in conf.repository_list if x.baseurl and x.baseurl.startswith('s3:')]:
        artifacts = sysctx.get_installs()
        if not Consts.S3_ADDONS <= artifacts:
            artifacts = artifacts | Consts.S3_ADDONS
            backup = conf.repository_list
            conf.repository_list = map(Conf.StaticRepository, Consts.INIT_REPOSITORY_URLS)
            sub_install.install_to_pod(conf, syspod, artifacts)
            conf.repository_list = backup
    return True, None


def _init(conf, args):
    return init(conf, args.force)


def setup_subcmd(subparsers):
    init_parser = subparsers.add_parser('init', help='Initialize')
    init_parser.add_argument('-f', '--force', help='remove all', action='store_true')
    init_parser.set_defaults(handler=_init, init=True)
