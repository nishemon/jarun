# -*- coding: utf-8 -*-

import shutil
import os

import Consts
import util


# TODO atomic install
def init(conf, args):
    javas = util.find_javas()
    if len(javas) == 0:
        return (False, "Command \"java\" is not found. Set JAVA_HOME/PATH/MARUN_JAVA or config file.")
    rootdir = conf.workdir
    if args.clear:
        shutil.rmtree(rootdir, True)
    libdir = os.path.join(rootdir, 'lib')
    util.mkdirs(libdir)
    util.download_package(Consts.INIT_REPOSITORY_URLS, 'org.apache.ivy', 'ivy', libdir)
    util.download_package(Consts.INIT_REPOSITORY_URLS, 'com.google.code.gson', 'gson', libdir)
    util.download_package(Consts.INIT_REPOSITORY_URLS, 'jp.cccis.marun', 'marun', libdir)
    sysjava = util.new_sys_java(conf)
    code, output = sysjava.sysRun(['Health'], conf.toDict())
    if code != 0:
        return (False, "Fail to execute marun java library.\n" + output)
    return (True, None)


def setup_subcmd(subparsers):
    init_parser = subparsers.add_parser('init', help='Initialize')
    init_parser.add_argument('-c', '--clear', help='clear cache', action='store_true')
    init_parser.set_defaults(handler=init)
