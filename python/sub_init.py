import os

import util

def init(conf, args):
        rootdir = conf.workdir
        libdir = os.path.join(rootdir, 'lib')
        util.mkdirs(libdir)
        util.getsysjars(conf.repositories, libdir)
        sysjava = util.new_sys_java(conf)
        code,output = sysjava.sysRun(['Health'], conf.toDict())
        if code == 0:
                print output
        return (True, None)

def setup_subcmd(subparsers):
	init_parser = subparsers.add_parser('init', help='Setup firstly.')
	init_parser.set_defaults(handler=init)

