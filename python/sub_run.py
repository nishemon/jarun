import argparse
import imp
import os

import Java

FLAVOR=os.path.abspath(__file__)

def load_flavor(name, flavorpaths):
	try:
		f,n,d = imp.find_module(name, flavorpaths)
		return imp.load_module(name, f, n, d)
	except ImportError:
		return None

def apply_flavor(conf, f):
	tree = []
	depth = 0
	fmod = None
	while depth < 100:
		c = conf.get_flavor_conf(f, { 'base': f })
		if c['base'] == f:
			fmod = load_flavor(f, [FLAVOR, conf.flavordir])
			break
		tree.append(c)
	if not fmod:
		pass
	wf = []
	fc = {}
	for c in tree:
		fc.update(c)
	for w  in fc.get('with', []):
		apply_flavor(conf, w)
	fmod.apply()

def run(conf, args):
	fs = [x.strip() for x in conf.flavor.split(',')]
	jvmflags = []
	for f in fs:
		apply_flavor()
	options = {}
	xxflags = {}
	xxoptions = {}
	for f in jvmflags:
		xxs = f.pop('XX', None)
		for xx in xxs.split() if xxs else []:
			if xx.startswith('+'):
				xxflags[xx[1:]] = True
			elif xx.startswith('-'):
				xxflags[xx[1:]] = False
			else:
				kv = xx.split('=', 2)
				xxoptions[kv[0]] = kv[1]
		for o in f:
			kv = o.lstrip('-').split('=', 2)
			if len(kv) == 1:
				options[kv[0]] = True
			else:
				options[kv[0]] = kv[1]
	applys = [('-' + o) if v == True else '-%s=%s' % (o, v) for o, v in options.iteritems()]
	applys.extends(['-XX:%s=%s' % (xx, v) for xx, v in xxoptions.iteritems()])
	applys.extends(['-XX:%s%s' % ('+' if f else '-', xx) for xx, f in xxflags.iteritems()])
	applys.extends(args.javaarg)
	java = Java.Java(conf)
        java.run(applys)

def setup_subcmd(subparsers):
	run_parser = subparsers.add_parser('run')
	run_parser.add_argument('javaarg', nargs=argparse.REMAINDER)
	run_parser.set_defaults(handler=run)

