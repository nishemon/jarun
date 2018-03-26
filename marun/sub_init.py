# -*- coding: utf-8 -*-

import shutil
import os

import Consts
import util


def init(conf, force=False):
    javas = util.find_javas()
    if len(javas) == 0:
        return False, "Command \"java\" is not found. Set JAVA_HOME/PATH/MARUN_JAVA or config file."
    rootdir = conf.workdir
    if force:
        shutil.rmtree(rootdir, True)
    libdir = os.path.join(rootdir, 'lib')
    util.mkdirs(libdir)
    jars = [x for x in os.listdir(libdir) if x.endswith('.jar')]
    for j in Consts.SYS_JARS:
        named = [x for x in jars if x.startswith(j[1] + '-')]
        if named:
            if len(j) == 2:
                continue
            if [x for x in named if x.startswith(j[1] + '-' + j[2])]:
                continue
        p = util.download_package(Consts.INIT_REPOSITORY_URLS, j[0], j[1], libdir, j[2] if 2 < len(j) else None)
        if p:
            map(os.remove, [os.path.join(libdir, x) for x in named if x != p])
    sysjava = util.new_sys_java(conf)
    code, output = sysjava.sys_run(['Health'], conf.to_dict())
    if code != 0:
        return False, "Fail to execute marun java library.\n" + output
    return True, None


def _init(conf, args):
    return init(conf, args.clear)


def setup_subcmd(subparsers):
    init_parser = subparsers.add_parser('init', help='Initialize')
    init_parser.add_argument('-c', '--clear', help='clear cache', action='store_true')
    init_parser.set_defaults(handler=_init, init=True)
