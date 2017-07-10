#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import subprocess
import json
import os

import Java
import Conf
import util

def create_helper_env():
	os.geteuid()

def new_sys_java(conf):
        rootdir = conf.workdir
        libdir = os.path.join(rootdir, 'lib')
        java = Java.Java(conf)
	java.chdir(rootdir)
        java.add_classpath(libdir)
	return java


parser = argparse.ArgumentParser(prog='jarun')
parser.add_argument('-u', '--update', help='update befor run.')
parser.add_argument('-f', '--flavors', help='append flavors', action='append')
parser.add_argument('-v', '--verbose', help='vebose', action='count')
parser.add_argument('-J', '--javaarg', help='Java parameter. add raw options to java cmd.')
subparsers = parser.add_subparsers()

def init(conf, args):
	rootdir = conf.workdir
	libdir = os.path.join(rootdir, 'lib')
	util.getsysjars(conf.repositories, libdir)
	sysjava = new_sys_java(conf)
	code,output = sysjava.sysRun(['Health'], conf.toDict())
	if code == 0:
		print json.dumps(output)

init_parser = subparsers.add_parser('init', help='Setup firstly.')
init_parser.set_defaults(handler=init)

def run(conf, args):
	java = Java.Java(conf)
	java.run(args)

run_parser = subparsers.add_parser('run')
run_parser.add_argument('javaarg', nargs=argparse.REMAINDER)
run_parser.set_defaults(handler=run)

def install(conf, args):
	sysjava = new_sys_java(conf)
	target = './lib'
	os.mkdir(target)
	code,output = sysjava.sysRun(['Install', target, 'runtime', args.artifact], conf.toDict())

install_parser = subparsers.add_parser('install', help='Download jars.')
install_parser.add_argument('artifact')
install_parser.set_defaults(handler=install)

def update(conf, args):
	pass

update_parser = subparsers.add_parser('update', help='Not yet implemented.')
#update_parser.add_argument('--minor', help='update minor version if pom.xml accept (default patch)')
#update_parser.add_argument('--ignore-pom')
update_parser.add_argument('artifact')
update_parser.set_defaults(handler=update)

def main():
	conf = Conf.CoreConf(['/etc/jarun.conf'])
	args = parser.parse_args()
	args.handler(conf, args)

if __name__ == '__main__':
	main()

