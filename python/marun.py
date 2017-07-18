#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import argparse

import Conf
import Consts


parser = argparse.ArgumentParser(prog='marun')
#parser.add_argument('-u', '--update', help='update befor run.')
#parser.add_argument('-f', '--flavors', help='append flavors', action='append')
#parser.add_argument('-v', '--verbose', help='vebose', action='count')
#parser.add_argument('-J', '--javaarg', help='Java parameter. add raw options to java cmd.')
subparsers = parser.add_subparsers()

import sub_init
sub_init.setup_subcmd(subparsers)

import sub_install
sub_install.setup_subcmd(subparsers)

import sub_run
sub_run.setup_subcmd(subparsers)

def main():
	gconffile = os.environ.get(Consts.ENV_CONF_FILE, Consts.DEFAULT_CONF_FILE)
	conf = Conf.CoreConf([gconffile])
	args = parser.parse_args()
	(b,msg) = args.handler(conf, args)
	if not b:
		print msg

if __name__ == '__main__':
	main()

