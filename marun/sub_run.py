# -*- coding: utf-8 -*-

import argparse
import imp
import os
import sys

from logging import getLogger

import Java
import App

logger = getLogger(__name__)
FLAVOR = os.path.abspath(__file__)


def _load_flavor(name, flavorpaths):
    floadable = [x for x in flavorpaths if x and os.path.isdir(x)]
    if not floadable:
        return None
    try:
        f, n, d = imp.find_module(name, floadable)
        return imp.load_module(name, f, n, d)
    except ImportError:
        return None


def _apply_flavor(f, conf, jvmflags):
    tree = []
    fmod = None
    fkv = f.split('=', 2)
    value = ''
    if 1 < len(fkv):
        f = fkv[0]
        value = fkv[1]
    while len(tree) < 100:
        c = conf.get_flavor_conf(f)
        if c:
            native = False
            base = c.get('base', f)
            tree.append(c)
        else:
            native = True
            base = None
        if base == f:
            fmod = _load_flavor(f, [FLAVOR, conf.flavordir])
            if native and not fmod:
                logger.error("flavor not found: '%s' in [%s]", f, ",".join([FLAVOR, conf.flavordir]))
                return []
            break
    fc = {}
    for c in tree:
        fc.update(c)
    for k in fc:
        fc[k] = fc[k].replace('[]', value)
    tmpflags = jvmflags[:]
    added = []
    for w in fc.get('with', []):
        fs = _apply_flavor(w, conf, tmpflags)
        added.append(fs)
        tmpflags.append(fs)
    if fmod:
        flags = fmod.apply(conf, tmpflags, fc)
    else:
        flags = fc
    added.append(flags)
    return added


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


def run(conf, args):
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
    run_parser = subparsers.add_parser('run', help="Run an installed artifact")
    run_parser.add_argument('--flavors', help="add(+) or replace(@) flavors. ex) --flavors +fl1,fl2")
    run_parser.add_argument('-J', nargs='+', help="JVM argument.", dest='jvmargs')
    run_parser.add_argument('-D', nargs='+', help="JVM system property (pass-through JVM argument).", dest='declare')
    run_parser.add_argument('-n', action='store_true', help="dryrun: print java command")
    run_parser.add_argument('mainclass')
    run_parser.add_argument('classargs', nargs=argparse.REMAINDER)
    run_parser.set_defaults(handler=run)
