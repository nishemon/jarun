#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
 ** marun flavor: compoom **
usage: compoom=/path/to/append.script
add parameter -XX:OnOutOfMemoryError=
"""

import util

def apply(conf, currentflags, flavor_conf):
    compressor = flavor_conf.get('compressor', 'gzip,lz4 --rm')
    for cmd in compressor.split(','):
        cmds = cmd.split()
        if util.find_cmds(cmds[0]):
            return { 'XX': "OnOutOfMemoryError=%s" % ' '.join(cmds) }
    return {}
