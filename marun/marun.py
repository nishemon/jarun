#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import logging
import os
import shutil

import Conf
import Consts

parser = argparse.ArgumentParser(prog='marun')
parser.add_argument('-r', '--root', help='directory')
parser.add_argument('-v', '--verbose', help='vebose', action='count', default=0)
parser.add_argument('-q', '--quiet', help='quiet', action='count', default=0)
subparsers = parser.add_subparsers()

import sub_init

sub_init.setup_subcmd(subparsers)

import sub_install

sub_install.setup_subcmd(subparsers)

import sub_run

sub_run.setup_subcmd(subparsers)

packagedir = os.path.dirname(os.path.abspath(__file__))

def main():
    gconffile = os.environ.get(Consts.ENV_CONF_FILE, Consts.DEFAULT_CONF_FILE)
    conf = Conf.CoreConf([gconffile])
    if not conf.isValid():
        print("Any config files is not found.")
        print("Now, generating '%s'. Edit it." % Consts.DEFAULT_CONF_FILE)
        shutil.copy(os.path.join(packagedir, Consts.CONF_FILE_BASE_FILE), Consts.DEFAULT_CONF_FILE)
        return
    args = parser.parse_args()
    volume = args.verbose - args.quiet
    if volume == 0:
        logging.basicConfig(level=logging.CRITICAL)
    elif volume == 1:
        logging.basicConfig(level=logging.WARNING)
    elif 2 <= volume:
        logging.basicConfig(level=logging.DEBUG)
    # TODO check init and do
    (b, msg) = args.handler(conf, args)

    if not b:
        print msg


if __name__ == '__main__':
    main()
