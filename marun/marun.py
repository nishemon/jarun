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

def setup_conffile(path):
    file_template = """
[marun]
# maven repositories in order
repositories=%s,bintray,central

workdir=/var/lib/marun
cachedir=/var/cache/marun/ivy

%s

[flavor:thp]
XX=+UseTransparentHugePages +AlwaysPreTouch
"""
    repository_template = """
[repository:%s]
baseurl=%s
%s

"""
    url = raw_input("Your Maven Repository URL []:")
    name = ''
    repo = ''
    if url:
        append = ""
        if url.startswith('s3://'):
            accessKey = raw_input("S3 Access Key []:")
            secretKey = raw_input("S3 Secret Key []:")
            append = "accessKey=%s\nsecretKey=%s" % (accessKey, secretKey)
        name = raw_input("Your Maven Repository Name [private]:")
        name = name or 'private'
        repo = repository_template % (name, url, append)
    contents = file_template % ((name + ',') if name else '', repo)
    with open(path, 'w') as f:
        f.write(contents)
    print("Wrote new configuration to '%s'" % path)


def main():
    gconffile = os.environ.get(Consts.ENV_CONF_FILE, Consts.DEFAULT_CONF_FILE)
    if not gconffile or not os.path.exists(gconffile):
        print("configuration file is not found!")
        if os.getuid() == 0 or os.access(os.path.dirname(gconffile), os.W_OK):
            setup_conffile(gconffile)
        else:
            print("set env '%s' for user or run as super user" % Consts.ENV_CONF_FILE)
            exit(-1)
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
    if (not 'init' in args) or (not args.init):
        sub_init.init(conf)
    (b, msg) = args.handler(conf, args)

    if not b:
        print msg


if __name__ == '__main__':
    main()
