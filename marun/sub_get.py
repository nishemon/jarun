# -*- coding: utf-8 -*-

import argparse

from logging import getLogger

import util

logger = getLogger(__name__)


def download(conf, args):
    """

    :param CoreConf conf:
    :param args:
    :return:
    """
    repositories = [x.baseurl for x in conf.repository_list if x.baseurl]
    for art in args.artifacts:
        v = art.split(':')
        util.download_package(repositories, v[0], v[1], ".", v[2])
    return (True, None)


def setup_subcmd(subparsers):
    get_parser = subparsers.add_parser('download', help="Get single artifact")
    get_parser.add_argument('artifacts', nargs='+')
    get_parser.set_defaults(handler=download)
