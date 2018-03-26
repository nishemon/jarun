# -*- coding: utf-8 -*-

import argparse

from logging import getLogger

import util

logger = getLogger(__name__)


def _find_main_class(directive, mainclasses):
    candidates = []
    dpath = directive.split('.')
    for c in mainclasses:
        if directive == c:
            return c
        path = c.split('.')
        if dpath[-1] != path[-1]:
            continue
        hit = True
        p = 0
        for d in range(len(dpath) - 1):
            cur = dpath[d]
            while p + 1 < len(path) and cur != path[p] and cur != path[p][0]:
                logger.debug("match: %s = %s" % (path[p], d))
                p = (p + 1)
            if p + 1 == len(path):
                hit = False
            p = (p + 1)
        if hit:
            candidates.append(c)
    if not candidates:
        return directive
    if 1 == len(candidates):
        return candidates[0]
    logger.critical("class parameter '%s' is ambiguous. [%s]", directive, ', '.join(candidates))
    return None


def download(conf, args):
    for art in args.artifacts:
        util.download_package(conf.get_repository_urls())
    fs = [x.strip() for x in conf.flavors.split(',')]
    if args.flavors:
        if args.flavors.startswith('@'):
            fs = [x.lstrip(' @+').strip() for x in args.flavors[1:].split(',')]
        else:
            fs.extend([x.lstrip(' @+').strip() for x in args.flavors.split(',')])
    jvmflags = []
    for f in fs:
        if f:
            jvmflags.extend(_apply_flavor(f, conf, jvmflags))
    java = Java.Java(conf)
    app = App.AppRepository(conf)
    context = app.get_current_context()
    main = _find_main_class(args.mainclass, context.get_mains())
    classpaths = [context.get_jardir() + '/*', context.get_resourcedir()]
    java.runClass(classpaths, jvmflags, args.jvmargs, main, args.classargs)
    return (True, None)


def setup_subcmd(subparsers):
    run_parser = subparsers.add_parser('download', help="Get single artifact")
    run_parser.add_argument('--flavors', help="add(+) or replace(@) flavors. ex) --flavors +fl1,fl2")
    run_parser.add_argument('-J', nargs='+', help="JVM argument.", dest='jvmargs')
    run_parser.add_argument('-D', nargs='+', help="JVM system property (pass-through JVM argument).", dest='declare')
    run_parser.add_argument('-n', action='store_true', help="dryrun: print java command")
    run_parser.add_argument('mainclass')
    run_parser.add_argument('classargs', nargs=argparse.REMAINDER)
    run_parser.set_defaults(handler=run)
